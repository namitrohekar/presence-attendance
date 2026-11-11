// Invariant 2: courses and join codes. Only faculty create, with themselves as
// teacher; only the owner updates name and threshold; join codes are unique,
// created with the course, readable one at a time and never listable.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OUTSIDER, COURSE, JOIN_CODE, DROP } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const NEW_COURSE = 'courseNew';
const NEW_CODE = 'XYZ789';

const newCourse = (overrides = {}) => ({
  teacherId: TEACHER.uid,
  name: 'Operating Systems',
  code: 'CS301',
  threshold: 75,
  joinCode: NEW_CODE,
  createdAt: serverTimestamp(),
  ...overrides,
});
const newJoinCode = (overrides = {}) => ({
  courseId: NEW_COURSE,
  courseName: 'Operating Systems',
  teacherName: TEACHER.name,
  ...overrides,
});

/** Course and join code in one batch, as the app writes them. Pass null to omit either. */
function createCourse(db, course = newCourse(), joinCode = newJoinCode(), code = NEW_CODE) {
  const batch = writeBatch(db);
  if (course) {
    batch.set(doc(db, 'courses', NEW_COURSE), course);
  }
  if (joinCode) {
    batch.set(doc(db, 'joinCodes', code), joinCode);
  }
  return batch.commit();
}

describe('INV-2 course create', () => {
  test('faculty create a course together with its join code', async () => {
    await assertSucceeds(createCourse(t.as(TEACHER)));
  });

  test.each([1, 100])('threshold %i is accepted', async (threshold) => {
    await assertSucceeds(createCourse(t.as(TEACHER), newCourse({ threshold })));
  });

  test('denied: a student creates a course', async () => {
    const course = newCourse({ teacherId: STUDENT.uid });
    const joinCode = newJoinCode({ teacherName: STUDENT.name });
    await assertFails(createCourse(t.as(STUDENT), course, joinCode));
  });

  test('denied: a course without its join code document', async () => {
    await assertFails(createCourse(t.as(TEACHER), newCourse(), null));
  });

  test('denied: a join code without its course', async () => {
    await assertFails(createCourse(t.as(TEACHER), null, newJoinCode()));
  });

  test('denied: a join code that is already taken', async () => {
    const course = newCourse({ joinCode: JOIN_CODE });
    await assertFails(createCourse(t.as(TEACHER), course, newJoinCode(), JOIN_CODE));
  });

  test.each([
    ['the teacher is someone else', { teacherId: OTHER_TEACHER.uid }],
    ['threshold is 0', { threshold: 0 }],
    ['threshold is 101', { threshold: 101 }],
    ['threshold is fractional', { threshold: 75.5 }],
    ['threshold is a string', { threshold: '75' }],
    ['the name is empty', { name: '' }],
    ['the name is longer than 80 characters', { name: 'x'.repeat(81) }],
    ['the code is empty', { code: '' }],
    ['createdAt is a client-chosen time', { createdAt: h.ago(h.SECOND) }],
    ['an extra field is present', { archived: false }],
    ['a required field is missing', { code: DROP }],
  ])('denied when %s', async (_, change) => {
    await assertFails(createCourse(t.as(TEACHER), h.patch(newCourse(), change)));
  });

  test.each([
    ['contains the excluded letter O', 'ABCDEO'],
    ['contains the excluded digit 0', 'ABCDE0'],
    ['contains the excluded digit 1', 'ABCDE1'],
    ['is lower case', 'abcdef'],
    ['has 5 characters', 'ABCDE'],
    ['has 7 characters', 'ABCDEFG'],
  ])('denied when the join code %s', async (_, code) => {
    await assertFails(createCourse(t.as(TEACHER), newCourse({ joinCode: code }), newJoinCode(), code));
  });

  test.each([
    ['points at another course', { courseId: COURSE }],
    ['carries a different course name', { courseName: 'Something Else' }],
    ['carries a teacher name not in the token', { teacherName: 'Someone Else' }],
    ['has an extra field', { hidden: true }],
  ])('denied when the join code document %s', async (_, change) => {
    await assertFails(createCourse(t.as(TEACHER), newCourse(), h.patch(newJoinCode(), change)));
  });
});

