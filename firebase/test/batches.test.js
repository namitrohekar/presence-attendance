// Batches: the HOD creates each batch (MCA 2025-27 is MCA25) in its first
// semester and later moves the semester, the intake, the number of lab groups
// or the programme's length. Everyone signed in reads them; nobody deletes one.

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

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, UNLISTED, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const MCA = { programme: 'MCA', startYear: 2025, years: 2, intake: 70, labGroups: 4 };
const ref = (db, id = 'MCA25') => doc(db, 'batches', id);
const newBatch = (overrides = {}) => ({
  programme: 'MCA',
  startYear: 2025,
  years: 2,
  semester: 1,
  intake: 70,
  labGroups: 4,
  createdBy: TEACHER.uid,
  createdAt: serverTimestamp(),
  ...overrides,
});

describe('batches: creating one', () => {
  test('the HOD creates MCA 2025-27 as MCA25, in semester 1', async () => {
    const db = t.as(TEACHER);
    expect(await assertSucceeds(flows.createBatch(db, TEACHER, MCA))).toBe('CREATED');
    expect((await getDoc(ref(db))).data()).toMatchObject({ semester: 1, intake: 70, labGroups: 4 });
  });

  test('the flow never overwrites a batch that exists', async () => {
    const db = t.as(TEACHER);
    await flows.createBatch(db, TEACHER, MCA);
    expect(await flows.createBatch(db, TEACHER, { ...MCA, intake: 60 })).toBe('EXISTS');
    expect((await getDoc(ref(db))).data().intake).toBe(70);
  });

  test('a second HOD may create one', async () => {
    await assertSucceeds(setDoc(ref(t.as(DEPUTY)), newBatch({ createdBy: DEPUTY.uid })));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER],
    ['a student', STUDENT],
    ['a VJTI student not on the roster', UNLISTED],
  ])('denied: %s creates a batch', async (_, user) => {
    await assertFails(setDoc(ref(t.as(user)), newBatch({ createdBy: user.uid })));
  });

  test.each([
    ['an id that is not the programme and year', 'MCA2025', {}],
    ['an id for another year', 'MCA26', {}],
    ['an id for another programme', 'MBA25', {}],
  ])('denied: %s', async (_, id, change) => {
    await assertFails(setDoc(ref(t.as(TEACHER), id), newBatch(change)));
  });

  test.each([
    ['a lower-case programme', { programme: 'mca' }],
    ['a one-letter programme', { programme: 'M' }],
    ['a seven-letter programme', { programme: 'MCAPLUS' }],
    ['a start year before 2010', { startYear: 2009 }],
    ['a start year after 2099', { startYear: 2100 }],
    ['a programme of 0 years', { years: 0 }],
    ['a programme of 6 years', { years: 6 }],
    ['a first semester other than 1', { semester: 2 }],
    ['an intake of 0', { intake: 0 }],
    ['an intake above the maximum', { intake: 501 }],
    ['no lab groups', { labGroups: 0 }],
    ['11 lab groups', { labGroups: 11 }],
    ['an intake stored as text', { intake: '70' }],
    ['someone else as the creator', { createdBy: OTHER_TEACHER.uid }],
    ['a client-chosen time', { createdAt: h.ago(DAY) }],
    ['a missing field', { labGroups: DROP }],
    ['an extra field', { name: 'MCA 2025-27' }],
  ])('denied: a batch with %s', async (_, change) => {
    const data = h.patch(newBatch(), change);
    const id =
      typeof data.programme === 'string' && Number.isInteger(data.startYear)
        ? flows.batchId(data.programme, data.startYear)
        : 'MCA25';
    await assertFails(setDoc(ref(t.as(TEACHER), id), data));
  });
});

describe('batches: changing one', () => {
  test('the HOD moves the semester on, and back if it was a mistake', async () => {
    const db = t.as(TEACHER);
    expect(await assertSucceeds(flows.updateBatch(db, 'MC26', { semester: 2 }))).toBe('DONE');
    await assertSucceeds(flows.updateBatch(db, 'MC26', { semester: 1 }));
  });

  test('the HOD changes the intake, the lab groups and the length together', async () => {
    const change = { intake: 68, labGroups: 3, years: 3, semester: 6 };
    await assertSucceeds(flows.updateBatch(t.as(TEACHER), 'MC26', change));
  });

  test('the flow reports a batch that does not exist', async () => {
    expect(await flows.updateBatch(t.as(TEACHER), 'MCA40', { semester: 2 })).toBe('NOT_FOUND');
  });

  test.each([
    ['semester 0', { semester: 0 }],
    ['semester 5 of a two-year programme', { semester: 5 }],
    ['a shorter programme than the semester it is in', { years: 1, semester: 3 }],
    ['an intake of 0', { intake: 0 }],
    ['12 lab groups', { labGroups: 12 }],
    ['the programme', { programme: 'MBA' }],
    ['the start year', { startYear: 2027 }],
    ['who created it', { createdBy: OTHER_TEACHER.uid }],
    ['when it was created', { createdAt: serverTimestamp() }],
    ['an extra field', { name: 'MCA' }],
  ])('denied: changing %s', async (_, change) => {
    await assertFails(updateDoc(ref(t.as(TEACHER), 'MC26'), change));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('denied: %s moves the semester', async (_, user) => {
    await assertFails(updateDoc(ref(t.as(user), 'MC26'), { semester: 2 }));
  });

  test('denied: anyone deletes a batch, even the HOD', async () => {
    await assertFails(deleteDoc(ref(t.as(TEACHER), 'MC26')));
  });
});

describe('batches: who reads them', () => {
  test.each([
    ['the HOD', TEACHER],
    ['a teacher', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('%s reads and lists the batches', async (_, user) => {
    const db = t.as(user);
    await assertSucceeds(getDoc(ref(db, 'MC26')));
    expect((await assertSucceeds(getDocs(collection(db, 'batches')))).size).toBe(2);
  });

  test('denied: someone signed out', async () => {
    await assertFails(getDoc(ref(t.anon(), 'MC26')));
  });
});
