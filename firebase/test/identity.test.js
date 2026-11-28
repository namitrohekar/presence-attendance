// Invariant 1: profiles and role. The role is derived from the HOD's faculty
// list (or the console's HOD list) on create and update, never chosen by the
// client, and the faculty list takes precedence over the student domain. Student profiles are registrations and
// are covered in registration.test.js.

const {
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, OTHER_STUDENT, UNLISTED, STRANGER, DROP } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const facultyProfile = (user, overrides = {}) => ({
  name: user.name,
  email: user.email,
  role: 'FACULTY',
  createdAt: serverTimestamp(),
  ...overrides,
});
const userRef = (db, user) => doc(db, 'users', user.uid);

describe('INV-1 faculty profile create', () => {
  test('someone on the faculty list creates their profile as FACULTY', async () => {
    await assertSucceeds(
      setDoc(userRef(t.as(OTHER_TEACHER), OTHER_TEACHER), facultyProfile(OTHER_TEACHER)),
    );
  });

  test('an HOD not on the faculty list is faculty too', async () => {
    await assertSucceeds(setDoc(userRef(t.as(DEPUTY), DEPUTY), facultyProfile(DEPUTY)));
  });

  test('the faculty lookup ignores the case of the email', async () => {
    const mixedCase = { ...TEACHER, email: 'Asha.Kulkarni@Example.edu' };
    await assertSucceeds(setDoc(userRef(t.as(mixedCase), mixedCase), facultyProfile(mixedCase)));
  });

  test.each([
    ['the role is STUDENT', { role: 'STUDENT' }],
    ['the role is an unknown value', { role: 'ADMIN' }],
    ['the name differs from the sign-in token', { name: 'Someone Else' }],
    ['the email differs from the sign-in token', { email: 'other@example.edu' }],
    ['createdAt is a client-chosen time', { createdAt: h.ago(h.SECOND) }],
    ['an extra field is present', { isAdmin: true }],
    ['a required field is missing', { createdAt: DROP }],
    ['student fields are attached', { rollNo: '262010009', cohort: 'MC26' }],
  ])('denied when %s', async (_, change) => {
    const data = h.patch(facultyProfile(TEACHER), change);
    await assertFails(setDoc(userRef(t.as(TEACHER), TEACHER), data));
  });

  test.each([
    ['a registered student', STUDENT],
    ['a VJTI student not on the roster', UNLISTED],
    ['an account outside the institute', STRANGER],
  ])('denied: %s writes a FACULTY profile', async (_, user) => {
    await assertFails(setDoc(userRef(t.as(user), user), facultyProfile(user)));
  });

  test('denied for another uid', async () => {
    await assertFails(setDoc(userRef(t.as(TEACHER), STUDENT), facultyProfile(TEACHER)));
  });

  test('denied when the email is not verified, or signed out', async () => {
    const unverified = t.as(TEACHER, { email_verified: false });
    await assertFails(setDoc(userRef(unverified, TEACHER), facultyProfile(TEACHER)));
    await assertFails(setDoc(userRef(t.anon(), TEACHER), facultyProfile(TEACHER)));
  });
});

describe('INV-1 role follows the allowlist on every profile write', () => {
  const stored = (user) => facultyProfile(user, { createdAt: h.ago(h.MINUTE) });

  beforeEach(async () => {
    await t.seed({
      [`users/${TEACHER.uid}`]: stored(TEACHER),
      [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT),
    });
  });

  test('faculty refresh their name from the sign-in token', async () => {
    const db = t.as(TEACHER, { name: 'Asha R. Kulkarni' });
    await assertSucceeds(updateDoc(userRef(db, TEACHER), { name: 'Asha R. Kulkarni' }));
  });

  test('denied: a student promotes themselves to FACULTY', async () => {
    const db = t.as(STUDENT);
    await assertFails(updateDoc(userRef(db, STUDENT), { role: 'FACULTY' }));
    const asFaculty = { role: 'FACULTY', rollNo: deleteField(), cohort: deleteField() };
    await assertFails(updateDoc(userRef(db, STUDENT), asFaculty));
  });

  test('faculty precedence: a VJTI address added to the faculty list becomes FACULTY', async () => {
    await t.seed({ [`faculty/${STUDENT.email}`]: h.facultyDoc(STUDENT) });
    const db = t.as(STUDENT);
    // The student shape is no longer valid for this account...
    await assertFails(updateDoc(userRef(db, STUDENT), { name: STUDENT.name }));
    // ...only the faculty shape is.
    const asFaculty = { role: 'FACULTY', rollNo: deleteField(), cohort: deleteField() };
    await assertSucceeds(updateDoc(userRef(db, STUDENT), asFaculty));
  });

  test('denied: a user removed from the faculty list keeps writing FACULTY', async () => {
    await t.seed({ [`users/${UNLISTED.uid}`]: stored(UNLISTED) });
    await assertFails(updateDoc(userRef(t.as(UNLISTED), UNLISTED), { name: UNLISTED.name }));
  });

  test('denied: changing createdAt, updating another user, deleting a profile', async () => {
    const db = t.as(TEACHER);
    await assertFails(updateDoc(userRef(db, TEACHER), { createdAt: serverTimestamp() }));
    await assertFails(updateDoc(userRef(db, STUDENT), { name: 'Changed' }));
    await assertFails(deleteDoc(userRef(db, TEACHER)));
  });
});

describe('INV-1 least-privilege reads', () => {
  beforeEach(async () => {
    await t.seed({ [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT) });
  });

  test('a user reads their own profile', async () => {
    await assertSucceeds(getDoc(userRef(t.as(STUDENT), STUDENT)));
  });

  test('denied: reading or listing other profiles', async () => {
    await assertFails(getDoc(userRef(t.as(OTHER_STUDENT), STUDENT)));
    await assertFails(getDoc(userRef(t.as(TEACHER), STUDENT)));
    await assertFails(getDocs(collection(t.as(TEACHER), 'users')));
  });

  test('the HOD reads their own HOD entry, and a teacher learns they have none', async () => {
    expect((await assertSucceeds(getDoc(doc(t.as(TEACHER), 'hods', TEACHER.email)))).exists()).toBe(
      true,
    );
    const own = await assertSucceeds(getDoc(doc(t.as(OTHER_TEACHER), 'hods', OTHER_TEACHER.email)));
    expect(own.exists()).toBe(false);
  });

  test.each([
    ['the HOD', TEACHER],
    ['a teacher', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('denied: %s reading another HOD entry, listing or writing them', async (_, user) => {
    const db = t.as(user);
    await assertFails(getDoc(doc(db, 'hods', DEPUTY.email)));
    await assertFails(getDocs(collection(db, 'hods')));
    await assertFails(setDoc(doc(db, 'hods', user.email), {}));
    await assertFails(deleteDoc(doc(db, 'hods', TEACHER.email)));
  });

  test('a signed-in user reads the campus config', async () => {
    await assertSucceeds(getDoc(doc(t.as(STUDENT), 'appConfig/campus')));
  });

  test('denied: writing the campus config or reading other config', async () => {
    const db = t.as(TEACHER);
    await assertFails(setDoc(doc(db, 'appConfig/campus'), h.GEOFENCE));
    await assertFails(getDoc(doc(db, 'appConfig/other')));
  });

  test('denied: any collection without a rule', async () => {
    const db = t.as(TEACHER);
    await assertFails(getDoc(doc(db, 'secrets/one')));
    await assertFails(setDoc(doc(db, 'secrets/one'), { a: 1 }));
  });
});
