// End-to-end replay of what the Android app does, in order, against the
// rules: sign-in, the faculty roster, student registration, joining a course,
// and reporting a rejected attempt. The transactions live in flows.js and
// build every document from contracts/documents.json.

const { collection, doc, getDoc, getDocs, query, runTransaction, serverTimestamp, where } =
  require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, STUDENT, UNLISTED, STRANGER, assertFails, assertSucceeds } = h;
const t = h.setup();

// A student faculty add during the test, then who registers and joins.
const NEW = {
  uid: 'studentN',
  email: 'apawar_mc26@mc.vjti.ac.in',
  name: 'Aditi Pawar',
  rollNo: '262011005',
  cohort: 'MC26',
};
const COURSE_ID = 'courseFlow';
const CODE = 'HJK482';

/** FirestoreCourseRepository.createCourse. Resolves to false when the join code is taken. */
function createCourse(db, teacher, courseId, joinCode) {
  const courseRef = doc(db, 'courses', courseId);
  const joinCodeRef = doc(db, 'joinCodes', joinCode);
  return runTransaction(db, async (tx) => {
    if ((await tx.get(joinCodeRef)).exists()) {
      return false;
    }
    tx.set(
      courseRef,
      flows.build('courses', {
        teacherId: teacher.uid,
        name: 'Compiler Design',
        code: 'CS402',
        threshold: 75,
        joinCode,
        createdAt: serverTimestamp(),
      }),
    );
    tx.set(
      joinCodeRef,
      flows.build('joinCodes', {
        courseId,
        courseName: 'Compiler Design',
        teacherName: teacher.name,
        teacherId: teacher.uid,
      }),
    );
    return true;
  });
}

describe('app flow: sign-in', () => {
  test('faculty: the FACULTY proposal is accepted, first time and every time after', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.writeFacultyProfile(db, TEACHER));
    await assertSucceeds(flows.writeFacultyProfile(db, TEACHER));
    expect((await getDoc(doc(db, 'users', TEACHER.uid))).data().role).toBe('FACULTY');
  });

  test('a student: the FACULTY proposal is denied, which sends them to registration', async () => {
    await assertFails(flows.writeFacultyProfile(t.as(NEW), NEW));
  });

  test('an account outside the institute: denied, and its alert is recorded', async () => {
    const db = t.as(STRANGER);
    await assertFails(flows.writeFacultyProfile(db, STRANGER));
    await assertSucceeds(flows.reportAlert(db, STRANGER, 'NON_VJTI_EMAIL', 'ALL'));
  });
});

