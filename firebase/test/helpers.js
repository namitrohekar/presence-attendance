// Shared fixtures for the rules suite. Every test starts from the same small
// world, seeded with rules disabled, and then makes one request as a client.

const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, setDoc, Timestamp, serverTimestamp, setLogLevel } = require('firebase/firestore');

// Denials are the point of most tests; the SDK's warning for each one is noise.
setLogLevel('silent');

// A "demo-" project id can never reach a real Firebase project.
const PROJECT_ID = 'demo-presence';

const RULES_PATH = path.join(__dirname, '..', 'firestore.rules');
const CONTRACTS_DIR = path.join(__dirname, '..', '..', 'contracts');

const TEACHER = { uid: 'teacherA', email: 'asha.kulkarni@example.edu', name: 'Asha Kulkarni' };
const OTHER_TEACHER = { uid: 'teacherB', email: 'vikram.rao@example.edu', name: 'Vikram Rao' };
const STUDENT = { uid: 'studentA', email: 'meera.joshi@example.edu', name: 'Meera Joshi' };
const OTHER_STUDENT = { uid: 'studentB', email: 'rohan.patil@example.edu', name: 'Rohan Patil' };
const OUTSIDER = { uid: 'studentC', email: 'isha.nair@example.edu', name: 'Isha Nair' };

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
  return env
    .authenticatedContext(user.uid, {
      email: user.email,
      email_verified: true,
      name: user.name,
      ...claims,
    })
    .firestore();
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

function membershipDoc(student, overrides = {}) {
  return {
    courseId: COURSE,
    teacherId: TEACHER.uid,
    studentId: student.uid,
    name: student.name,
    email: student.email,
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
 * The standard world: an allowlisted teacher with one course, two enrolled
 * students, one live session and one token issued two seconds ago.
 */
function world(overrides = {}) {
  return {
    [`teacherAllowlist/${TEACHER.email}`]: {},
    [`teacherAllowlist/${OTHER_TEACHER.email}`]: {},
    'appConfig/campus': GEOFENCE,
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
    ...overrides,
  };
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
  STUDENT,
  OTHER_STUDENT,
  OUTSIDER,
  COURSE,
  JOIN_CODE,
  SESSION,
  TOKEN,
  GEOFENCE,
  INSIDE,
  DROP,
  SECOND,
  MINUTE,
  ago,
  fromNow,
  setup,
  world,
  courseDoc,
  joinCodeDoc,
  membershipDoc,
  sessionDoc,
  liveMark,
  queuedMark,
  manualMark,
  storedMark,
  patch,
  attendanceId,
  assertFails,
  assertSucceeds,
};
