// The faculty list: the HOD (named in the console) keeps it. Someone on it may
// sign in as faculty, and loses that as soon as they are removed. The HOD is
// faculty without being on it. Students cannot read it.

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
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, STRANGER, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

// A teacher with no account yet, as the timetable names them.
const NEW_TEACHER = { uid: 'teacherN', email: 'gajanan.sidam@example.edu', name: 'Gajanan Sidam' };
const ref = (db, email) => doc(db, 'faculty', email);
const member = (overrides = {}) => ({
  email: NEW_TEACHER.email,
  name: NEW_TEACHER.name,
  initials: 'GS',
  addedBy: TEACHER.uid,
  addedAt: serverTimestamp(),
  ...overrides,
});
const profile = (who) => ({
  name: who.name,
  email: who.email,
  role: 'FACULTY',
  createdAt: serverTimestamp(),
});

describe('faculty list: the HOD keeps it', () => {
  test('the HOD adds a teacher who has never signed in', async () => {
    const db = t.as(TEACHER);
    const added = flows.addFaculty(db, TEACHER, { ...NEW_TEACHER, initials: 'GS' });
    expect(await assertSucceeds(added)).toBe('ADDED');
    expect((await getDoc(ref(db, NEW_TEACHER.email))).data().initials).toBe('GS');
  });

  test('the flow never overwrites someone already on the list', async () => {
    const db = t.as(TEACHER);
    expect(await flows.addFaculty(db, TEACHER, { ...OTHER_TEACHER, initials: 'X' })).toBe('EXISTS');
    expect((await getDoc(ref(db, OTHER_TEACHER.email))).data().initials).toBe('VR');
  });

  test('the HOD corrects a name and initials, and removes someone', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(
      updateDoc(ref(db, OTHER_TEACHER.email), { name: 'Vikram S. Rao', initials: 'VSR' }),
    );
    await assertSucceeds(deleteDoc(ref(db, OTHER_TEACHER.email)));
  });

  test('a second HOD, not on the list themself, keeps it too', async () => {
    await assertSucceeds(setDoc(ref(t.as(DEPUTY), NEW_TEACHER.email), member({ addedBy: DEPUTY.uid })));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('denied: %s adds, edits or removes faculty', async (_, user) => {
    const db = t.as(user);
    await assertFails(setDoc(ref(db, NEW_TEACHER.email), member({ addedBy: user.uid })));
    await assertFails(updateDoc(ref(db, OTHER_TEACHER.email), { name: 'Someone' }));
    await assertFails(deleteDoc(ref(db, OTHER_TEACHER.email)));
  });

  test('denied: an entry whose id and address are both not an address', async () => {
    const db = t.as(TEACHER);
    await assertFails(setDoc(ref(db, 'gajanan'), member({ email: 'gajanan' })));
    await assertFails(setDoc(ref(db, 'g s@example.edu'), member({ email: 'g s@example.edu' })));
  });

  test('denied: someone outside VJTI adds themself', async () => {
    await assertFails(setDoc(ref(t.as(STRANGER), STRANGER.email), member({ email: STRANGER.email })));
  });

  test.each([
    ['an id that is not the address', { email: 'someone.else@example.edu' }],
    ['an address with capital letters', { email: 'Gajanan.Sidam@example.edu' }],
    ['an address without a domain', { email: 'gajanan' }],
    ['an empty name', { name: '' }],
    ['a name with a trailing space', { name: 'Gajanan Sidam ' }],
    ['lower-case initials', { initials: 'gs' }],
    ['five initials', { initials: 'GSABC' }],
    ['no initials', { initials: DROP }],
    ['someone else as the one who added them', { addedBy: OTHER_TEACHER.uid }],
    ['a client-chosen time', { addedAt: h.ago(DAY) }],
    ['an extra field', { role: 'HOD' }],
  ])('denied: an entry with %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(TEACHER), NEW_TEACHER.email), h.patch(member(), change)));
  });

  test.each([
    ['the address', { email: 'other@example.edu' }],
    ['who added them', { addedBy: DEPUTY.uid }],
    ['when they were added', { addedAt: serverTimestamp() }],
    ['initials that are not initials', { initials: 'v r' }],
  ])('denied: changing %s', async (_, change) => {
    await assertFails(updateDoc(ref(t.as(TEACHER), OTHER_TEACHER.email), change));
  });
});

describe('faculty list: what being on it means', () => {
  test('a teacher added by the HOD signs in as faculty', async () => {
    await flows.addFaculty(t.as(TEACHER), TEACHER, { ...NEW_TEACHER, initials: 'GS' });
    const db = t.as(NEW_TEACHER);
    await assertSucceeds(setDoc(doc(db, 'users', NEW_TEACHER.uid), profile(NEW_TEACHER)));
  });

  test('denied: someone not on it signs in as faculty', async () => {
    const db = t.as(NEW_TEACHER);
    await assertFails(setDoc(doc(db, 'users', NEW_TEACHER.uid), profile(NEW_TEACHER)));
  });

  test('denied: a teacher removed from it keeps faculty rights', async () => {
    await deleteDoc(ref(t.as(TEACHER), OTHER_TEACHER.email));
    const db = t.as(OTHER_TEACHER);
    await assertFails(setDoc(doc(db, 'users', OTHER_TEACHER.uid), profile(OTHER_TEACHER)));
    await assertFails(getDocs(collection(db, 'studentRegistry')));
  });

  test('faculty read the list; students cannot', async () => {
    expect((await assertSucceeds(getDocs(collection(t.as(OTHER_TEACHER), 'faculty')))).size).toBe(2);
    await assertSucceeds(getDocs(collection(t.as(DEPUTY), 'faculty')));
    await assertFails(getDocs(collection(t.as(STUDENT), 'faculty')));
    await assertFails(getDoc(ref(t.as(STUDENT), TEACHER.email)));
  });
});