describe('app flow: roster, registration, course, join', () => {
  test('the whole path for a new student', async () => {
    const faculty = t.as(TEACHER);
    const student = t.as(NEW);

    // Before faculty add them, the student is told they are not on the roster.
    expect(await flows.register(student, NEW, NEW.rollNo)).toBe('NOT_ON_REGISTER');
    await assertSucceeds(
      flows.reportAlert(student, NEW, 'NOT_ON_REGISTER', 'ALL', { enteredRollNo: NEW.rollNo }),
    );

    // Faculty add the student; adding again changes nothing.
    expect(await assertSucceeds(flows.addStudent(faculty, TEACHER, NEW))).toBe('ADDED');
    expect(await flows.addStudent(faculty, TEACHER, NEW)).toBe('EMAIL_EXISTS');
    const sameRoll = { ...NEW, email: 'bkale_mc26@mc.vjti.ac.in' };
    expect(await flows.addStudent(faculty, TEACHER, sameRoll)).toBe('ROLL_TAKEN');

    // A wrong roll number is refused and reported to the teacher who added them.
    expect(await flows.register(student, NEW, '262019999')).toBe('ROLL_MISMATCH');
    const entry = (await getDoc(doc(student, 'studentRegistry', NEW.email))).data();
    await assertSucceeds(
      flows.reportAlert(student, NEW, 'ROLL_MISMATCH', entry.addedBy, {
        enteredRollNo: '262019999',
      }),
    );

    // The right one registers them.
    expect(await assertSucceeds(flows.register(student, NEW, NEW.rollNo))).toBe('REGISTERED');

    // The teacher creates a course; the student resolves the code and joins.
    expect(await assertSucceeds(createCourse(faculty, TEACHER, COURSE_ID, CODE))).toBe(true);
    await assertFails(getDoc(doc(student, 'courses', COURSE_ID)));
    const code = await assertSucceeds(getDoc(doc(student, 'joinCodes', CODE)));
    const info = { code: code.id, ...code.data() };
    expect(await assertSucceeds(flows.joinCourse(student, NEW, info))).toBe(true);
    expect(await assertSucceeds(flows.joinCourse(student, NEW, info))).toBe(false);

    // The student's home screen: memberships first, then each course.
    const mine = query(collection(student, 'memberships'), where('studentId', '==', NEW.uid));
    const memberships = (await assertSucceeds(getDocs(mine))).docs.map((d) => d.data());
    expect(memberships).toHaveLength(1);
    expect(memberships[0]).toMatchObject({ courseId: COURSE_ID, rollNo: NEW.rollNo, cohort: 'MC26' });
    const course = await assertSucceeds(getDoc(doc(student, 'courses', COURSE_ID)));
    expect(course.data().name).toBe('Compiler Design');

    // The teacher's alerts list shows both rejected attempts.
    const inbox = query(
      collection(faculty, 'identityAlerts'),
      where('audience', 'in', [TEACHER.uid, 'ALL']),
    );
    const types = (await assertSucceeds(getDocs(inbox))).docs.map((d) => d.data().type).sort();
    expect(types).toEqual(['NOT_ON_REGISTER', 'ROLL_MISMATCH']);
  });

  test('a student who has not registered cannot join', async () => {
    await createCourse(t.as(TEACHER), TEACHER, COURSE_ID, CODE);
    const who = { ...UNLISTED, rollNo: '262010009', cohort: 'MC26' };
    const info = { code: CODE, courseId: COURSE_ID, teacherId: TEACHER.uid };
    await assertFails(flows.joinCourse(t.as(who), who, info));
  });

  test('the teacher creates a course and sees it; a taken join code is detected', async () => {
    const db = t.as(TEACHER);
    expect(await assertSucceeds(createCourse(db, TEACHER, COURSE_ID, CODE))).toBe(true);
    expect(await createCourse(db, TEACHER, 'courseOther', h.JOIN_CODE)).toBe(false);
    const mine = query(collection(db, 'courses'), where('teacherId', '==', TEACHER.uid));
    const ids = (await assertSucceeds(getDocs(mine))).docs.map((d) => d.id);
    expect(ids).toContain(COURSE_ID);
  });

  test('denied: a student runs the course-creation transaction', async () => {
    await assertFails(createCourse(t.as(STUDENT), STUDENT, COURSE_ID, CODE));
  });
});

