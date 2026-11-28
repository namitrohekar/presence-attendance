// One phone, one student per session. A mark names the phone it came from and
// must be written together with a claim on that phone; the claim cannot exist
// without the mark, and a claimed phone cannot be claimed again.

const { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc, writeBatch } =
  require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { STUDENT, OTHER_STUDENT, SESSION, TOKEN, SECOND, assertFails, assertSucceeds } = h;

const OTHER_SESSION = 'sessB';
const OTHER_TOKEN = 'tokenOther_000000-0002';
const t = h.setup(() => ({
  [`sessions/${OTHER_SESSION}`]: h.sessionDoc(),
  [`sessions/${OTHER_SESSION}/tokens/${OTHER_TOKEN}`]: { validFrom: h.ago(2 * SECOND) },
}));

const PHONE = h.deviceOf(STUDENT);
const markRef = (db, student = STUDENT, session = SESSION) =>
  doc(db, 'attendance', h.attendanceId(student, session));
const claimRef = (db, phone = PHONE, session = SESSION) =>
  doc(db, 'sessions', session, 'devices', phone);
const session = { id: SESSION, ...h.sessionDoc() };

/** A mark and a claim written together, each exactly as given. */
function together(db, mark, phone, claim) {
  const batch = writeBatch(db);
  batch.set(markRef(db), mark);
  batch.set(claimRef(db, phone), claim);
  return batch.commit();
}

describe('phone claim: allowed', () => {
  test('a mark and its claim are written together, and the owner can read the claim', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(h.submit(markRef(db), h.liveMark()));
    const claim = await assertSucceeds(getDoc(claimRef(db)));
    expect(claim.data()).toEqual({ studentId: STUDENT.uid });
    expect((await getDoc(markRef(db))).data().deviceId).toBe(PHONE);
  });

  test('an unclaimed phone reads as "not found", which is how the app knows it is free', async () => {
    const claim = await assertSucceeds(getDoc(claimRef(t.as(OTHER_STUDENT))));
    expect(claim.exists()).toBe(false);
  });

  test('a phone two accounts are bound to may mark for each in different sessions', async () => {
    await t.seed({
      [`users/${OTHER_STUDENT.uid}`]: h.studentProfileDoc(OTHER_STUDENT, { deviceId: PHONE }),
    });
    await assertSucceeds(h.submit(markRef(t.as(STUDENT)), h.liveMark()));
    const other = t.as(OTHER_STUDENT);
    const mark = h.liveMark(OTHER_STUDENT, {
      sessionId: OTHER_SESSION,
      token: OTHER_TOKEN,
      deviceId: PHONE,
    });
    await assertSucceeds(h.submit(markRef(other, OTHER_STUDENT, OTHER_SESSION), mark));
  });

  test('a saved scan sent later claims the phone in the same way', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(h.submit(markRef(db), h.queuedMark(h.ago(SECOND))));
    expect((await getDoc(claimRef(db))).exists()).toBe(true);
  });
});

