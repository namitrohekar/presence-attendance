// Replays the Firestore writes the Android app makes, in the same order and
// inside the same transactions, against the rules. The documents are built
// from contracts/documents.json, the field lists that MapperContractTest
// holds the app's mappers to. If the app, the contract and the rules ever
// disagree about a document's shape, either this file or that test fails.

const fs = require('fs');
const path = require('path');
const {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  runTransaction,
  serverTimestamp,
  where,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, STUDENT, OUTSIDER, assertFails, assertSucceeds } = h;
const t = h.setup();

const contract = JSON.parse(
  fs.readFileSync(path.join(h.CONTRACTS_DIR, 'documents.json'), 'utf8'),
);

/** A document with exactly the contract's fields for a collection, plus any optional ones given. */
function build(collectionName, values, optional = {}) {
  const out = {};
  for (const field of contract[collectionName].split(',')) {
    if (!(field in values)) {
      throw new Error(`${collectionName}.${field} is in the contract but the test has no value`);
    }
    out[field] = values[field];
  }
  const allowed = (contract[`${collectionName}.optional`] ?? '').split(',');
  for (const [field, value] of Object.entries(optional)) {
    if (!allowed.includes(field)) {
      throw new Error(`${collectionName}.${field} is not an optional field in the contract`);
    }
    out[field] = value;
  }
  return out;
}

/** FirebaseAuthRepository.writeProfile: create with a proposed role, or update the stored one. */
function writeProfile(db, user, proposedRole) {
  const ref = doc(db, 'users', user.uid);
  return runTransaction(db, async (tx) => {
    const snapshot = await tx.get(ref);
    if (snapshot.exists()) {
      tx.update(ref, { name: user.name, email: user.email, role: proposedRole });
    } else {
      tx.set(
        ref,
        build('users', {
          name: user.name,
          email: user.email,
          role: proposedRole,
          createdAt: serverTimestamp(),
        }),
      );
    }
  });
}

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
      build('courses', {
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
      build('joinCodes', {
        courseId,
        courseName: 'Compiler Design',
        teacherName: teacher.name,
        teacherId: teacher.uid,
      }),
    );
    return true;
  });
}

/** FirestoreCourseRepository.joinCourse. Resolves to false when already a member. */
function joinCourse(db, student, info, rollNo) {
  const ref = doc(db, 'memberships', `${info.courseId}_${student.uid}`);
  return runTransaction(db, async (tx) => {
    if ((await tx.get(ref)).exists()) {
      return false;
    }
    const values = {
      courseId: info.courseId,
      teacherId: info.teacherId,
      studentId: student.uid,
      name: student.name,
      email: student.email,
      joinCode: info.code,
      joinedAt: serverTimestamp(),
    };
    tx.set(ref, build('memberships', values, rollNo ? { rollNo } : {}));
    return true;
  });
}

describe('app flow: sign-in profile and role', () => {
  test('a new student: the first proposal (STUDENT) is accepted', async () => {
    await assertSucceeds(writeProfile(t.as(STUDENT), STUDENT, 'STUDENT'));
  });

  test('a new teacher: STUDENT is denied, then FACULTY is accepted', async () => {
    const db = t.as(TEACHER);
    await assertFails(writeProfile(db, TEACHER, 'STUDENT'));
    await assertSucceeds(writeProfile(db, TEACHER, 'FACULTY'));
  });

  test('a returning user: the stored role is proposed again and accepted', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(writeProfile(db, TEACHER, 'FACULTY'));
    await assertSucceeds(writeProfile(db, TEACHER, 'FACULTY'));
    expect((await getDoc(doc(db, 'users', TEACHER.uid))).data().role).toBe('FACULTY');
  });

  test('a student later added to the allowlist is moved to FACULTY at the next sign-in', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(writeProfile(db, STUDENT, 'STUDENT'));
    await t.seed({ [`teacherAllowlist/${STUDENT.email}`]: {} });
    await assertFails(writeProfile(db, STUDENT, 'STUDENT'));
    await assertSucceeds(writeProfile(db, STUDENT, 'FACULTY'));
  });
});

describe('app flow: create a course, then join it', () => {
  const COURSE_ID = 'courseFlow';
  const CODE = 'HJK482';

  test('the teacher creates a course and sees it in their list', async () => {
    const db = t.as(TEACHER);
    expect(await assertSucceeds(createCourse(db, TEACHER, COURSE_ID, CODE))).toBe(true);
    const mine = query(collection(db, 'courses'), where('teacherId', '==', TEACHER.uid));
    const ids = (await assertSucceeds(getDocs(mine))).docs.map((d) => d.id);
    expect(ids).toContain(COURSE_ID);
  });

  test('a taken join code is detected, so the app can pick another', async () => {
    expect(await createCourse(t.as(TEACHER), TEACHER, COURSE_ID, h.JOIN_CODE)).toBe(false);
  });

  test('a student is denied when a non-teacher tries the same transaction', async () => {
    await assertFails(createCourse(t.as(STUDENT), STUDENT, COURSE_ID, CODE));
  });

  test('a student resolves the code, joins, and then can read the course', async () => {
    await createCourse(t.as(TEACHER), TEACHER, COURSE_ID, CODE);
    const db = t.as(OUTSIDER);

    // Before joining, the join code is the only thing the student can read.
    await assertFails(getDoc(doc(db, 'courses', COURSE_ID)));
    const code = await assertSucceeds(getDoc(doc(db, 'joinCodes', CODE)));
    const info = { code: code.id, ...code.data() };
    expect(info.teacherId).toBe(TEACHER.uid);

    expect(await assertSucceeds(joinCourse(db, OUTSIDER, info, '21CS045'))).toBe(true);

    // The home screen: memberships first, then each course.
    const memberships = query(collection(db, 'memberships'), where('studentId', '==', OUTSIDER.uid));
    const joined = (await assertSucceeds(getDocs(memberships))).docs.map((d) => d.data().courseId);
    expect(joined).toEqual([COURSE_ID]);
    const course = await assertSucceeds(getDoc(doc(db, 'courses', COURSE_ID)));
    expect(course.data().name).toBe('Compiler Design');
  });

  test('joining without a roll number, and joining twice', async () => {
    await createCourse(t.as(TEACHER), TEACHER, COURSE_ID, CODE);
    const db = t.as(OUTSIDER);
    const info = { code: CODE, courseId: COURSE_ID, teacherId: TEACHER.uid };
    expect(await assertSucceeds(joinCourse(db, OUTSIDER, info))).toBe(true);
    expect(await assertSucceeds(joinCourse(db, OUTSIDER, info))).toBe(false);
  });
});
