// Student roster: studentRegistry/{email} is the authoritative email -> roll
// number map and rollIndex/{cohort}_{rollNo} keeps a roll number unique in its
// cohort. Only allowlisted faculty write them, always as a pair, and any
// change to an existing entry needs an immutable audit record in the same
// write. Students can read their own entry and nothing else.

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
  writeBatch,
} = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, LISTED, UNLISTED, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

// A student faculty are about to add.
const NEW = {
  uid: 'studentN',
  email: 'apawar_mc26@mc.vjti.ac.in',
  name: 'Aditi Pawar',
  rollNo: '262011005',
  cohort: 'MC26',
};

const entryRef = (db, who) => doc(db, 'studentRegistry', who.email);
const indexRef = (db, who) => doc(db, 'rollIndex', `${who.cohort}_${who.rollNo}`);
const newEntry = (overrides = {}) => ({
  email: NEW.email,
  rollNo: NEW.rollNo,
  cohort: NEW.cohort,
  name: NEW.name,
  emoji: 'LION',
  addedBy: TEACHER.uid,
  addedAt: serverTimestamp(),
  revision: 0,
  ...overrides,
});

/** The pair of writes with no client-side checks, so the rules decide. Pass null to omit one. */
function addPair(db, entry = newEntry(), index = { email: NEW.email }, indexId) {
  const batch = writeBatch(db);
  if (entry) {
    batch.set(doc(db, 'studentRegistry', entry.email ?? NEW.email), entry);
  }
  if (index) {
    const id = indexId ?? `${(entry ?? NEW).cohort}_${(entry ?? NEW).rollNo}`;
    batch.set(doc(db, 'rollIndex', id), index);
  }
  return batch.commit();
}

