// Shared fixtures for the rules suite. Every test starts from the same small
// world, seeded with rules disabled, and then makes one request as a client.

const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc, writeBatch, Timestamp, serverTimestamp, setLogLevel } =
  require('firebase/firestore');

// Denials are the point of most tests; the SDK's warning for each one is noise.
setLogLevel('silent');

// A "demo-" project id can never reach a real Firebase project.
const PROJECT_ID = 'demo-presence';

const RULES_PATH = path.join(__dirname, '..', 'firestore.rules');
const CONTRACTS_DIR = path.join(__dirname, '..', '..', 'contracts');

// The HOD, who also teaches, and a teacher on the HOD's faculty list.
const TEACHER = { uid: 'teacherA', email: 'asha.kulkarni@example.edu', name: 'Asha Kulkarni' };
const OTHER_TEACHER = { uid: 'teacherB', email: 'vikram.rao@example.edu', name: 'Vikram Rao' };
// A second HOD (a department may name more than one), not on the faculty list.
const DEPUTY = { uid: 'teacherC', email: 'neha.shah@example.edu', name: 'Neha Shah' };

// Fictional students. Roll numbers are opaque nine-digit identifiers.
const student = (uid, local, name, rollNo) => ({
  uid,
  email: `${local}@mc.vjti.ac.in`,
  name,
  rollNo,
  cohort: 'MC26',
});
// Registered and enrolled in the course.
const STUDENT = student('studentA', 'mjoshi_mc26', 'Meera Joshi', '262011001');
const OTHER_STUDENT = student('studentB', 'rpatil_mc26', 'Rohan Patil', '262010002');
// Registered, but not enrolled in the course.
const OUTSIDER = student('studentC', 'inair_mc26', 'Isha Nair', '262011003');
// On the roster, but has not registered yet.
const LISTED = student('studentD', 'kdesai_mc26', 'Kunal Desai', '262010004');
// A VJTI address that faculty have not added to the roster.
const UNLISTED = { uid: 'studentE', email: 'sshah_mc26@mc.vjti.ac.in', name: 'Sana Shah' };
// Neither a VJTI address nor on the faculty allowlist.
const STRANGER = { uid: 'strangerA', email: 'some.one@gmail.com', name: 'Some One' };

const COURSE = 'courseA';
const JOIN_CODE = 'ABC234';
const SESSION = 'sessA';
const TOKEN = 'tokenFresh_0000000-001';
const GEOFENCE = { lat: 19.076, lng: 72.8777, radiusM: 100, cosLat: 0.9450858939663362 };
// 20 m north of the geofence centre.
const INSIDE = { lat: 19.07617966223500, lng: 72.8777, accuracyM: 10 };

/** Marks a field for removal in patch(). */
const DROP = Symbol('drop');

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const DAY = 24 * 60 * MINUTE;

const ago = (ms) => Timestamp.fromMillis(Date.now() - ms);
const fromNow = (ms) => Timestamp.fromMillis(Date.now() + ms);

async function newEnv(projectId, rules) {
  return initializeTestEnvironment({
    projectId,
    firestore: { rules: rules ?? fs.readFileSync(RULES_PATH, 'utf8') },
  });
}

/** Firestore handle for a signed-in user with a verified email. */
function as(env, user, claims = {}) {
  const db = env
    .authenticatedContext(user.uid, {
      email: user.email,
      email_verified: true,
      name: user.name,
      ...claims,
    })
    .firestore();
  // Remembered so submit() can claim the phone as whoever is writing. Set on the
  // instance document references point at, which is not the wrapper returned here.
  doc(db, 'users', user.uid).firestore.presenceUid = user.uid;
  return db;
}

/** The id of the one phone a fixture student owns: 22 characters, like a token. */
const deviceOf = (student) => `${student.uid}Phone0000000000000000`.slice(0, 22);

/**
 * Writes a mark the way the app does: together with the claim on the phone it
 * names, made as whoever is signed in. A document with no usable deviceId (a
 * teacher's manual record, or a deliberately broken mark) is written alone.
 */
function submit(ref, data) {
  const db = ref.firestore;
  const usable = (v) => typeof v === 'string' && v !== '' && !v.includes('/');
  if (!usable(data.deviceId) || !usable(data.sessionId)) {
    return setDoc(ref, data);
  }
  const batch = writeBatch(db);
  batch.set(ref, data);
  batch.set(doc(db, 'sessions', data.sessionId, 'devices', data.deviceId), {
    studentId: db.presenceUid,
  });
  return batch.commit();
}

/** Runs writes with rules disabled, to put the database in a known state. */
async function seed(env, writes) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await Promise.all(
      Object.entries(writes).map(([docPath, data]) => setDoc(doc(db, docPath), data)),
    );
  });
}

function courseDoc(overrides = {}) {
  return {
    teacherId: TEACHER.uid,
    name: 'Data Structures',
    code: 'CS201',
    threshold: 75,
    joinCode: JOIN_CODE,
    createdAt: ago(30 * 24 * 60 * MINUTE),
    ...overrides,
  };
}

