// Invariant 1: identity. Verified email required; role derived from the
// allowlist on create and update, never chosen by the client.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, STUDENT, OTHER_STUDENT, DROP, assertFails, assertSucceeds } = h;
const t = h.setup();

const profile = (user, role, overrides = {}) => ({
  name: user.name,
  email: user.email,
  role,
  createdAt: serverTimestamp(),
  ...overrides,
});
const storedProfile = (user, role) => profile(user, role, { createdAt: h.ago(h.MINUTE) });
const userRef = (db, user) => doc(db, 'users', user.uid);

describe('INV-1 profile create', () => {
  test('a verified, non-allowlisted user creates their profile as STUDENT', async () => {
    await assertSucceeds(setDoc(userRef(t.as(STUDENT), STUDENT), profile(STUDENT, 'STUDENT')));
  });

  test('an allowlisted user creates their profile as FACULTY', async () => {
    await assertSucceeds(setDoc(userRef(t.as(TEACHER), TEACHER), profile(TEACHER, 'FACULTY')));
  });

  test('the allowlist lookup ignores the case of the email', async () => {
    const mixedCase = { ...TEACHER, email: 'Asha.Kulkarni@Example.edu' };
    await assertSucceeds(setDoc(userRef(t.as(mixedCase), mixedCase), profile(mixedCase, 'FACULTY')));
  });

  test.each([
    ['a student claims FACULTY', STUDENT, { role: 'FACULTY' }],
    ['an allowlisted teacher claims STUDENT', TEACHER, { role: 'STUDENT' }],
    ['the role is an unknown value', STUDENT, { role: 'ADMIN' }],
    ['the name differs from the sign-in token', STUDENT, { name: 'Someone Else' }],
    ['the email differs from the sign-in token', STUDENT, { email: 'other@example.edu' }],
    ['createdAt is a client-chosen time', STUDENT, { createdAt: h.ago(h.SECOND) }],
    ['an extra field is present', STUDENT, { isAdmin: true }],
    ['a required field is missing', STUDENT, { createdAt: DROP }],
  ])('denied when %s', async (_, user, change) => {
    const role = user === TEACHER ? 'FACULTY' : 'STUDENT';
    await assertFails(setDoc(userRef(t.as(user), user), h.patch(profile(user, role), change)));
  });

  test('denied for another uid', async () => {
    await assertFails(
      setDoc(userRef(t.as(STUDENT), OTHER_STUDENT), profile(STUDENT, 'STUDENT')),
    );
  });

  test('denied when the email is not verified', async () => {
    const db = t.as(STUDENT, { email_verified: false });
    await assertFails(setDoc(userRef(db, STUDENT), profile(STUDENT, 'STUDENT')));
    await assertFails(getDoc(doc(db, 'appConfig/campus')));
  });

  test('denied when signed out', async () => {
    await assertFails(setDoc(userRef(t.anon(), STUDENT), profile(STUDENT, 'STUDENT')));
    await assertFails(getDoc(doc(t.anon(), 'appConfig/campus')));
  });
});

describe('INV-1 profile update', () => {
  beforeEach(async () => {
    await t.seed({ [`users/${STUDENT.uid}`]: storedProfile(STUDENT, 'STUDENT') });
  });

  test('a student can refresh their name from the sign-in token', async () => {
    const db = t.as(STUDENT, { name: 'Meera S. Joshi' });
    await assertSucceeds(updateDoc(userRef(db, STUDENT), { name: 'Meera S. Joshi' }));
  });

  test('denied: a student promotes themselves to FACULTY', async () => {
    await assertFails(updateDoc(userRef(t.as(STUDENT), STUDENT), { role: 'FACULTY' }));
  });

  test('a user added to the allowlist becomes FACULTY on the next profile write', async () => {
    await t.seed({ [`teacherAllowlist/${STUDENT.email}`]: {} });
    await assertSucceeds(updateDoc(userRef(t.as(STUDENT), STUDENT), { role: 'FACULTY' }));
  });

  test('denied: an allowlisted user keeps writing STUDENT', async () => {
    await t.seed({ [`teacherAllowlist/${STUDENT.email}`]: {} });
    await assertFails(updateDoc(userRef(t.as(STUDENT), STUDENT), { name: STUDENT.name }));
  });

  test('denied: a user removed from the allowlist keeps FACULTY', async () => {
    await t.seed({ [`users/${OTHER_STUDENT.uid}`]: storedProfile(OTHER_STUDENT, 'FACULTY') });
    await assertFails(
      updateDoc(userRef(t.as(OTHER_STUDENT), OTHER_STUDENT), { name: OTHER_STUDENT.name }),
    );
  });

  test('denied: changing createdAt', async () => {
    await assertFails(
      updateDoc(userRef(t.as(STUDENT), STUDENT), { createdAt: serverTimestamp() }),
    );
  });

  test('denied: updating another user', async () => {
    await assertFails(updateDoc(userRef(t.as(OTHER_STUDENT), STUDENT), { name: 'Changed' }));
  });

  test('denied: deleting a profile', async () => {
    await assertFails(deleteDoc(userRef(t.as(STUDENT), STUDENT)));
  });
});

describe('INV-1 least-privilege reads', () => {
  beforeEach(async () => {
    await t.seed({ [`users/${STUDENT.uid}`]: storedProfile(STUDENT, 'STUDENT') });
  });

  test('a user reads their own profile', async () => {
    await assertSucceeds(getDoc(userRef(t.as(STUDENT), STUDENT)));
  });

  test('denied: reading or listing other profiles', async () => {
    await assertFails(getDoc(userRef(t.as(OTHER_STUDENT), STUDENT)));
    await assertFails(getDoc(userRef(t.as(TEACHER), STUDENT)));
    await assertFails(getDocs(collection(t.as(TEACHER), 'users')));
  });

  test.each([
    ['a teacher', TEACHER],
    ['a student', STUDENT],
  ])('denied: %s reading, listing or writing the allowlist', async (_, user) => {
    const db = t.as(user);
    await assertFails(getDoc(doc(db, 'teacherAllowlist', user.email)));
    await assertFails(getDocs(collection(db, 'teacherAllowlist')));
    await assertFails(setDoc(doc(db, 'teacherAllowlist', user.email), {}));
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
