// Invariant 7: manual correction by the session's teacher only. Create
// MANUAL_PRESENT for a member, or change an existing record's status with a
// required note, stamped with who and when. Nothing else can change.

const { deleteDoc, doc, serverTimestamp } = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, OUTSIDER, DROP } = h;
const { MINUTE, assertFails, assertSucceeds } = h;

// STUDENT already has a LIVE record; OTHER_STUDENT has none.
const t = h.setup(() => ({
  [`attendance/${h.attendanceId(STUDENT)}`]: h.storedMark(STUDENT),
}));

const ref = (db, student) => doc(db, 'attendance', h.attendanceId(student));
const correction = (overrides = {}) => ({
  status: 'MANUAL_ABSENT',
  note: 'Left before the roll call',
  updatedBy: TEACHER.uid,
  updatedAt: serverTimestamp(),
  revision: 1,
  ...overrides,
});

// Each change is written with its history entry, as the app does. The entry always describes
// the record as written, so a denial here comes from the record's own rule.
const create = (db, student, mark, teacher = TEACHER) =>
  h.teacherWrite(db, student, mark, { from: 'NONE', revision: 1, create: true, teacher });
const update = (db, student, change, teacher = TEACHER, from = 'PRESENT') =>
  h.teacherWrite(db, student, change, { from, revision: change.revision ?? 1, teacher });

describe('INV-7 manual present (create)', () => {
  test('the teacher marks an enrolled student present with a note', async () => {
    await assertSucceeds(create(t.as(TEACHER), OTHER_STUDENT, h.manualMark(OTHER_STUDENT)));
  });

  test('a 140-character note is accepted', async () => {
    const mark = h.manualMark(OTHER_STUDENT, { note: 'n'.repeat(140) });
    await assertSucceeds(create(t.as(TEACHER), OTHER_STUDENT, mark));
  });

  test.each([
    ['the status is MANUAL_ABSENT', { status: 'MANUAL_ABSENT' }],
    ['the status is PRESENT', { status: 'PRESENT' }],
    ['the source is LIVE', { source: 'LIVE' }],
    ['the note is missing', { note: DROP }],
    ['the note is empty', { note: '' }],
    ['the note is 141 characters', { note: 'n'.repeat(141) }],
    ['the note is not a string', { note: 7 }],
    ['updatedBy is someone else', { updatedBy: OTHER_TEACHER.uid }],
    ['updatedBy is missing', { updatedBy: DROP }],
    ['updatedAt is a client-chosen time', { updatedAt: h.ago(MINUTE) }],
    ['markedAt is a client-chosen time', { markedAt: h.ago(MINUTE) }],
    ['teacherId is someone else', { teacherId: OTHER_TEACHER.uid }],
    ['courseId is not the session course', { courseId: 'courseB' }],
    ['the session does not exist', { sessionId: 'ghost' }],
    ['a token is attached', { token: h.TOKEN }],
    ['coordinates are attached', { lat: 19.076, lng: 72.8777, accuracyM: 5 }],
  ])('denied when %s', async (_, change) => {
    const mark = h.patch(h.manualMark(OTHER_STUDENT), change);
    await assertFails(create(t.as(TEACHER), OTHER_STUDENT, mark));
  });

  test('denied: marking a student who is not enrolled', async () => {
    await assertFails(create(t.as(TEACHER), OUTSIDER, h.manualMark(OUTSIDER)));
  });

  test('denied: a document id for a different student than the data', async () => {
    await assertFails(create(t.as(TEACHER), OUTSIDER, h.manualMark(OTHER_STUDENT)));
  });

  test('denied: another teacher marks a student in this session', async () => {
    const mark = h.manualMark(OTHER_STUDENT, {
      teacherId: OTHER_TEACHER.uid,
      updatedBy: OTHER_TEACHER.uid,
    });
    await assertFails(create(t.as(OTHER_TEACHER), OTHER_STUDENT, mark, OTHER_TEACHER));
    await assertFails(
      create(t.as(OTHER_TEACHER), OTHER_STUDENT, h.manualMark(OTHER_STUDENT, OTHER_TEACHER)),
    );
  });

  test('denied: a student marks themselves present manually', async () => {
    const mark = h.manualMark(OTHER_STUDENT, { updatedBy: OTHER_STUDENT.uid });
    await assertFails(create(t.as(OTHER_STUDENT), OTHER_STUDENT, mark, OTHER_STUDENT));
  });
});

describe('INV-7 manual correction (update)', () => {
  test('the teacher marks a present student absent with a note', async () => {
    await assertSucceeds(update(t.as(TEACHER), STUDENT, correction()));
  });

  test('the teacher can correct the same record again', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(update(db, STUDENT, correction()));
    const back = correction({
      status: 'MANUAL_PRESENT',
      note: 'Returned, confirmed by TA',
      revision: 2,
    });
    await assertSucceeds(update(db, STUDENT, back, TEACHER, 'MANUAL_ABSENT'));
  });

  test('the teacher corrects a late-synced record', async () => {
    const late = h.storedMark(STUDENT, {
      status: 'LATE_SYNC',
      source: 'QUEUED',
      scannedAt: h.ago(6 * MINUTE),
    });
    await t.seed({ [`attendance/${h.attendanceId(STUDENT)}`]: late });
    await assertSucceeds(update(t.as(TEACHER), STUDENT, correction(), TEACHER, 'LATE_SYNC'));
  });

  test.each([
    ['the note is missing', { note: DROP }],
    ['the note is empty', { note: '' }],
    ['the note is 141 characters', { note: 'n'.repeat(141) }],
    ['the status is PRESENT', { status: 'PRESENT' }],
    ['the status is LATE_SYNC', { status: 'LATE_SYNC' }],
    ['updatedBy is someone else', { updatedBy: OTHER_TEACHER.uid }],
    ['updatedBy is missing', { updatedBy: DROP }],
    ['updatedAt is a client-chosen time', { updatedAt: h.ago(MINUTE) }],
    ['the coordinates are changed', { lat: 0 }],
    ['the source is changed', { source: 'MANUAL' }],
    ['the student is changed', { studentId: OTHER_STUDENT.uid }],
    ['markedAt is changed', { markedAt: serverTimestamp() }],
    ['the token is changed', { token: 'AAAAAAAAAAAAAAAAAAAAAA' }],
    ['an unknown field is added', { verified: true }],
  ])('denied when %s', async (_, change) => {
    await assertFails(update(t.as(TEACHER), STUDENT, h.patch(correction(), change)));
  });

  test.each([
    ['another teacher', OTHER_TEACHER],
    ['the student themselves', STUDENT],
    ['a classmate', OTHER_STUDENT],
  ])('denied: %s corrects the record', async (_, user) => {
    const change = correction({ status: 'MANUAL_PRESENT', updatedBy: user.uid });
    await assertFails(update(t.as(user), STUDENT, change, user));
  });

  test('denied: anyone deleting an attendance record', async () => {
    await assertFails(deleteDoc(ref(t.as(TEACHER), STUDENT)));
    await assertFails(deleteDoc(ref(t.as(STUDENT), STUDENT)));
  });
});