function joinCodeDoc(overrides = {}) {
  return {
    courseId: COURSE,
    courseName: 'Data Structures',
    teacherName: TEACHER.name,
    teacherId: TEACHER.uid,
    ...overrides,
  };
}

/** A batch as the HOD creates it: the fixtures' cohort MC26 is programme MC from 2026. */
function batchDoc(overrides = {}) {
  return {
    programme: 'MC',
    startYear: 2026,
    years: 2,
    semester: 1,
    intake: 70,
    labGroups: 4,
    createdBy: TEACHER.uid,
    createdAt: ago(60 * DAY),
    ...overrides,
  };
}

/** Someone on the HOD's faculty list. */
function facultyDoc(who, overrides = {}) {
  return {
    email: who.email,
    name: who.name,
    initials: who.name
      .split(' ')
      .map((word) => word[0])
      .join(''),
    addedBy: TEACHER.uid,
    addedAt: ago(60 * DAY),
    ...overrides,
  };
}

/** A roster entry as the HOD adds it: not yet claimed by the student. */
function registryDoc(who, overrides = {}) {
  return {
    email: who.email,
    rollNo: who.rollNo,
    cohort: who.cohort,
    name: who.name,
    addedBy: TEACHER.uid,
    addedAt: ago(40 * DAY),
    revision: 0,
    ...overrides,
  };
}

/** A roster entry after the student has registered. */
function claimedRegistryDoc(who, overrides = {}) {
  return registryDoc(who, { registeredUid: who.uid, registeredAt: ago(30 * DAY), ...overrides });
}

const registryPath = (who) => `studentRegistry/${who.email}`;
const rollIndexPath = (who) => `rollIndex/${who.cohort}_${who.rollNo}`;

/** The two documents the HOD writes for one student, with the entry claimed or not. */
function rosterDocs(who, claimed) {
  return {
    [registryPath(who)]: claimed ? claimedRegistryDoc(who) : registryDoc(who),
    [rollIndexPath(who)]: { email: who.email },
  };
}

/** A registered student's profile. */
function studentProfileDoc(who, overrides = {}) {
  return {
    name: who.name,
    email: who.email,
    role: 'STUDENT',
    createdAt: ago(30 * DAY),
    rollNo: who.rollNo,
    cohort: who.cohort,
    ...overrides,
  };
}

function membershipDoc(student, overrides = {}) {
  return {
    courseId: COURSE,
    teacherId: TEACHER.uid,
    studentId: student.uid,
    name: student.name,
    email: student.email,
    rollNo: student.rollNo,
    cohort: student.cohort,
    joinCode: JOIN_CODE,
    joinedAt: ago(20 * 24 * 60 * MINUTE),
    ...overrides,
  };
}

function sessionDoc(overrides = {}) {
  return {
    courseId: COURSE,
    teacherId: TEACHER.uid,
    status: 'ACTIVE',
    startedAt: ago(10 * MINUTE),
    endsAt: fromNow(50 * MINUTE),
    geofence: GEOFENCE,
    ...overrides,
  };
}

/**
 * The standard world: the HOD, who teaches one course, and a teacher on the
 * faculty list; batches MC26 and MC25; a roster of four MC26 students (three
 * registered, one not yet), two of them enrolled, one live session and one
 * token issued two seconds ago.
 */
function world(overrides = {}) {
  return {
    [`hods/${TEACHER.email}`]: {},
    [`hods/${DEPUTY.email}`]: {},
    [`faculty/${TEACHER.email}`]: facultyDoc(TEACHER),
    [`faculty/${OTHER_TEACHER.email}`]: facultyDoc(OTHER_TEACHER),
    'batches/MC26': batchDoc(),
    'batches/MC25': batchDoc({ startYear: 2025, semester: 3 }),
    'appConfig/campus': GEOFENCE,
    ...rosterDocs(STUDENT, true),
    ...rosterDocs(OTHER_STUDENT, true),
    ...rosterDocs(OUTSIDER, true),
    ...rosterDocs(LISTED, false),
    // Registered students' profiles, each bound to the one phone they mark from.
    [`users/${STUDENT.uid}`]: studentProfileDoc(STUDENT, { deviceId: deviceOf(STUDENT) }),
    [`users/${OTHER_STUDENT.uid}`]: studentProfileDoc(OTHER_STUDENT, {
      deviceId: deviceOf(OTHER_STUDENT),
    }),
    [`users/${OUTSIDER.uid}`]: studentProfileDoc(OUTSIDER, { deviceId: deviceOf(OUTSIDER) }),
    [`courses/${COURSE}`]: courseDoc(),
    [`joinCodes/${JOIN_CODE}`]: joinCodeDoc(),
    [`memberships/${COURSE}_${STUDENT.uid}`]: membershipDoc(STUDENT),
    [`memberships/${COURSE}_${OTHER_STUDENT.uid}`]: membershipDoc(OTHER_STUDENT),
    [`sessions/${SESSION}`]: sessionDoc(),
    [`sessions/${SESSION}/tokens/${TOKEN}`]: { validFrom: ago(2 * SECOND) },
    ...overrides,
  };
}