describe('roster: adding a student', () => {
  test('faculty add a student as an entry and its roll index together', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(addPair(db));
    expect((await getDoc(entryRef(db, NEW))).data().revision).toBe(0);
  });

  test('any allowlisted teacher can add to the shared roster', async () => {
    await assertSucceeds(addPair(t.as(OTHER_TEACHER), newEntry({ addedBy: OTHER_TEACHER.uid })));
  });

  test('the same roll number is allowed in a different cohort', async () => {
    const entry = newEntry({ rollNo: STUDENT.rollNo, cohort: 'MC25' });
    await assertSucceeds(addPair(t.as(TEACHER), entry));
  });

  test('denied: an entry without its index, or an index without its entry', async () => {
    await assertFails(addPair(t.as(TEACHER), newEntry(), null));
    await assertFails(addPair(t.as(TEACHER), null, { email: NEW.email }));
  });

  test('denied: a duplicate email (the address is already on the roster)', async () => {
    const entry = newEntry({ email: LISTED.email, rollNo: '262019001' });
    await assertFails(addPair(t.as(TEACHER), entry, { email: LISTED.email }));
  });

  test('denied: a duplicate roll number in the same cohort', async () => {
    const entry = newEntry({ rollNo: STUDENT.rollNo });
    await assertFails(addPair(t.as(TEACHER), entry));
  });

  test('denied: an index that points at a different address', async () => {
    await assertFails(addPair(t.as(TEACHER), newEntry(), { email: STUDENT.email }));
  });

  test('denied: an index under a key that is not the entry\'s cohort and roll number', async () => {
    await assertFails(addPair(t.as(TEACHER), newEntry(), { email: NEW.email }, 'MC26_262019999'));
  });

  test.each([
    ['a Gmail address', { email: 'apawar_mc26@gmail.com' }],
    ['a sub-domain of the student domain', { email: 'apawar_mc26@x.mc.vjti.ac.in' }],
    ['a look-alike domain', { email: 'apawar_mc26@mc.vjti.ac.in.example.com' }],
    ['an address with capital letters', { email: 'APawar_MC26@mc.vjti.ac.in' }],
    ['a roll number of 8 digits', { rollNo: '26201100' }],
    ['a roll number of 10 digits', { rollNo: '2620110050' }],
    ['a roll number with a letter', { rollNo: '26201100A' }],
    ['a roll number stored as a number', { rollNo: 262011005 }],
    ['a lower-case cohort', { cohort: 'mc26' }],
    ['a one-character cohort', { cohort: 'M' }],
    ['a nine-character cohort', { cohort: 'MCA2026XX' }],
    ['a cohort with punctuation', { cohort: 'MC-26' }],
    ['addedBy naming someone else', { addedBy: OTHER_TEACHER.uid }],
    ['a client-chosen addedAt', { addedAt: h.ago(DAY) }],
    ['a revision other than 0', { revision: 5 }],
    ['an extra field', { verified: true }],
    ['a missing field', { cohort: DROP }],
    ['a claim already filled in', { registeredUid: NEW.uid, registeredAt: serverTimestamp() }],
    ['no name', { name: DROP }],
    ['no emoji', { emoji: DROP }],
    ['an empty name', { name: '' }],
    ['a name of spaces only', { name: '   ' }],
    ['a name with a leading space', { name: ' Aditi Pawar' }],
    ['a name with a trailing space', { name: 'Aditi Pawar ' }],
    ['a name of 81 characters', { name: 'a'.repeat(81) }],
    ['a name stored as a number', { name: 42 }],
    ['batch 0', { batch: 0 }],
    ['batch 21', { batch: 21 }],
    ['a batch stored as text', { batch: '2' }],
    ['a fractional batch', { batch: 1.5 }],
    ['an emoji outside the ten', { emoji: 'CAT' }],
    ['a lower-case emoji name', { emoji: 'lion' }],
    ['the emoji character instead of its name', { emoji: '\u{1F981}' }],
  ])('denied: %s', async (_, change) => {
    const entry = h.patch(newEntry(), change);
    const index = { email: typeof entry.email === 'string' ? entry.email : NEW.email };
    await assertFails(addPair(t.as(TEACHER), entry, index));
  });

  test('denied: a document id that is not the entry\'s address', async () => {
    const db = t.as(TEACHER);
    const batch = writeBatch(db);
    batch.set(doc(db, 'studentRegistry', 'someone_mc26@mc.vjti.ac.in'), newEntry());
    batch.set(indexRef(db, NEW), { email: NEW.email });
    await assertFails(batch.commit());
  });

  test.each([
    ['a registered student', STUDENT],
    ['a VJTI student not on the roster', UNLISTED],
  ])('denied: %s adds a roster entry, even for themselves', async (_, user) => {
    const entry = newEntry({ email: user.email, addedBy: user.uid });
    await assertFails(addPair(t.as(user), entry, { email: user.email }));
  });

  test('race: two teachers add the same roll number at once and exactly one wins', async () => {
    const other = { ...NEW, uid: 'studentM', email: 'bkale_mc26@mc.vjti.ac.in' };
    const results = await Promise.allSettled([
      flows.addStudent(t.as(TEACHER), TEACHER, NEW),
      flows.addStudent(t.as(OTHER_TEACHER), OTHER_TEACHER, other),
    ]);
    const outcomes = results.map((r) => r.value).sort();
    expect(outcomes).toEqual(['ADDED', 'ROLL_TAKEN']);
    const index = await getDoc(indexRef(t.as(TEACHER), NEW));
    const owner = index.data().email;
    expect([NEW.email, other.email]).toContain(owner);
    // The loser left nothing behind.
    const loser = owner === NEW.email ? other : NEW;
    expect((await getDoc(entryRef(t.as(TEACHER), loser))).exists()).toBe(false);
  });
});

describe('roster: nothing is overwritten or removed without an audit record', () => {
  test('denied: faculty edit or replace an existing entry directly', async () => {
    const db = t.as(TEACHER);
    await assertFails(updateDoc(entryRef(db, LISTED), { rollNo: '262019001' }));
    await assertFails(updateDoc(entryRef(db, LISTED), { cohort: 'MC25' }));
    await assertFails(setDoc(entryRef(db, LISTED), h.registryDoc(LISTED, { addedAt: serverTimestamp() })));
  });

  test('denied: faculty delete an entry, its index, or both, directly', async () => {
    const db = t.as(TEACHER);
    await assertFails(deleteDoc(entryRef(db, LISTED)));
    await assertFails(deleteDoc(indexRef(db, LISTED)));
    const batch = writeBatch(db);
    batch.delete(entryRef(db, LISTED));
    batch.delete(indexRef(db, LISTED));
    await assertFails(batch.commit());
  });

  test('denied: rewriting an index to point at another address', async () => {
    const db = t.as(TEACHER);
    await assertFails(setDoc(indexRef(db, LISTED), { email: STUDENT.email }));
    await assertFails(updateDoc(indexRef(db, LISTED), { email: STUDENT.email }));
  });
});

