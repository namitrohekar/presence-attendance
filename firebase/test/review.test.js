// After a session: the teacher records a headcount, revokes a scan found to be
// a proxy (a penalty the student can see), restores it if that was a mistake,
// and every change to a record keeps an entry in attendanceChanges.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, SESSION, TOKEN, MINUTE, SECOND } = h;
const { assertFails, assertSucceeds } = h;

// STUDENT scanned (LIVE); OTHER_STUDENT has no record yet.
const t = h.setup(() => ({
  [`attendance/${h.attendanceId(STUDENT)}`]: h.storedMark(STUDENT),
}));

const recordRef = (db, student = STUDENT) => doc(db, 'attendance', h.attendanceId(student));
const changeRef = (db, revision, student = STUDENT) =>
  doc(db, 'attendanceChanges', `${h.attendanceId(student)}_${revision}`);
const change = (status, revision, note = 'Friend scanned for them, seen on camera') => ({
  status,
  note,
  updatedBy: TEACHER.uid,
  updatedAt: serverTimestamp(),
  revision,
});
const revoke = (db = t.as(TEACHER), options = {}) =>
  h.teacherWrite(db, STUDENT, change('REVOKED', 1), { from: 'PRESENT', revision: 1, ...options });

describe('revoke a proxy', () => {
  test('the teacher revokes a scanned mark; the student sees it and the reason', async () => {
    await assertSucceeds(revoke());
    const seen = (await assertSucceeds(getDoc(recordRef(t.as(STUDENT))))).data();
    expect(seen.status).toBe('REVOKED');
    expect(seen.note).toBe('Friend scanned for them, seen on camera');
    expect(seen.source).toBe('LIVE');
  });

  test('a mark sent late from a saved scan can be revoked too', async () => {
    await t.seed({
      [`attendance/${h.attendanceId(STUDENT)}`]: h.storedMark(STUDENT, {
        status: 'LATE_SYNC',
        source: 'QUEUED',
        scannedAt: h.ago(6 * MINUTE),
      }),
    });
    await assertSucceeds(revoke(t.as(TEACHER), { from: 'LATE_SYNC' }));
  });

  test('restore turns it back to present, as the second change', async () => {
    const db = t.as(TEACHER);
    await revoke(db);
    const back = change('MANUAL_PRESENT', 2, 'Checked the camera again, it was them');
    await assertSucceeds(h.teacherWrite(db, STUDENT, back, { from: 'REVOKED', revision: 2 }));
    expect((await getDoc(recordRef(db))).data().status).toBe('MANUAL_PRESENT');
  });

  test('a revoked student cannot mark that session again', async () => {
    await revoke();
    const session = { id: SESSION, ...h.sessionDoc() };
    expect(await flows.markLive(t.as(STUDENT), STUDENT, session, TOKEN, h.INSIDE)).toBe(
      'ALREADY_MARKED',
    );
    expect((await getDoc(recordRef(t.as(STUDENT)))).data().status).toBe('REVOKED');
  });

  test('denied: revoking a record the teacher made by hand', async () => {
    const db = t.as(TEACHER);
    const manual = h.manualMark(OTHER_STUDENT);
    await h.teacherWrite(db, OTHER_STUDENT, manual, { from: 'NONE', revision: 1, create: true });
    const revoked = change('REVOKED', 2);
    await assertFails(
      h.teacherWrite(db, OTHER_STUDENT, revoked, { from: 'MANUAL_PRESENT', revision: 2 }),
    );
  });

  test('denied: revoking where there is no record, by another teacher, or by a student', async () => {
    const created = h.manualMark(OTHER_STUDENT, { status: 'REVOKED' });
    await assertFails(
      h.teacherWrite(t.as(TEACHER), OTHER_STUDENT, created, {
        from: 'NONE',
        revision: 1,
        create: true,
      }),
    );
    const theirs = { ...change('REVOKED', 1), updatedBy: OTHER_TEACHER.uid };
    await assertFails(
      h.teacherWrite(t.as(OTHER_TEACHER), STUDENT, theirs, {
        from: 'PRESENT',
        revision: 1,
        teacher: OTHER_TEACHER,
      }),
    );
    const self = { ...change('MANUAL_PRESENT', 1), updatedBy: STUDENT.uid };
    await assertFails(
      h.teacherWrite(t.as(STUDENT), STUDENT, self, { from: 'PRESENT', revision: 1, teacher: STUDENT }),
    );
  });

  test('the app flow: revoke, then restore, each recorded', async () => {
    const db = t.as(TEACHER);
    const correct = (status, note) =>
      flows.correctAttendance(db, TEACHER, h.COURSE, SESSION, STUDENT.uid, true, status, note);
    expect(await assertSucceeds(correct('REVOKED', 'Proxy, confirmed by headcount'))).toBe(true);
    expect(await assertSucceeds(correct('MANUAL_PRESENT', 'Mistake, it was them'))).toBe(true);
    const first = (await getDoc(changeRef(db, 1))).data();
    const second = (await getDoc(changeRef(db, 2))).data();
    expect([first.from, first.to]).toEqual(['PRESENT', 'REVOKED']);
    expect([second.from, second.to]).toEqual(['REVOKED', 'MANUAL_PRESENT']);
    expect(second.note).toBe('Mistake, it was them');
  });
});