describe('INV-2 course update', () => {
  const courseRef = (db) => doc(db, 'courses', COURSE);
  const codeRef = (db) => doc(db, 'joinCodes', JOIN_CODE);

  test('the owner changes the threshold', async () => {
    await assertSucceeds(updateDoc(courseRef(t.as(TEACHER)), { threshold: 80 }));
  });

  test('the owner renames the course and its join code together', async () => {
    const db = t.as(TEACHER);
    const batch = writeBatch(db);
    batch.update(courseRef(db), { name: 'Data Structures II' });
    batch.update(codeRef(db), { courseName: 'Data Structures II' });
    await assertSucceeds(batch.commit());
  });

  test('denied: renaming the course and leaving the join code stale', async () => {
    await assertFails(updateDoc(courseRef(t.as(TEACHER)), { name: 'Data Structures II' }));
  });

  test('denied: renaming only the join code', async () => {
    await assertFails(updateDoc(codeRef(t.as(TEACHER)), { courseName: 'Data Structures II' }));
  });

  test('denied: pointing an existing join code at another course', async () => {
    await t.seed({ 'courses/courseB': h.courseDoc({ joinCode: JOIN_CODE }) });
    await assertFails(updateDoc(codeRef(t.as(TEACHER)), { courseId: 'courseB' }));
  });

  test.each([
    ['another teacher', OTHER_TEACHER],
    ['an enrolled student', STUDENT],
  ])('denied: %s changes the threshold', async (_, user) => {
    await assertFails(updateDoc(courseRef(t.as(user)), { threshold: 50 }));
  });

  test.each([
    ['teacherId', { teacherId: OTHER_TEACHER.uid }],
    ['joinCode', { joinCode: NEW_CODE }],
    ['code', { code: 'CS999' }],
    ['createdAt', { createdAt: serverTimestamp() }],
    ['threshold to 0', { threshold: 0 }],
    ['threshold to 101', { threshold: 101 }],
    ['an unknown field', { archived: true }],
  ])('denied: the owner changes %s', async (_, change) => {
    await assertFails(updateDoc(courseRef(t.as(TEACHER)), change));
  });

  test('denied: deleting a course or a join code', async () => {
    await assertFails(deleteDoc(courseRef(t.as(TEACHER))));
    await assertFails(deleteDoc(codeRef(t.as(TEACHER))));
  });
});

describe('INV-2 course and join code reads', () => {
  test('the owner and an enrolled student read the course', async () => {
    await assertSucceeds(getDoc(doc(t.as(TEACHER), 'courses', COURSE)));
    await assertSucceeds(getDoc(doc(t.as(STUDENT), 'courses', COURSE)));
  });

  test('denied: a non-member or another teacher reads the course', async () => {
    await assertFails(getDoc(doc(t.as(OUTSIDER), 'courses', COURSE)));
    await assertFails(getDoc(doc(t.as(OTHER_TEACHER), 'courses', COURSE)));
  });

  test('the owner lists their own courses', async () => {
    const db = t.as(TEACHER);
    const mine = query(collection(db, 'courses'), where('teacherId', '==', TEACHER.uid));
    const snap = await assertSucceeds(getDocs(mine));
    expect(snap.size).toBe(1);
  });

  test('denied: listing courses without the owner constraint', async () => {
    await assertFails(getDocs(collection(t.as(TEACHER), 'courses')));
    const db = t.as(STUDENT);
    const theirs = query(collection(db, 'courses'), where('teacherId', '==', TEACHER.uid));
    await assertFails(getDocs(theirs));
  });

  test('any signed-in user looks up one join code', async () => {
    const snap = await assertSucceeds(getDoc(doc(t.as(OUTSIDER), 'joinCodes', JOIN_CODE)));
    expect(snap.data().courseId).toBe(COURSE);
  });

  test('denied: listing join codes, or looking one up signed out', async () => {
    await assertFails(getDocs(collection(t.as(TEACHER), 'joinCodes')));
    await assertFails(getDoc(doc(t.anon(), 'joinCodes', JOIN_CODE)));
  });
});