describe('roster: audited corrections', () => {
  const REASON = 'Roll number mistyped in the department list';
  const NEW_ROLL = '262010044';
  const recordRef = (db, entry) => doc(db, 'registryCorrections', flows.correctionId(entry));
  const seeded = async (who) => (await getDoc(entryRef(t.as(TEACHER), who))).data();

  test('UPDATE of an unclaimed entry moves the index and writes the record', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(LISTED);
    const outcome = flows.correct(db, TEACHER, LISTED.email, 'UPDATE', REASON, { rollNo: NEW_ROLL });
    expect(await assertSucceeds(outcome)).toBe('DONE');

    const after = (await getDoc(entryRef(db, LISTED))).data();
    expect(after.rollNo).toBe(NEW_ROLL);
    expect(after.revision).toBe(1);
    expect((await getDoc(indexRef(db, LISTED))).exists()).toBe(false);
    expect((await getDoc(doc(db, 'rollIndex', `MC26_${NEW_ROLL}`))).data().email).toBe(LISTED.email);

    const record = (await getDoc(recordRef(db, before))).data();
    expect(record).toMatchObject({
      action: 'UPDATE',
      beforeRollNo: LISTED.rollNo,
      afterRollNo: NEW_ROLL,
      correctedBy: TEACHER.uid,
      reason: REASON,
    });
    expect(record.releasedUid).toBeUndefined();
  });

  test('UPDATE of a claimed entry releases the claim and records who held it', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(STUDENT);
    await assertSucceeds(flows.correct(db, TEACHER, STUDENT.email, 'UPDATE', REASON, { cohort: 'MC25' }));
    const after = (await getDoc(entryRef(db, STUDENT))).data();
    expect(after.cohort).toBe('MC25');
    expect(after.registeredUid).toBeUndefined();
    expect((await getDoc(recordRef(db, before))).data().releasedUid).toBe(STUDENT.uid);
  });

  test('RELEASE frees a claim without changing the roll number', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.correct(db, TEACHER, STUDENT.email, 'RELEASE', 'Student got a new Google account'));
    const after = (await getDoc(entryRef(db, STUDENT))).data();
    expect(after.registeredUid).toBeUndefined();
    expect(after.rollNo).toBe(STUDENT.rollNo);
    expect((await getDoc(indexRef(db, STUDENT))).exists()).toBe(true);
  });

  test('REMOVE deletes the entry and its index and keeps the record', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(STUDENT);
    await assertSucceeds(flows.correct(db, TEACHER, STUDENT.email, 'REMOVE', 'Left the programme'));
    expect((await getDoc(entryRef(db, STUDENT))).exists()).toBe(false);
    expect((await getDoc(indexRef(db, STUDENT))).exists()).toBe(false);
    const record = (await getDoc(recordRef(db, before))).data();
    expect(record).toMatchObject({ action: 'REMOVE', releasedUid: STUDENT.uid });
    expect(record.afterRollNo).toBeUndefined();
  });

  test('a second correction of the same entry gets its own record', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.correct(db, TEACHER, LISTED.email, 'UPDATE', REASON, { rollNo: NEW_ROLL }));
    const middle = await seeded(LISTED);
    await assertSucceeds(flows.correct(db, TEACHER, LISTED.email, 'UPDATE', 'Reverted', { rollNo: LISTED.rollNo }));
    expect((await getDoc(entryRef(db, LISTED))).data().revision).toBe(2);
    expect((await getDoc(recordRef(db, middle))).data().afterRollNo).toBe(LISTED.rollNo);
  });

  test('a removed student can be added again, and corrected again', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.correct(db, TEACHER, LISTED.email, 'REMOVE', 'Added by mistake'));
    expect(await assertSucceeds(flows.addStudent(db, TEACHER, LISTED))).toBe('ADDED');
    await assertSucceeds(flows.correct(db, TEACHER, LISTED.email, 'UPDATE', REASON, { rollNo: NEW_ROLL }));
  });

  test('the flow refuses a roll number that belongs to someone else', async () => {
    const outcome = flows.correct(t.as(TEACHER), TEACHER, LISTED.email, 'UPDATE', REASON, {
      rollNo: STUDENT.rollNo,
    });
    expect(await outcome).toBe('ROLL_TAKEN');
  });

  /**
   * An UPDATE of LISTED's roll number written without the flow's checks. Each
   * option breaks one part of the atomic change.
   */
  async function rawUpdate(db, options = {}) {
    const before = await seeded(LISTED);
    const after = { ...before, rollNo: NEW_ROLL, revision: before.revision + 1, ...options.entry };
    const record = h.patch(
      {
        email: LISTED.email,
        action: 'UPDATE',
        reason: REASON,
        correctedBy: TEACHER.uid,
        correctedAt: serverTimestamp(),
        beforeRollNo: before.rollNo,
        beforeCohort: before.cohort,
        afterRollNo: after.rollNo,
        afterCohort: after.cohort,
      },
      options.record ?? {},
    );
    const batch = writeBatch(db);
    if (!options.skipRecord) {
      batch.set(doc(db, 'registryCorrections', options.recordId ?? flows.correctionId(before)), record);
    }
    if (!options.skipEntry) {
      batch.set(entryRef(db, LISTED), after);
    }
    if (!options.keepOldIndex) {
      batch.delete(indexRef(db, LISTED));
    }
    if (!options.skipNewIndex) {
      batch.set(doc(db, 'rollIndex', `${after.cohort}_${after.rollNo}`), { email: LISTED.email });
    }
    return batch.commit();
  }

  test('the unchecked UPDATE passes when every part is present', async () => {
    await assertSucceeds(rawUpdate(t.as(TEACHER)));
  });

  test.each([
    ['there is no audit record', { skipRecord: true }],
    ['the audit record has an arbitrary id', { recordId: 'whatever' }],
    ['the audit record exists but the entry does not change', { skipEntry: true, keepOldIndex: true, skipNewIndex: true }],
    ['the old index is left behind', { keepOldIndex: true }],
    ['the new index is not written', { skipNewIndex: true }],
    ['the reason is empty', { record: { reason: '' } }],
    ['the reason is 141 characters', { record: { reason: 'r'.repeat(141) } }],
    ['the reason is missing', { record: { reason: DROP } }],
    ['the record names another teacher', { record: { correctedBy: OTHER_TEACHER.uid } }],
    ['the record has a client-chosen time', { record: { correctedAt: h.ago(DAY) } }],
    ['the record misstates the previous roll number', { record: { beforeRollNo: '262019999' } }],
    ['the record misstates the previous cohort', { record: { beforeCohort: 'MC25' } }],
    ['the record misstates the new roll number', { record: { afterRollNo: '262019999' } }],
    ['the record claims a released sign-in that did not exist', { record: { releasedUid: 'nobody' } }],
    ['the record has an unknown action', { record: { action: 'MERGE' } }],
    ['the record has an extra field', { record: { hidden: true } }],
    ['the entry skips a revision', { entry: { revision: 7 } }],
    ['the entry keeps its revision', { entry: { revision: 0 } }],
    ['the entry changes who added it', { entry: { addedBy: OTHER_TEACHER.uid } }],
    ['the entry changes when it was added', { entry: { addedAt: h.ago(DAY) } }],
    ['the new roll number is malformed', { entry: { rollNo: '12345' }, record: { afterRollNo: '12345' } }],
    ['the entry is given a claim', { entry: { registeredUid: 'someone', registeredAt: h.ago(DAY) } }],
    ['the entry is given batch 0', { entry: { batch: 0 } }],
    ['the entry is given an emoji outside the ten', { entry: { emoji: 'CAT' } }],
    ['the entry is given an empty name', { entry: { name: '' } }],
    ['the entry is given an unknown field', { entry: { nickname: 'KD' } }],
  ])('denied: an UPDATE where %s', async (_, options) => {
    await assertFails(rawUpdate(t.as(TEACHER), options));
  });

  test('denied: a second audit record under an arbitrary id riding along with a real correction', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(LISTED);
    const record = {
      email: LISTED.email,
      action: 'UPDATE',
      reason: REASON,
      correctedBy: TEACHER.uid,
      correctedAt: serverTimestamp(),
      beforeRollNo: before.rollNo,
      beforeCohort: before.cohort,
      afterRollNo: NEW_ROLL,
      afterCohort: before.cohort,
    };
    const batch = writeBatch(db);
    batch.set(recordRef(db, before), record);
    batch.set(doc(db, 'registryCorrections', 'fabricated'), record);
    batch.set(entryRef(db, LISTED), { ...before, rollNo: NEW_ROLL, revision: before.revision + 1 });
    batch.delete(indexRef(db, LISTED));
    batch.set(doc(db, 'rollIndex', `MC26_${NEW_ROLL}`), { email: LISTED.email });
    await assertFails(batch.commit());
  });

  test('denied: an audit record on its own, even one that describes no change', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(LISTED);
    await assertFails(
      setDoc(recordRef(db, before), {
        email: LISTED.email,
        action: 'UPDATE',
        reason: REASON,
        correctedBy: TEACHER.uid,
        correctedAt: serverTimestamp(),
        beforeRollNo: before.rollNo,
        beforeCohort: before.cohort,
        afterRollNo: before.rollNo,
        afterCohort: before.cohort,
      }),
    );
  });

  test('denied: an UPDATE that takes another student\'s roll number', async () => {
    const options = {
      entry: { rollNo: STUDENT.rollNo },
      record: { afterRollNo: STUDENT.rollNo },
      skipNewIndex: true,
    };
    await assertFails(rawUpdate(t.as(TEACHER), options));
  });

  test('denied: an UPDATE of a claimed entry that keeps the claim', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(STUDENT);
    const batch = writeBatch(db);
    batch.set(doc(db, 'registryCorrections', flows.correctionId(before)), {
      email: STUDENT.email,
      action: 'UPDATE',
      reason: REASON,
      correctedBy: TEACHER.uid,
      correctedAt: serverTimestamp(),
      beforeRollNo: before.rollNo,
      beforeCohort: before.cohort,
      releasedUid: STUDENT.uid,
      afterRollNo: NEW_ROLL,
      afterCohort: before.cohort,
    });
    batch.update(entryRef(db, STUDENT), { rollNo: NEW_ROLL, revision: 1 });
    batch.delete(indexRef(db, STUDENT));
    batch.set(doc(db, 'rollIndex', `MC26_${NEW_ROLL}`), { email: STUDENT.email });
    await assertFails(batch.commit());
  });

  test('denied: RELEASE of an entry that is not claimed, or one that changes the roll number', async () => {
    const db = t.as(TEACHER);
    await assertFails(flows.correct(db, TEACHER, LISTED.email, 'RELEASE', REASON));
    await assertFails(flows.correct(db, TEACHER, STUDENT.email, 'RELEASE', REASON, { rollNo: NEW_ROLL }));
  });

  test('denied: REMOVE that leaves the index, or a REMOVE record with nothing removed', async () => {
    const db = t.as(TEACHER);
    const before = await seeded(LISTED);
    const record = {
      email: LISTED.email,
      action: 'REMOVE',
      reason: REASON,
      correctedBy: TEACHER.uid,
      correctedAt: serverTimestamp(),
      beforeRollNo: before.rollNo,
      beforeCohort: before.cohort,
    };
    const leavesIndex = writeBatch(db);
    leavesIndex.set(recordRef(db, before), record);
    leavesIndex.delete(entryRef(db, LISTED));
    await assertFails(leavesIndex.commit());

    await assertFails(setDoc(recordRef(db, before), record));
  });

  test.each([
    ['a registered student', STUDENT],
    ['the student the entry belongs to', LISTED],
  ])('denied: %s corrects, releases or removes an entry', async (_, user) => {
    const db = t.as(user);
    const asUser = { uid: user.uid };
    // The flow's first read is the entry itself; a student can only read their own.
    await assertFails(flows.correct(db, asUser, OTHER_STUDENT.email, 'REMOVE', REASON));
    await assertFails(deleteDoc(entryRef(db, LISTED)));
    await assertFails(updateDoc(entryRef(db, LISTED), { rollNo: NEW_ROLL }));
  });
});

