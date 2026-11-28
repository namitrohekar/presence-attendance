// Department figures: the HOD counts any course's sessions, students and
// marks. Teachers count only their own courses; students only themselves.

const { collection, getCountFromServer, getDoc, doc, query, where } = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, COURSE, SESSION } = h;
const { assertFails, assertSucceeds } = h;

// The course belongs to the other teacher, so only the HOD reads it as HOD.
const t = h.setup(() => ({
  [`courses/${COURSE}`]: h.courseDoc({ teacherId: OTHER_TEACHER.uid }),
  [`memberships/${COURSE}_${STUDENT.uid}`]: h.membershipDoc(STUDENT, { teacherId: OTHER_TEACHER.uid }),
  [`memberships/${COURSE}_${h.OTHER_STUDENT.uid}`]: h.membershipDoc(h.OTHER_STUDENT, {
    teacherId: OTHER_TEACHER.uid,
  }),
  [`sessions/${SESSION}`]: h.sessionDoc({ teacherId: OTHER_TEACHER.uid, status: 'ENDED' }),
  [`attendance/${SESSION}_${STUDENT.uid}`]: h.storedMark(STUDENT, { teacherId: OTHER_TEACHER.uid }),
}));

const count = (db, name) =>
  getCountFromServer(query(collection(db, name), where('courseId', '==', COURSE))).then(
    (snapshot) => snapshot.data().count,
  );

describe('department figures', () => {
  test("the HOD counts another teacher's sessions, students and marks", async () => {
    for (const hod of [TEACHER, DEPUTY]) {
      const db = t.as(hod);
      expect(await assertSucceeds(count(db, 'sessions'))).toBe(1);
      expect(await assertSucceeds(count(db, 'memberships'))).toBe(2);
      expect(await assertSucceeds(count(db, 'attendance'))).toBe(1);
      await assertSucceeds(getDoc(doc(db, 'sessions', SESSION)));
    }
  });

  // Only the HOD may count without naming themself as the teacher or the student.
  test.each([
    ['a teacher', OTHER_TEACHER, 'sessions'],
    ['a teacher', OTHER_TEACHER, 'memberships'],
    ['a teacher', OTHER_TEACHER, 'attendance'],
    ['a student', STUDENT, 'attendance'],
    ['a student', STUDENT, 'memberships'],
  ])('denied: %s counts a whole course\'s %s', async (_, user, name) => {
    await assertFails(count(t.as(user), name));
  });
});