describe('app flow: a session from start to end', () => {
  const SESSION_ID = 'sessFlow';
  const TOKEN_1 = 'flowToken_000000-00001';
  const TOKEN_2 = 'flowToken_000000-00002';
  const HERE = { lat: 19.076, lng: 72.8777, radiusM: 120, cosLat: Math.cos((19.076 * Math.PI) / 180) };

  test('start, rotate tokens, a student marks, the teacher sees it, end', async () => {
    const faculty = t.as(TEACHER);
    await assertSucceeds(flows.startSession(faculty, TEACHER, SESSION_ID, h.COURSE, 60, HERE));

    // The teacher's home and course screens.
    const active = query(
      collection(faculty, 'sessions'),
      where('teacherId', '==', TEACHER.uid),
      where('status', '==', 'ACTIVE'),
    );
    const ids = (await assertSucceeds(getDocs(active))).docs.map((d) => d.id);
    expect(ids).toContain(SESSION_ID);

    await assertSucceeds(flows.writeToken(faculty, SESSION_ID, TOKEN_1));
    await assertSucceeds(flows.writeToken(faculty, SESSION_ID, TOKEN_2));

    // An enrolled student inside the area scans the current code. The app first reads its own
    // record (not found) and the session (allowed for a member), then marks.
    const student = t.as(STUDENT);
    const own = doc(student, 'attendance', `${SESSION_ID}_${STUDENT.uid}`);
    expect((await assertSucceeds(getDoc(own))).exists()).toBe(false);
    const snapshot = await assertSucceeds(getDoc(doc(student, 'sessions', SESSION_ID)));
    const session = { id: snapshot.id, ...snapshot.data() };
    expect(await assertSucceeds(flows.markLive(student, STUDENT, session, TOKEN_2, h.INSIDE))).toBe('MARKED');
    expect(await assertSucceeds(flows.markLive(student, STUDENT, session, TOKEN_2, h.INSIDE))).toBe('ALREADY_MARKED');

    // A student who is not enrolled cannot even read the session, which is how the app
    // knows to say "not enrolled".
    await assertFails(getDoc(doc(t.as(h.OUTSIDER), 'sessions', SESSION_ID)));

    // An old code: the first token is still inside 20 s here, so age it, then try it.
    await t.seed({ [`sessions/${SESSION_ID}/tokens/${TOKEN_1}`]: { validFrom: h.ago(21 * h.SECOND) } });
    const other = t.as(h.OTHER_STUDENT);
    await assertFails(flows.markLive(other, h.OTHER_STUDENT, session, TOKEN_1, h.INSIDE));
    // Outside the area with the current code.
    const outside = { ...h.INSIDE, lat: h.INSIDE.lat + 0.01 };
    await assertFails(flows.markLive(other, h.OTHER_STUDENT, session, TOKEN_2, outside));

    // The live roster query.
    const roster = query(
      collection(faculty, 'attendance'),
      where('sessionId', '==', SESSION_ID),
      where('teacherId', '==', TEACHER.uid),
    );
    expect((await assertSucceeds(getDocs(roster))).size).toBe(1);

    // Ending early stamps the server time; afterwards no token can be written.
    expect(await assertSucceeds(flows.endSession(faculty, SESSION_ID))).toBe('ENDED_EARLY');
    await assertFails(flows.writeToken(faculty, SESSION_ID, 'flowToken_000000-00003'));
    expect(await flows.endSession(faculty, SESSION_ID)).toBe('ALREADY_ENDED');
  });

  test('closing a session whose time has run out keeps its original end', async () => {
    const endedAt = h.ago(h.MINUTE);
    await t.seed({
      [`sessions/${SESSION_ID}`]: h.sessionDoc({ startedAt: h.ago(61 * h.MINUTE), endsAt: endedAt }),
    });
    const faculty = t.as(TEACHER);
    expect(await assertSucceeds(flows.endSession(faculty, SESSION_ID))).toBe('CLOSED');
    const stored = (await getDoc(doc(faculty, 'sessions', SESSION_ID))).data();
    expect(stored.status).toBe('ENDED');
    expect(stored.endsAt.toMillis()).toBe(endedAt.toMillis());
  });

  test('the device clock is wrong about a session being over: the second form is used', async () => {
    // The device thinks the session still has time; the server knows it ended a moment ago.
    const realNow = Date.now;
    await t.seed({ [`sessions/${SESSION_ID}`]: h.sessionDoc({ endsAt: h.ago(2 * h.SECOND) }) });
    const faculty = t.as(TEACHER);
    Date.now = () => realNow() - 5 * h.MINUTE;
    try {
      expect(await assertSucceeds(flows.endSession(faculty, SESSION_ID))).toBe('CLOSED');
    } finally {
      Date.now = realNow;
    }
  });

  test('denied: a student runs the start, token or end transactions', async () => {
    const student = t.as(STUDENT);
    await assertFails(flows.startSession(student, STUDENT, SESSION_ID, h.COURSE, 60, HERE));
    await assertFails(flows.writeToken(student, h.SESSION, TOKEN_1));
    await assertFails(flows.endSession(student, h.SESSION));
  });
});