describe('roster: audit records are immutable and retained', () => {
  const OLD = 'registryCorrections/old';
  const RECENT = 'registryCorrections/recent';
  const record = (ageDays) => ({
    email: LISTED.email,
    action: 'UPDATE',
    reason: 'Earlier fix',
    correctedBy: TEACHER.uid,
    correctedAt: h.ago(ageDays * DAY),
    beforeRollNo: '262010001',
    beforeCohort: 'MC26',
    afterRollNo: LISTED.rollNo,
    afterCohort: 'MC26',
  });

  beforeEach(async () => {
    await t.seed({ [OLD]: record(366), [RECENT]: record(364) });
  });

  test('faculty read and list the audit trail', async () => {
    const db = t.as(OTHER_TEACHER);
    await assertSucceeds(getDoc(doc(db, RECENT)));
    const all = await assertSucceeds(getDocs(collection(db, 'registryCorrections')));
    expect(all.size).toBe(2);
  });

  test('denied: editing a record, or deleting one inside the retention period', async () => {
    const db = t.as(TEACHER);
    await assertFails(updateDoc(doc(db, RECENT), { reason: 'Rewritten' }));
    await assertFails(deleteDoc(doc(db, RECENT)));
  });

  test('a record older than the retention period can be purged by faculty', async () => {
    await assertSucceeds(deleteDoc(doc(t.as(TEACHER), OLD)));
  });

  test('denied: students read, list or purge the audit trail', async () => {
    const db = t.as(STUDENT);
    await assertFails(getDoc(doc(db, OLD)));
    await assertFails(getDocs(collection(db, 'registryCorrections')));
    await assertFails(deleteDoc(doc(db, OLD)));
  });
});