describe('history of changes', () => {
  test('the teacher and the student read the history; a classmate and another teacher cannot', async () => {
    await revoke();
    await assertSucceeds(getDoc(changeRef(t.as(TEACHER), 1)));
    await assertSucceeds(getDoc(changeRef(t.as(STUDENT), 1)));
    const mine = query(collection(t.as(STUDENT), 'attendanceChanges'), where('studentId', '==', STUDENT.uid));
    expect((await assertSucceeds(getDocs(mine))).size).toBe(1);
    await assertFails(getDoc(changeRef(t.as(OTHER_STUDENT), 1)));
    await assertFails(getDoc(changeRef(t.as(OTHER_TEACHER), 1)));
    await assertFails(getDocs(collection(t.as(OTHER_STUDENT), 'attendanceChanges')));
  });

  test('denied: a change without its entry, or an entry without its change', async () => {
    const db = t.as(TEACHER);
    await assertFails(revoke(db, { history: null }));
    await assertFails(
      setDoc(changeRef(db, 1), h.historyEntry(STUDENT, 1, 'PRESENT', change('REVOKED', 1))),
    );
    // Nor for a record that has had changes: its current revision already has its entry,
    // and an entry for a later revision needs the record to reach it in the same write.
    await revoke(db);
    await assertFails(
      setDoc(changeRef(db, 2), h.historyEntry(STUDENT, 2, 'REVOKED', change('REVOKED', 2))),
    );
  });

  test('denied: a manual create without its entry', async () => {
    await assertFails(
      h.teacherWrite(t.as(TEACHER), OTHER_STUDENT, h.manualMark(OTHER_STUDENT), {
        from: 'NONE',
        revision: 1,
        create: true,
        history: null,
      }),
    );
  });

  test.each([
    ['the previous status is wrong', { from: 'MANUAL_ABSENT' }],
    ['a change to an existing record claims there was none', { from: 'NONE' }],
    ['the new status is wrong', { to: 'MANUAL_PRESENT' }],
    ['the note differs from the record', { note: 'Something else' }],
    ['the student is someone else', { studentId: OTHER_STUDENT.uid }],
    ['it names another author', { by: OTHER_TEACHER.uid }],
    ['it names another teacher', { teacherId: OTHER_TEACHER.uid }],
    ['the time is chosen by the client', { at: h.ago(MINUTE) }],
    ['the revision is a string', { revision: '1' }],
    ['an extra field is added', { approvedBy: 'principal' }],
  ])('denied when %s', async (_, wrong) => {
    await assertFails(revoke(t.as(TEACHER), { history: wrong }));
  });

  test('denied: the revision skips, repeats, or the entry has the wrong id', async () => {
    const db = t.as(TEACHER);
    await assertFails(h.teacherWrite(db, STUDENT, change('REVOKED', 2), { from: 'PRESENT', revision: 2 }));
    await assertFails(h.teacherWrite(db, STUDENT, change('REVOKED', 0), { from: 'PRESENT', revision: 0 }));
    // The record says revision 1, the entry is filed as 2.
    const wrongId = h.teacherWrite(db, STUDENT, change('REVOKED', 1), {
      from: 'PRESENT',
      revision: 2,
      history: { revision: 1 },
    });
    await assertFails(wrongId);
  });

  test('denied: a correct change that also files a copy of its entry under another id', async () => {
    const db = t.as(TEACHER);
    const revoked = change('REVOKED', 1);
    const batch = writeBatch(db);
    batch.update(recordRef(db), revoked);
    batch.set(changeRef(db, 1), h.historyEntry(STUDENT, 1, 'PRESENT', revoked));
    // The same description, filed where it could pass for another record's history.
    batch.set(
      doc(db, 'attendanceChanges', `${h.attendanceId(OTHER_STUDENT)}_1`),
      h.historyEntry(STUDENT, 1, 'PRESENT', revoked),
    );
    await assertFails(batch.commit());
  });

  test('denied: changing or deleting an entry, even by its author', async () => {
    const db = t.as(TEACHER);
    await revoke(db);
    await assertFails(updateDoc(changeRef(db, 1), { note: 'Rewritten' }));
    await assertFails(deleteDoc(changeRef(db, 1)));
    await assertFails(deleteDoc(changeRef(t.as(STUDENT), 1)));
  });
});

