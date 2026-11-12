// Invariant 3: memberships. A student creates only their own membership, only
// with a join code that resolves to that course, with server-true teacher,
// name, email and time. No updates or deletes.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, OUTSIDER, COURSE, JOIN_CODE, DROP } = h;
const { assertFails, assertSucceeds } = h;

const OTHER_COURSE = 'courseB';
const OTHER_CODE = 'QRS567';

// A second course, owned by the other teacher, to test cross-course mix-ups.
const t = h.setup(() => ({
  [`courses/${OTHER_COURSE}`]: h.courseDoc({ teacherId: OTHER_TEACHER.uid, joinCode: OTHER_CODE }),
  [`joinCodes/${OTHER_CODE}`]: h.joinCodeDoc({
    courseId: OTHER_COURSE,
    teacherName: OTHER_TEACHER.name,
    teacherId: OTHER_TEACHER.uid,
  }),
}));

const join = (student = OUTSIDER, overrides = {}) => ({
  courseId: COURSE,
  teacherId: TEACHER.uid,
  studentId: student.uid,
  name: student.name,
  email: student.email,
  joinCode: JOIN_CODE,
  joinedAt: serverTimestamp(),
  ...overrides,
});
const ref = (db, student = OUTSIDER, course = COURSE) =>
  doc(db, 'memberships', `${course}_${student.uid}`);

describe('INV-3 membership create', () => {
  test('a student joins with a valid code', async () => {
    await assertSucceeds(setDoc(ref(t.as(OUTSIDER)), join()));
  });

  test('a student joins with an optional roll number', async () => {
    await assertSucceeds(setDoc(ref(t.as(OUTSIDER)), join(OUTSIDER, { rollNo: '21CS045' })));
  });

  test.each([
    ['the code belongs to another course', { joinCode: OTHER_CODE }],
    ['the code does not exist', { joinCode: 'ZZZZZZ' }],
    ['the code is malformed', { joinCode: 'abc' }],
    ['the teacher is not the course teacher', { teacherId: OTHER_TEACHER.uid }],
    ['the name differs from the sign-in token', { name: 'Someone Else' }],
    ['the email differs from the sign-in token', { email: 'other@example.edu' }],
    ['joinedAt is a client-chosen time', { joinedAt: h.ago(90 * 24 * 60 * h.MINUTE) }],
    ['studentId is another user', { studentId: OTHER_STUDENT.uid }],
    ['an extra field is present', { role: 'TA' }],
    ['a required field is missing', { joinCode: DROP }],
    ['the roll number is empty', { rollNo: '' }],
    ['the roll number is longer than 20 characters', { rollNo: '9'.repeat(21) }],
    ['the roll number is not a string', { rollNo: 45 }],
  ])('denied when %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(OUTSIDER)), h.patch(join(), change)));
  });

  test('denied: creating a membership document for another student', async () => {
    await assertFails(setDoc(ref(t.as(OUTSIDER), OTHER_STUDENT, OTHER_COURSE), join(OUTSIDER)));
  });

  test('denied: a document id for a different course than the data', async () => {
    await assertFails(setDoc(ref(t.as(OUTSIDER), OUTSIDER, OTHER_COURSE), join()));
  });

  test('denied: a course that does not exist', async () => {
    const data = join(OUTSIDER, { courseId: 'ghost' });
    await assertFails(setDoc(ref(t.as(OUTSIDER), OUTSIDER, 'ghost'), data));
  });

  test('denied: a teacher joining their own course', async () => {
    await assertFails(setDoc(ref(t.as(TEACHER), TEACHER), join(TEACHER)));
  });

  test('denied: joining twice (the second write is an update)', async () => {
    const db = t.as(OUTSIDER);
    await assertSucceeds(setDoc(ref(db), join()));
    await assertFails(setDoc(ref(db), join(OUTSIDER, { rollNo: '21CS046' })));
  });
});

describe('INV-3 membership is immutable', () => {
  test.each([
    ['the student', STUDENT],
    ['the teacher', TEACHER],
  ])('denied: %s updates or deletes a membership', async (_, user) => {
    const db = t.as(user);
    await assertFails(updateDoc(ref(db, STUDENT), { rollNo: '21CS001' }));
    await assertFails(deleteDoc(ref(db, STUDENT)));
  });
});

describe('INV-3 membership reads', () => {
  test('a student reads and lists their own memberships', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(getDoc(ref(db, STUDENT)));
    const mine = query(collection(db, 'memberships'), where('studentId', '==', STUDENT.uid));
    const snap = await assertSucceeds(getDocs(mine));
    expect(snap.size).toBe(1);
  });

  test('a student checking a course they have not joined gets "not found"', async () => {
    const snap = await assertSucceeds(getDoc(ref(t.as(OUTSIDER))));
    expect(snap.exists()).toBe(false);
  });

  test('the teacher reads and lists the memberships of their course', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(getDoc(ref(db, STUDENT)));
    const roster = query(
      collection(db, 'memberships'),
      where('teacherId', '==', TEACHER.uid),
      where('courseId', '==', COURSE),
    );
    const snap = await assertSucceeds(getDocs(roster));
    expect(snap.size).toBe(2);
  });

  test('denied: another student or another teacher reads a membership', async () => {
    await assertFails(getDoc(ref(t.as(OTHER_STUDENT), STUDENT)));
    await assertFails(getDoc(ref(t.as(OTHER_TEACHER), STUDENT)));
  });

  test('denied: a student lists classmates, or anyone lists without constraints', async () => {
    const db = t.as(STUDENT);
    const classmates = query(collection(db, 'memberships'), where('courseId', '==', COURSE));
    await assertFails(getDocs(classmates));
    await assertFails(getDocs(collection(t.as(TEACHER), 'memberships')));
  });
});