describe('phone claim: refused', () => {
  test('a mark without its claim, live or queued', async () => {
    const db = t.as(STUDENT);
    await assertFails(setDoc(markRef(db), h.liveMark()));
    await assertFails(setDoc(markRef(db), h.queuedMark(h.ago(SECOND))));
  });

  test('a claim without its mark', async () => {
    await assertFails(setDoc(claimRef(t.as(STUDENT)), { studentId: STUDENT.uid }));
  });

  test('a second student on a phone that already marked this session', async () => {
    await assertSucceeds(h.submit(markRef(t.as(STUDENT)), h.liveMark()));
    const other = t.as(OTHER_STUDENT);
    const mark = h.liveMark(OTHER_STUDENT, { deviceId: PHONE });
    await assertFails(h.submit(markRef(other, OTHER_STUDENT), mark));
    // Nothing of the refused write remains, and the first record is untouched.
    expect((await getDoc(markRef(other, OTHER_STUDENT))).exists()).toBe(false);
    expect((await getDoc(claimRef(t.as(STUDENT)))).data().studentId).toBe(STUDENT.uid);
  });

  test('a mark that names one phone while claiming another', async () => {
    const db = t.as(STUDENT);
    const elsewhere = h.deviceOf(OTHER_STUDENT);
    await assertFails(together(db, h.liveMark(), elsewhere, { studentId: STUDENT.uid }));
  });

  test('a claim made in another student\'s name', async () => {
    const db = t.as(STUDENT);
    await assertFails(together(db, h.liveMark(), PHONE, { studentId: OTHER_STUDENT.uid }));
    // Even where a record exists with no claim (never the case for a record the rules let in).
    await t.seed({ [`attendance/${SESSION}_${STUDENT.uid}`]: h.storedMark() });
    await assertFails(setDoc(claimRef(db), { studentId: OTHER_STUDENT.uid }));
  });

  test('a claim with extra fields, or on a malformed phone id', async () => {
    const db = t.as(STUDENT);
    await assertFails(
      together(db, h.liveMark(), PHONE, { studentId: STUDENT.uid, note: 'mine' }),
    );
    const short = 'abc';
    // Even an account somehow bound to it: the claim's own id check refuses it.
    await t.seed({ [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT, { deviceId: short }) });
    await assertFails(
      together(db, h.liveMark(STUDENT, { deviceId: short }), short, { studentId: STUDENT.uid }),
    );
    await assertFails(setDoc(markRef(db), h.patch(h.liveMark(), { deviceId: h.DROP })));
    await assertFails(setDoc(markRef(db), h.liveMark(STUDENT, { deviceId: 42 })));
  });

  test('changing, deleting or listing claims, or reading one signed out', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(h.submit(markRef(db), h.liveMark()));
    await assertFails(updateDoc(claimRef(db), { studentId: OTHER_STUDENT.uid }));
    await assertFails(setDoc(claimRef(t.as(OTHER_STUDENT)), { studentId: OTHER_STUDENT.uid }));
    await assertFails(deleteDoc(claimRef(db)));
    await assertFails(getDocs(collection(db, 'sessions', SESSION, 'devices')));
    await assertFails(getDocs(collection(t.as(h.TEACHER), 'sessions', SESSION, 'devices')));
    await assertFails(getDoc(claimRef(t.anon())));
  });
});

describe('phone claim: the app flow', () => {
  test('the second student is told the phone was used, and nothing is written', async () => {
    expect(await flows.markLive(t.as(STUDENT), STUDENT, session, TOKEN, h.INSIDE)).toBe('MARKED');
    const other = t.as(OTHER_STUDENT);
    expect(
      await assertSucceeds(flows.markLive(other, OTHER_STUDENT, session, TOKEN, h.INSIDE, PHONE)),
    ).toBe('DEVICE_USED');
    expect((await getDoc(markRef(other, OTHER_STUDENT))).exists()).toBe(false);
    // From their own phone the same student is accepted.
    expect(await flows.markLive(other, OTHER_STUDENT, session, TOKEN, h.INSIDE)).toBe('MARKED');
  });

  test('scanning again from the same phone reports "already marked", not "phone used"', async () => {
    const db = t.as(STUDENT);
    await flows.markLive(db, STUDENT, session, TOKEN, h.INSIDE);
    expect(await flows.markLive(db, STUDENT, session, TOKEN, h.INSIDE)).toBe('ALREADY_MARKED');
  });

  test('two students submit from one phone at the same instant: one record', async () => {
    const results = await Promise.all(
      [STUDENT, OTHER_STUDENT].map((s) =>
        flows.markLive(t.as(s), s, session, TOKEN, h.INSIDE, PHONE).then(
          (value) => value,
          () => 'REFUSED',
        ),
      ),
    );
    expect(results.filter((r) => r === 'MARKED')).toHaveLength(1);
    expect(['DEVICE_USED', 'REFUSED']).toContain(results.find((r) => r !== 'MARKED'));
    const records = await Promise.all(
      [STUDENT, OTHER_STUDENT].map((s) => getDoc(markRef(t.as(s), s))),
    );
    expect(records.filter((r) => r.exists())).toHaveLength(1);
  });
});