describe('app flow: a scan saved offline is sent later', () => {
  const TOKEN_AT = 40 * h.MINUTE;
  const session = (endedAgo) => ({
    id: h.SESSION,
    ...h.sessionDoc({ startedAt: h.ago(60 * h.MINUTE), endsAt: h.ago(endedAgo) }),
  });
  // Scanned five seconds after the code was issued, forty minutes ago.
  const saved = (student, offsetMs = 5 * h.SECOND) => ({
    studentId: student.uid,
    token: h.TOKEN,
    scannedAtMillis: Date.now() - TOKEN_AT + offsetMs,
    ...h.INSIDE,
  });
  const seedEnded = (endedAgo) =>
    t.seed({
      [`sessions/${h.SESSION}`]: h.sessionDoc({ startedAt: h.ago(60 * h.MINUTE), endsAt: h.ago(endedAgo) }),
      [`sessions/${h.SESSION}/tokens/${h.TOKEN}`]: { validFrom: h.ago(TOKEN_AT) },
    });

  test('inside the grace period it is recorded once, as a late sync', async () => {
    await seedEnded(29 * h.MINUTE);
    const student = t.as(STUDENT);
    expect(await assertSucceeds(flows.markQueued(student, session(29 * h.MINUTE), saved(STUDENT)))).toBe('MARKED');
    const stored = (await getDoc(doc(student, 'attendance', `${h.SESSION}_${STUDENT.uid}`))).data();
    expect(stored.status).toBe('LATE_SYNC');
    expect(stored.source).toBe('QUEUED');
    // The worker runs again after a lost answer: nothing is written twice.
    expect(await assertSucceeds(flows.markQueued(student, session(29 * h.MINUTE), saved(STUDENT)))).toBe('ALREADY_MARKED');
  });

  test('after the grace period it is refused', async () => {
    await seedEnded(31 * h.MINUTE);
    await assertFails(flows.markQueued(t.as(STUDENT), session(31 * h.MINUTE), saved(STUDENT)));
  });

  test('a scan time at which the code was not current is refused', async () => {
    await seedEnded(29 * h.MINUTE);
    const student = t.as(STUDENT);
    await assertFails(flows.markQueued(student, session(29 * h.MINUTE), saved(STUDENT, 21 * h.SECOND)));
    await assertFails(flows.markQueued(student, session(29 * h.MINUTE), saved(STUDENT, -1 * h.SECOND)));
  });

  test('a saved scan cannot be sent for another student or from outside the area', async () => {
    await seedEnded(29 * h.MINUTE);
    const student = t.as(STUDENT);
    await assertFails(flows.markQueued(student, session(29 * h.MINUTE), saved(h.OTHER_STUDENT)));
    const outside = { ...saved(STUDENT), lat: h.INSIDE.lat + 0.01 };
    await assertFails(flows.markQueued(student, session(29 * h.MINUTE), outside));
  });
});

describe('app flow: a teacher corrects attendance by hand', () => {
  const record = (db, student) => getDoc(doc(db, 'attendance', `${h.SESSION}_${student.uid}`));
  const correct = (db, who, student, hasRecord, present, note = 'Seen in class') =>
    flows.correctAttendance(
      db,
      who,
      h.COURSE,
      h.SESSION,
      student.uid,
      hasRecord,
      present ? 'MANUAL_PRESENT' : 'MANUAL_ABSENT',
      note,
    );

  test('a teacher cannot read a record that does not exist, so the app never tries', async () => {
    await assertFails(record(t.as(TEACHER), STUDENT));
  });

  test('absent with no record writes nothing; present creates a manual record', async () => {
    const faculty = t.as(TEACHER);
    expect(await correct(faculty, TEACHER, STUDENT, false, false)).toBe(false);
    expect(await assertSucceeds(correct(faculty, TEACHER, STUDENT, false, true))).toBe(true);
    const stored = (await assertSucceeds(record(faculty, STUDENT))).data();
    expect(stored.status).toBe('MANUAL_PRESENT');
    expect(stored.source).toBe('MANUAL');
    expect(stored.updatedBy).toBe(TEACHER.uid);
  });

  test('a scanned mark can be turned absent and back, keeping where it came from', async () => {
    await t.seed({ [`attendance/${h.SESSION}_${STUDENT.uid}`]: h.storedMark(STUDENT) });
    const faculty = t.as(TEACHER);
    expect(await assertSucceeds(correct(faculty, TEACHER, STUDENT, true, false, 'Left early'))).toBe(true);
    let stored = (await record(faculty, STUDENT)).data();
    expect(stored.status).toBe('MANUAL_ABSENT');
    expect(stored.note).toBe('Left early');
    expect(stored.source).toBe('LIVE');
    expect(stored.token).toBe(h.TOKEN);
    expect(await assertSucceeds(correct(faculty, TEACHER, STUDENT, true, true, 'Returned'))).toBe(true);
    stored = (await record(faculty, STUDENT)).data();
    expect(stored.status).toBe('MANUAL_PRESENT');
  });

  test('a stale roster cannot damage a record: the wrong kind of write is refused', async () => {
    const faculty = t.as(TEACHER);
    // The roster says "no record" but the student has just marked: a create must not overwrite.
    await t.seed({ [`attendance/${h.SESSION}_${STUDENT.uid}`]: h.storedMark(STUDENT) });
    await assertFails(correct(faculty, TEACHER, STUDENT, false, true));
    expect((await record(faculty, STUDENT)).data().status).toBe('PRESENT');
    // The roster says "has a record" but there is none: an update has nothing to change.
    await assertFails(correct(faculty, TEACHER, h.OTHER_STUDENT, true, false));
  });

  test('refused: no note, another teacher, a student, someone not enrolled', async () => {
    const faculty = t.as(TEACHER);
    await assertFails(correct(faculty, TEACHER, STUDENT, false, true, ''));
    await assertFails(correct(t.as(h.OTHER_TEACHER), h.OTHER_TEACHER, STUDENT, false, true));
    await assertFails(correct(t.as(STUDENT), STUDENT, STUDENT, false, true));
    await assertFails(correct(faculty, TEACHER, h.OUTSIDER, false, true));
  });
});