describe('roster: who can read it', () => {
  test('faculty read one entry, list the whole roster, and check one roll number', async () => {
    const db = t.as(OTHER_TEACHER);
    await assertSucceeds(getDoc(entryRef(db, STUDENT)));
    const roster = await assertSucceeds(getDocs(collection(db, 'studentRegistry')));
    expect(roster.size).toBe(4);
    const index = await assertSucceeds(getDoc(indexRef(db, STUDENT)));
    expect(index.data().email).toBe(STUDENT.email);
    const free = await assertSucceeds(getDoc(doc(db, 'rollIndex', 'MC26_262019999')));
    expect(free.exists()).toBe(false);
  });

  test('a student reads their own entry, and gets "not found" when not on the roster', async () => {
    const own = await assertSucceeds(getDoc(entryRef(t.as(LISTED), LISTED)));
    expect(own.data().rollNo).toBe(LISTED.rollNo);
    const missing = await assertSucceeds(getDoc(entryRef(t.as(UNLISTED), UNLISTED)));
    expect(missing.exists()).toBe(false);
  });

  test('denied: a student reads a classmate\'s entry or lists the roster', async () => {
    const db = t.as(STUDENT);
    await assertFails(getDoc(entryRef(db, OTHER_STUDENT)));
    await assertFails(getDocs(collection(db, 'studentRegistry')));
  });

  test('denied: a student reads or lists the roll index, even their own number', async () => {
    const db = t.as(STUDENT);
    await assertFails(getDoc(indexRef(db, STUDENT)));
    await assertFails(getDoc(indexRef(db, OTHER_STUDENT)));
    await assertFails(getDocs(collection(db, 'rollIndex')));
  });

  test('denied: nobody lists the roll index, faculty included', async () => {
    await assertFails(getDocs(collection(t.as(TEACHER), 'rollIndex')));
  });
});

