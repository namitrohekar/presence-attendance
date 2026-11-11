// Invariant 8: attendance reads. A student reads only their own records; a
// teacher reads only records where they are the teacher. Coordinates are
// therefore visible to the student and the course's teacher and nobody else.

const { collection, doc, getDoc, getDocs, orderBy, query, where } = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, COURSE, SESSION } = h;
const { assertFails, assertSucceeds } = h;

const t = h.setup(() => ({
  [`attendance/${h.attendanceId(STUDENT)}`]: h.storedMark(STUDENT),
  [`attendance/${h.attendanceId(OTHER_STUDENT)}`]: h.storedMark(OTHER_STUDENT),
}));

const ref = (db, student, session = SESSION) =>
  doc(db, 'attendance', h.attendanceId(student, session));
const attendance = (db, ...constraints) => query(collection(db, 'attendance'), ...constraints);

describe('INV-8 attendance reads', () => {
  test('a student reads their own record, including coordinates', async () => {
    const snap = await assertSucceeds(getDoc(ref(t.as(STUDENT), STUDENT)));
    expect(snap.data().lat).toBeCloseTo(h.INSIDE.lat);
  });

  test('a student checking a session they have not marked gets "not found"', async () => {
    const snap = await assertSucceeds(getDoc(ref(t.as(STUDENT), STUDENT, 'sessZ')));
    expect(snap.exists()).toBe(false);
  });

  test('a student lists their own records, optionally for one course', async () => {
    const db = t.as(STUDENT);
    const mine = await assertSucceeds(getDocs(attendance(db, where('studentId', '==', STUDENT.uid))));
    expect(mine.size).toBe(1);
    const forCourse = attendance(
      db,
      where('studentId', '==', STUDENT.uid),
      where('courseId', '==', COURSE),
    );
    await assertSucceeds(getDocs(forCourse));
  });

  test('the teacher reads one record and lists the session roster, newest first', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(getDoc(ref(db, STUDENT)));
    const roster = attendance(
      db,
      where('sessionId', '==', SESSION),
      where('teacherId', '==', TEACHER.uid),
      orderBy('markedAt', 'desc'),
    );
    const snap = await assertSucceeds(getDocs(roster));
    expect(snap.size).toBe(2);
  });

  test('denied: a student reads a classmate or lists the session roster', async () => {
    const db = t.as(STUDENT);
    await assertFails(getDoc(ref(db, OTHER_STUDENT)));
    await assertFails(getDocs(attendance(db, where('sessionId', '==', SESSION))));
    await assertFails(getDocs(attendance(db, where('studentId', '==', OTHER_STUDENT.uid))));
  });

  test('denied: another teacher reads a record or lists the session', async () => {
    const db = t.as(OTHER_TEACHER);
    await assertFails(getDoc(ref(db, STUDENT)));
    await assertFails(getDocs(attendance(db, where('sessionId', '==', SESSION))));
    await assertFails(getDocs(attendance(db, where('teacherId', '==', TEACHER.uid))));
  });

  test('another teacher querying with their own id gets an empty result', async () => {
    const db = t.as(OTHER_TEACHER);
    const mine = attendance(
      db,
      where('sessionId', '==', SESSION),
      where('teacherId', '==', OTHER_TEACHER.uid),
    );
    const snap = await assertSucceeds(getDocs(mine));
    expect(snap.size).toBe(0);
  });

  test('denied: listing without constraints, or reading signed out', async () => {
    await assertFails(getDocs(collection(t.as(TEACHER), 'attendance')));
    await assertFails(getDoc(ref(t.anon(), STUDENT)));
  });
});
