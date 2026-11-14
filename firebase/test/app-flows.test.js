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