describe('roster: name, batch and emoji on a new entry', () => {
  test('a name of 80 characters, any script, batch 1 or 20', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(addPair(db, newEntry({ name: 'a'.repeat(80), batch: 1 })));
    const other = { email: 'bkale_mc26@mc.vjti.ac.in', rollNo: '262011006' };
    await assertSucceeds(
      addPair(db, newEntry({ ...other, name: '\u0906\u0926\u093f\u0924\u0940', batch: 20 }), {
        email: other.email,
      }),
    );
  });

  test.each(['LION', 'TIGER', 'ELEPHANT', 'PANDA', 'FOX', 'OWL', 'DOLPHIN', 'TURTLE', 'BUTTERFLY', 'PEACOCK'])(
    'the emoji %s is accepted',
    async (emoji) => {
      await assertSucceeds(addPair(t.as(TEACHER), newEntry({ emoji })));
    },
  );

  test('the add flow writes the details it is given', async () => {
    const db = t.as(TEACHER);
    const who = { ...NEW, emoji: 'FOX', batch: 3 };
    expect(await assertSucceeds(flows.addStudent(db, TEACHER, who))).toBe('ADDED');
    expect((await getDoc(entryRef(db, NEW))).data()).toMatchObject({
      name: NEW.name,
      emoji: 'FOX',
      batch: 3,
    });
  });
});