describe('app flow: attendance reports', () => {
  const records = (db, field, uid) =>
    query(collection(db, 'attendance'), where(field, '==', uid), where('courseId', '==', h.COURSE));
  const courseSessions = (db) =>
    query(collection(db, 'sessions'), where('courseId', '==', h.COURSE));

  beforeEach(() =>
    t.seed({
      [`attendance/${h.SESSION}_${STUDENT.uid}`]: h.storedMark(STUDENT),
      [`attendance/${h.SESSION}_${h.OTHER_STUDENT.uid}`]: h.storedMark(h.OTHER_STUDENT),
    }),
  );

  test('the teacher loads every record of the course; a student only their own', async () => {
    const all = await assertSucceeds(getDocs(records(t.as(TEACHER), 'teacherId', TEACHER.uid)));
    expect(all.size).toBe(2);
    const mine = await assertSucceeds(getDocs(records(t.as(STUDENT), 'studentId', STUDENT.uid)));
    expect(mine.docs.map((d) => d.data().studentId)).toEqual([STUDENT.uid]);
    expect((await assertSucceeds(getDocs(courseSessions(t.as(STUDENT))))).size).toBe(1);
  });

  test('the teacher loads one student\'s records; another teacher gets nothing', async () => {
    const one = (db, teacher) =>
      query(
        collection(db, 'attendance'),
        where('teacherId', '==', teacher.uid),
        where('studentId', '==', STUDENT.uid),
        where('courseId', '==', h.COURSE),
      );
    const mine = await assertSucceeds(getDocs(one(t.as(TEACHER), TEACHER)));
    expect(mine.docs.map((d) => d.data().studentId)).toEqual([STUDENT.uid]);
    expect((await assertSucceeds(getDocs(one(t.as(h.OTHER_TEACHER), h.OTHER_TEACHER)))).size).toBe(0);
    // Without their own id in the query, a teacher is refused.
    const loose = query(
      collection(t.as(h.OTHER_TEACHER), 'attendance'),
      where('studentId', '==', STUDENT.uid),
      where('courseId', '==', h.COURSE),
    );
    await assertFails(getDocs(loose));
  });

  test('denied: a classmate, another teacher or a non-member asking for the same data', async () => {
    await assertFails(getDocs(records(t.as(STUDENT), 'studentId', h.OTHER_STUDENT.uid)));
    await assertFails(getDocs(records(t.as(STUDENT), 'teacherId', TEACHER.uid)));
    await assertFails(getDocs(records(t.as(h.OTHER_TEACHER), 'teacherId', TEACHER.uid)));
    await assertFails(getDocs(courseSessions(t.as(h.OUTSIDER))));
  });
});
