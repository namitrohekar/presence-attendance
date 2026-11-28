// Student registration. A student enters their roll number once. It succeeds
// only if the address in the sign-in token is in the student domain, is on
// the faculty-managed roster, the entered number equals the roster's, and
// the roster entry is not already bound to a different sign-in. Afterwards
// the roll number can only change through an audited roster correction.

const {
  deleteField,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, STUDENT, OTHER_STUDENT, LISTED, UNLISTED, STRANGER, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const entryRef = (db, who) => doc(db, 'studentRegistry', who.email);
const profileRef = (db, who) => doc(db, 'users', who.uid);
const admin = () => t.as(TEACHER);
const entryOf = async (who) => (await getDoc(entryRef(admin(), who))).data();

const claim = (who, overrides = {}) => ({
  registeredUid: who.uid,
  registeredAt: serverTimestamp(),
  ...overrides,
});
const profile = (who, overrides = {}) => ({
  name: who.name,
  email: who.email,
  role: 'STUDENT',
  createdAt: serverTimestamp(),
  rollNo: who.rollNo,
  cohort: who.cohort,
  ...overrides,
});

/** Claim and profile in one batch with no client-side checks, so the rules decide. */
function rawRegister(db, who, options = {}) {
  const batch = writeBatch(db);
  if (!options.skipClaim) {
    batch.update(entryRef(db, options.entryOf ?? who), h.patch(claim(who), options.claim ?? {}));
  }
  if (!options.skipProfile) {
    batch.set(profileRef(db, who), h.patch(profile(who), options.profile ?? {}));
  }
  return batch.commit();
}

describe('registration: the happy path', () => {
  test('a student on the roster registers with their roll number', async () => {
    const db = t.as(LISTED);
    expect(await assertSucceeds(flows.register(db, LISTED, LISTED.rollNo))).toBe('REGISTERED');

    const stored = (await getDoc(profileRef(db, LISTED))).data();
    expect(stored).toMatchObject({ role: 'STUDENT', rollNo: LISTED.rollNo, cohort: 'MC26' });
    const entry = await entryOf(LISTED);
    expect(entry.registeredUid).toBe(LISTED.uid);
    expect(entry.rollNo).toBe(LISTED.rollNo);
  });

  test('the unchecked batch passes when every part is correct', async () => {
    await assertSucceeds(rawRegister(t.as(LISTED), LISTED));
  });

  test('an existing profile from before the roster is upgraded in place', async () => {
    const legacy = { name: LISTED.name, email: LISTED.email, role: 'STUDENT', createdAt: h.ago(DAY) };
    await t.seed({ [`users/${LISTED.uid}`]: legacy });
    const db = t.as(LISTED);
    expect(await assertSucceeds(flows.register(db, LISTED, LISTED.rollNo))).toBe('REGISTERED');
    const stored = (await getDoc(profileRef(db, LISTED))).data();
    expect(stored.rollNo).toBe(LISTED.rollNo);
    expect(stored.createdAt.toMillis()).toBeLessThan(Date.now() - DAY + h.MINUTE);
  });

  test('registering again with the same sign-in is harmless', async () => {
    const db = t.as(LISTED);
    await assertSucceeds(flows.register(db, LISTED, LISTED.rollNo));
    expect(await assertSucceeds(flows.register(db, LISTED, LISTED.rollNo))).toBe('REGISTERED');
  });

  test('a registered student refreshes their name from the sign-in token', async () => {
    await t.seed({ [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT) });
    const db = t.as(STUDENT, { name: 'Meera S. Joshi' });
    await assertSucceeds(updateDoc(profileRef(db, STUDENT), { name: 'Meera S. Joshi' }));
  });
});

describe('registration: what the app tells the student', () => {
  test('not on the roster', async () => {
    expect(await flows.register(t.as(UNLISTED), UNLISTED, '262010009')).toBe('NOT_ON_REGISTER');
  });

  test('a roll number that does not match the roster', async () => {
    expect(await flows.register(t.as(LISTED), LISTED, '262019999')).toBe('ROLL_MISMATCH');
    expect(await flows.register(t.as(LISTED), LISTED, STUDENT.rollNo)).toBe('ROLL_MISMATCH');
    expect((await entryOf(LISTED)).registeredUid).toBeUndefined();
  });

  test('an entry already registered to a different sign-in', async () => {
    const recreated = { ...STUDENT, uid: 'studentAnew' };
    const outcome = flows.register(t.as(recreated), recreated, STUDENT.rollNo);
    expect(await outcome).toBe('DUPLICATE_CLAIM');
  });
});

describe('registration: the rules refuse every shortcut', () => {
  test.each([
    ['a roll number that is not the roster\'s', { profile: { rollNo: '262019999' } }],
    ['another student\'s roll number', { profile: { rollNo: STUDENT.rollNo } }],
    ['a cohort that is not the roster\'s', { profile: { cohort: 'MC25' } }],
    ['no roll number', { profile: { rollNo: DROP } }],
    ['no cohort', { profile: { cohort: DROP } }],
    ['a profile without claiming the roster entry', { skipClaim: true }],
    ['the role FACULTY', { profile: { role: 'FACULTY' } }],
    ['a name that is not the token\'s', { profile: { name: 'Someone Else' } }],
    ['an email that is not the token\'s', { profile: { email: STUDENT.email } }],
    ['a client-chosen createdAt', { profile: { createdAt: h.ago(DAY) } }],
    ['an extra profile field', { profile: { verified: true } }],
    ['a claim naming a different uid', { claim: { registeredUid: 'someoneElse' } }],
    ['a claim with a client-chosen time', { claim: { registeredAt: h.ago(DAY) } }],
    ['a claim without a time', { claim: { registeredAt: DROP } }],
    ['a claim that also changes the roll number', { claim: { rollNo: '262019999' } }],
    ['a claim that also changes the cohort', { claim: { cohort: 'MC25' } }],
    ['a claim that also changes who added the entry', { claim: { addedBy: LISTED.uid } }],
  ])('denied: registering with %s', async (_, options) => {
    await assertFails(rawRegister(t.as(LISTED), LISTED, options));
    // The batch is atomic: a refused profile never leaves a claim behind.
    expect((await entryOf(LISTED)).registeredUid).toBeUndefined();
  });

  // The claim on its own, without a profile to catch the problem for it.
  test.each([
    ['names a different uid', { registeredUid: 'someoneElse' }],
    ['has a client-chosen time', { registeredAt: h.ago(DAY) }],
    ['also changes the roll number', { rollNo: '262019999' }],
    ['also changes the cohort', { cohort: 'MC25' }],
    ['also changes the revision', { revision: 3 }],
  ])('denied: a claim alone that %s', async (_, change) => {
    await assertFails(updateDoc(entryRef(t.as(LISTED), LISTED), h.patch(claim(LISTED), change)));
  });

  test('denied: rewriting the roster\'s roll number while claiming, with a profile to match', async () => {
    const forged = '262019999';
    const options = { claim: { rollNo: forged }, profile: { rollNo: forged } };
    await assertFails(rawRegister(t.as(LISTED), LISTED, options));
    expect((await entryOf(LISTED)).rollNo).toBe(LISTED.rollNo);
  });

  test('denied: a VJTI student who is not on the roster', async () => {
    const who = { ...UNLISTED, rollNo: '262010009', cohort: 'MC26' };
    await assertFails(rawRegister(t.as(who), who, { skipClaim: true }));
    await assertFails(setDoc(entryRef(t.as(who), who), h.registryDoc(who, { addedBy: who.uid })));
  });

  test('denied: an account outside the institute, even with a roster entry seeded for it', async () => {
    const who = { ...STRANGER, rollNo: '262010009', cohort: 'MC26' };
    await t.seed({ [h.registryPath(who)]: h.registryDoc(who) });
    await assertFails(rawRegister(t.as(who), who));
    await assertFails(rawRegister(t.as(who), who, { skipClaim: true }));
  });

  test('denied: claiming a classmate\'s entry', async () => {
    const db = t.as(STUDENT);
    await assertFails(updateDoc(entryRef(db, LISTED), claim(STUDENT)));
    await assertFails(rawRegister(db, STUDENT, { entryOf: LISTED, skipProfile: true }));
  });

  test('denied: an entry already registered to a different sign-in', async () => {
    const recreated = { ...STUDENT, uid: 'studentAnew' };
    const db = t.as(recreated);
    await assertFails(rawRegister(db, recreated));
    await assertFails(rawRegister(db, recreated, { skipClaim: true }));
    await assertFails(updateDoc(entryRef(db, recreated), claim(recreated)));
    expect((await entryOf(STUDENT)).registeredUid).toBe(STUDENT.uid);
  });

  test('faculty precedence: a VJTI address on the faculty list cannot claim an entry', async () => {
    await t.seed({ [`faculty/${LISTED.email}`]: h.facultyDoc(LISTED) });
    await assertFails(updateDoc(entryRef(t.as(LISTED), LISTED), claim(LISTED)));
  });

  test('faculty precedence: a VJTI address on the faculty list cannot hold a student profile', async () => {
    // Even with the entry already claimed, the student shape is refused.
    await t.seed({
      [`faculty/${LISTED.email}`]: h.facultyDoc(LISTED),
      [h.registryPath(LISTED)]: h.claimedRegistryDoc(LISTED),
    });
    await assertFails(rawRegister(t.as(LISTED), LISTED, { skipClaim: true }));
  });
});

describe('registration: identity is fixed afterwards', () => {
  beforeEach(async () => {
    await t.seed({ [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT) });
  });

  test.each([
    ['their roll number', { rollNo: '262019999' }],
    ['their roll number to a classmate\'s', { rollNo: OTHER_STUDENT.rollNo }],
    ['their cohort', { cohort: 'MC25' }],
    ['their email', { email: OTHER_STUDENT.email }],
    ['their role', { role: 'FACULTY' }],
  ])('denied: a registered student changes %s on their profile', async (_, change) => {
    await assertFails(updateDoc(profileRef(t.as(STUDENT), STUDENT), change));
  });

  test('denied: a registered student removes their roll number', async () => {
    const db = t.as(STUDENT);
    await assertFails(updateDoc(profileRef(db, STUDENT), { rollNo: deleteField() }));
    await assertFails(updateDoc(profileRef(db, STUDENT), { rollNo: deleteField(), cohort: deleteField() }));
  });

  test.each([
    ['the roll number', { rollNo: '262019999' }],
    ['the cohort', { cohort: 'MC25' }],
    ['the claim, to release it', { registeredUid: deleteField(), registeredAt: deleteField() }],
    ['the claim, to hand it to another sign-in', { registeredUid: 'someoneElse' }],
    ['the claim time', { registeredAt: serverTimestamp() }],
    ['the revision', { revision: 9 }],
  ])('denied: a registered student changes %s on their roster entry', async (_, change) => {
    await assertFails(updateDoc(entryRef(t.as(STUDENT), STUDENT), change));
  });

  test('after an audited correction the student must register again with the new number', async () => {
    const corrected = '262010077';
    await flows.correct(admin(), TEACHER, STUDENT.email, 'UPDATE', 'Mistyped', { rollNo: corrected });

    const db = t.as(STUDENT);
    // The stored profile now disagrees with the roster, so it cannot be rewritten as it is.
    await assertFails(updateDoc(profileRef(db, STUDENT), { name: STUDENT.name }));
    expect(await flows.register(db, STUDENT, STUDENT.rollNo)).toBe('ROLL_MISMATCH');
    expect(await assertSucceeds(flows.register(db, STUDENT, corrected))).toBe('REGISTERED');
    expect((await getDoc(profileRef(db, STUDENT))).data().rollNo).toBe(corrected);
  });

  test('after a release, a new sign-in for the same address can register', async () => {
    await flows.correct(admin(), TEACHER, STUDENT.email, 'RELEASE', 'New Google account');
    const recreated = { ...STUDENT, uid: 'studentAnew' };
    const outcome = flows.register(t.as(recreated), recreated, STUDENT.rollNo);
    expect(await assertSucceeds(outcome)).toBe('REGISTERED');
    expect((await entryOf(STUDENT)).registeredUid).toBe('studentAnew');
  });
});

describe('registration: races', () => {
  const settled = (results, value) =>
    results.filter((r) => r.status === 'fulfilled' && r.value === value).length;

  test('two sign-ins for one address register at once: exactly one is bound', async () => {
    const twin = { ...LISTED, uid: 'studentDtwin' };
    const results = await Promise.allSettled([
      flows.register(t.as(LISTED), LISTED, LISTED.rollNo),
      flows.register(t.as(twin), twin, LISTED.rollNo),
    ]);
    expect(settled(results, 'REGISTERED')).toBe(1);
    expect(settled(results, 'DUPLICATE_CLAIM')).toBe(1);
    const owner = (await entryOf(LISTED)).registeredUid;
    expect([LISTED.uid, twin.uid]).toContain(owner);
    const loser = owner === LISTED.uid ? twin : LISTED;
    expect((await getDoc(profileRef(t.as(loser), loser))).exists()).toBe(false);
  });

  test('two unchecked batches for one address: the rules let exactly one through', async () => {
    const twin = { ...LISTED, uid: 'studentDtwin' };
    const results = await Promise.allSettled([
      rawRegister(t.as(LISTED), LISTED),
      rawRegister(t.as(twin), twin),
    ]);
    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
  });

  test('the same student submits twice at once: both finish, one claim, one profile', async () => {
    const db = t.as(LISTED);
    const results = await Promise.allSettled([
      flows.register(db, LISTED, LISTED.rollNo),
      flows.register(db, LISTED, LISTED.rollNo),
    ]);
    expect(settled(results, 'REGISTERED')).toBe(2);
    expect((await entryOf(LISTED)).registeredUid).toBe(LISTED.uid);
    expect((await getDoc(profileRef(db, LISTED))).data().rollNo).toBe(LISTED.rollNo);
  });

  test('a correction lands while the student registers: never a profile with the old number', async () => {
    const corrected = '262010088';
    await Promise.allSettled([
      flows.register(t.as(LISTED), LISTED, LISTED.rollNo),
      flows.correct(admin(), TEACHER, LISTED.email, 'UPDATE', 'Mistyped', { rollNo: corrected }),
    ]);
    const entry = await entryOf(LISTED);
    const stored = await getDoc(profileRef(t.as(LISTED), LISTED));
    // Whichever order they landed in, a claimed entry and its profile agree.
    if (entry.registeredUid) {
      expect(stored.data().rollNo).toBe(entry.rollNo);
    }
    expect([LISTED.rollNo, corrected]).toContain(entry.rollNo);
  });
});