describe('roster: editing name, batch and emoji', () => {
  const CHANGE = { name: 'Meera S. Joshi', emoji: 'PEACOCK', batch: 2 };

  test('faculty edit the details of a registered student, who stays registered', async () => {
    const db = t.as(TEACHER);
    expect(await assertSucceeds(flows.updateDetails(db, STUDENT.email, CHANGE))).toBe('DONE');
    const after = (await getDoc(entryRef(db, STUDENT))).data();
    expect(after).toMatchObject({ ...CHANGE, registeredUid: STUDENT.uid, revision: 0 });
    // Nothing for the audit trail: identity did not change.
    expect((await getDocs(collection(db, 'registryCorrections'))).size).toBe(0);
  });

  test('a batch can be cleared again', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.updateDetails(db, STUDENT.email, CHANGE));
    await assertSucceeds(flows.updateDetails(db, STUDENT.email, { ...CHANGE, batch: null }));
    expect((await getDoc(entryRef(db, STUDENT))).data().batch).toBeUndefined();
  });

  test('any allowlisted teacher may edit details on the shared roster', async () => {
    await assertSucceeds(flows.updateDetails(t.as(OTHER_TEACHER), LISTED.email, CHANGE));
  });

  test('an entry added before names were kept gets its details', async () => {
    await t.seed({
      [`studentRegistry/${LISTED.email}`]: h.patch(h.registryDoc(LISTED), { name: DROP, emoji: DROP }),
    });
    await assertSucceeds(flows.updateDetails(t.as(TEACHER), LISTED.email, CHANGE));
  });

  test('an entry added before names were kept can still be corrected', async () => {
    await t.seed({
      [`studentRegistry/${LISTED.email}`]: h.patch(h.registryDoc(LISTED), { name: DROP, emoji: DROP }),
    });
    const outcome = flows.correct(t.as(TEACHER), TEACHER, LISTED.email, 'UPDATE', 'Typo', {
      rollNo: '262010044',
    });
    expect(await assertSucceeds(outcome)).toBe('DONE');
  });

  test('a correction keeps the details', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.updateDetails(db, STUDENT.email, CHANGE));
    await assertSucceeds(flows.correct(db, TEACHER, STUDENT.email, 'RELEASE', 'New Google account'));
    expect((await getDoc(entryRef(db, STUDENT))).data()).toMatchObject(CHANGE);
  });

  test('the flow reports an entry that is gone', async () => {
    expect(await flows.updateDetails(t.as(TEACHER), NEW.email, CHANGE)).toBe('NOT_FOUND');
  });

  test.each([
    ['the student themself', STUDENT],
    ['another student', OTHER_STUDENT],
    ['a VJTI student not on the roster', UNLISTED],
  ])('denied: %s edits a student\'s details', async (_, user) => {
    await assertFails(updateDoc(entryRef(t.as(user), STUDENT), { name: 'Someone Else' }));
  });

  test.each([
    ['an empty name', { name: '' }],
    ['a name of 81 characters', { name: 'a'.repeat(81) }],
    ['batch 21', { batch: 21 }],
    ['a batch stored as text', { batch: '1' }],
    ['an emoji outside the ten', { emoji: 'CAT' }],
    ['the name removed', { name: deleteField() }],
    ['the emoji removed', { emoji: deleteField() }],
    ['the roll number changed too', { name: 'Meera J', rollNo: '262019001' }],
    ['the cohort changed too', { name: 'Meera J', cohort: 'MC25' }],
    ['the revision changed too', { name: 'Meera J', revision: 1 }],
    ['who added it changed too', { name: 'Meera J', addedBy: OTHER_TEACHER.uid }],
    ['the registration cleared too', { name: 'Meera J', registeredUid: deleteField() }],
    ['an unknown field', { nickname: 'MJ' }],
  ])('denied: a details edit with %s', async (_, change) => {
    await assertFails(updateDoc(entryRef(t.as(TEACHER), STUDENT), change));
  });

  test('denied: a details edit leaves a legacy entry without a name', async () => {
    await t.seed({
      [`studentRegistry/${LISTED.email}`]: h.patch(h.registryDoc(LISTED), { name: DROP, emoji: DROP }),
    });
    await assertFails(updateDoc(entryRef(t.as(TEACHER), LISTED), { emoji: 'FOX' }));
  });
});