/** A LIVE mark that passes every rule in the standard world. */
function liveMark(student = STUDENT, overrides = {}) {
  return {
    sessionId: SESSION,
    courseId: COURSE,
    teacherId: TEACHER.uid,
    studentId: student.uid,
    status: 'PRESENT',
    source: 'LIVE',
    token: TOKEN,
    markedAt: serverTimestamp(),
    ...INSIDE,
    deviceId: deviceOf(student),
    ...overrides,
  };
}

/** A QUEUED mark; scannedAt must be supplied relative to the seeded token. */
function queuedMark(scannedAt, student = STUDENT, overrides = {}) {
  return liveMark(student, { status: 'LATE_SYNC', source: 'QUEUED', scannedAt, ...overrides });
}

/** A teacher's manual present record for a student with no document yet. */
function manualMark(student = STUDENT, overrides = {}) {
  return {
    sessionId: SESSION,
    courseId: COURSE,
    teacherId: TEACHER.uid,
    studentId: student.uid,
    status: 'MANUAL_PRESENT',
    source: 'MANUAL',
    markedAt: serverTimestamp(),
    note: 'Phone battery died, seen in class',
    updatedBy: TEACHER.uid,
    updatedAt: serverTimestamp(),
    revision: 1,
    ...overrides,
  };
}

/**
 * The history entry a teacher's change must be written with, describing `record` as the new
 * state of the student's record. `from` is the status before, or 'NONE' for a create.
 */
function historyEntry(student, revision, from, record, teacher = TEACHER, overrides = {}) {
  const or = (v) => (v === undefined ? null : v);
  return {
    recordId: attendanceId(student),
    revision,
    teacherId: teacher.uid,
    studentId: student.uid,
    from,
    to: or(record.status),
    note: or(record.note),
    by: teacher.uid,
    at: serverTimestamp(),
    ...overrides,
  };
}

/**
 * A teacher's change written as the app writes it: the record, created or updated, and its
 * history entry in one batch. Pass `history: null` to leave the entry out.
 */
function teacherWrite(db, student, record, options) {
  const { from, revision, create = false, teacher = TEACHER, history = {} } = options;
  const batch = writeBatch(db);
  const ref = doc(db, 'attendance', attendanceId(student));
  if (create) {
    batch.set(ref, record);
  } else {
    batch.update(ref, record);
  }
  if (history !== null) {
    batch.set(
      doc(db, 'attendanceChanges', `${attendanceId(student)}_${revision}`),
      historyEntry(student, revision, from, record, teacher, history),
    );
  }
  return batch.commit();
}

/** An attendance document as it would exist after a successful LIVE mark. */
function storedMark(student = STUDENT, overrides = {}) {
  return liveMark(student, { markedAt: ago(5 * MINUTE), ...overrides });
}

/** Copy of base with the patch applied; DROP removes the key. */
function patch(base, changes) {
  const out = { ...base, ...changes };
  for (const key of Object.keys(out)) {
    if (out[key] === DROP) {
      delete out[key];
    }
  }
  return out;
}

const attendanceId = (student = STUDENT, session = SESSION) => `${session}_${student.uid}`;

/**
 * Registers the Jest hooks for a rules test file and returns handles for it.
 * The standard world is rebuilt before every test; extra() adds documents.
 */
function setup(extra = () => ({}), rules) {
  const t = { env: null };
  t.reset = async () => {
    await t.env.clearFirestore();
    await seed(t.env, world(extra()));
  };
  t.as = (user, claims) => as(t.env, user, claims);
  t.anon = () => t.env.unauthenticatedContext().firestore();
  t.seed = (writes) => seed(t.env, writes);
  beforeAll(async () => {
    t.env = await newEnv(PROJECT_ID, rules);
  });
  afterAll(async () => {
    await t.env.cleanup();
  });
  beforeEach(async () => {
    await t.reset();
  });
  return t;
}

module.exports = {
  CONTRACTS_DIR,
  RULES_PATH,
  TEACHER,
  OTHER_TEACHER,
  DEPUTY,
  STUDENT,
  OTHER_STUDENT,
  OUTSIDER,
  LISTED,
  UNLISTED,
  STRANGER,
  COURSE,
  JOIN_CODE,
  SESSION,
  TOKEN,
  GEOFENCE,
  INSIDE,
  DROP,
  SECOND,
  MINUTE,
  DAY,
  ago,
  fromNow,
  setup,
  world,
  courseDoc,
  joinCodeDoc,
  batchDoc,
  facultyDoc,
  membershipDoc,
  registryDoc,
  claimedRegistryDoc,
  registryPath,
  rollIndexPath,
  rosterDocs,
  studentProfileDoc,
  sessionDoc,
  liveMark,
  queuedMark,
  manualMark,
  historyEntry,
  teacherWrite,
  storedMark,
  patch,
  attendanceId,
  deviceOf,
  submit,
  assertFails,
  assertSucceeds,
};