describe('headcount', () => {
  const ended = () =>
    t.seed({ [`sessions/${SESSION}`]: h.sessionDoc({ status: 'ENDED', endsAt: h.ago(MINUTE) }) });
  const session = (db) => doc(db, 'sessions', SESSION);

  test('the teacher records a headcount after the session and can correct it', async () => {
    await ended();
    const db = t.as(TEACHER);
    expect(await assertSucceeds(flows.setHeadcount(db, SESSION, 56))).toBe(56);
    await assertSucceeds(flows.setHeadcount(db, SESSION, 57));
    expect((await getDoc(session(db))).data().headcount).toBe(57);
    await assertSucceeds(updateDoc(session(db), { headcount: 0 }));
    await assertSucceeds(updateDoc(session(db), { headcount: 1000 }));
  });

  test('a session past its end that nobody closed counts as over', async () => {
    await t.seed({ [`sessions/${SESSION}`]: h.sessionDoc({ endsAt: h.ago(SECOND) }) });
    await assertSucceeds(updateDoc(session(t.as(TEACHER)), { headcount: 40 }));
  });

  test('denied: while the session is still running', async () => {
    await assertFails(updateDoc(session(t.as(TEACHER)), { headcount: 40 }));
  });

  test.each([
    ['negative', -1],
    ['above the maximum', 1001],
    ['a fraction', 40.5],
    ['text', '40'],
  ])('denied: a headcount that is %s', async (_, value) => {
    await ended();
    await assertFails(updateDoc(session(t.as(TEACHER)), { headcount: value }));
  });

  test('denied: another teacher, a student, or another field changed with it', async () => {
    await ended();
    await assertFails(updateDoc(session(t.as(OTHER_TEACHER)), { headcount: 40 }));
    await assertFails(updateDoc(session(t.as(STUDENT)), { headcount: 40 }));
    await assertFails(updateDoc(session(t.as(TEACHER)), { headcount: 40, endsAt: h.fromNow(MINUTE) }));
    await assertFails(updateDoc(session(t.as(TEACHER)), { headcount: 40, status: 'ACTIVE' }));
  });
});
