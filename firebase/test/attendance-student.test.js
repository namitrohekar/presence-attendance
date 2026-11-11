// Invariant 6: a student's attendance mark. Exact field set, own document
// only, server-true course and teacher, membership required, then either
// LIVE (fresh token, session running) or QUEUED (scanned inside the token
// window, synced within the grace period). One record per student per session.

const {
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OTHER_STUDENT, OUTSIDER, SESSION, TOKEN, DROP } = h;
const { MINUTE, SECOND, assertFails, assertSucceeds } = h;

const OTHER_SESSION = 'sessB';
const OTHER_TOKEN = 'tokenOther_000000-0002';
const t = h.setup(() => ({
  [`sessions/${OTHER_SESSION}`]: h.sessionDoc(),
  [`sessions/${OTHER_SESSION}/tokens/${OTHER_TOKEN}`]: { validFrom: h.ago(2 * SECOND) },
}));

const ref = (db, student = STUDENT, session = SESSION) =>
  doc(db, 'attendance', h.attendanceId(student, session));
const tokenPath = (token = TOKEN, session = SESSION) => `sessions/${session}/tokens/${token}`;
const at = (ms) => Timestamp.fromMillis(ms);

describe('INV-6 LIVE mark', () => {
  test('an enrolled student inside the geofence marks with a fresh token', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(setDoc(ref(db), h.liveMark()));
    const stored = (await getDoc(ref(db))).data();
    expect(stored.status).toBe('PRESENT');
    expect(stored.markedAt.toMillis()).toBeGreaterThan(Date.now() - MINUTE);
  });

  test('a token that is 19.9 s old is still accepted', async () => {
    // Only meaningful if the request reaches the rules inside the remaining
    // 100 ms, so a slow attempt is retried instead of being counted.
    const db = t.as(STUDENT);
    await getDoc(ref(db));
    for (let attempt = 1; ; attempt += 1) {
      await t.reset();
      const seededAt = Date.now();
      await t.seed({ [tokenPath()]: { validFrom: at(seededAt - 19900) } });
      const accepted = await setDoc(ref(db), h.liveMark()).then(
        () => true,
        () => false,
      );
      const elapsedMs = Date.now() - seededAt;
      if (accepted) {
        return;
      }
      if (elapsedMs < 90 || attempt === 5) {
        throw new Error(`denied ${elapsedMs} ms after seeding (attempt ${attempt})`);
      }
    }
  });

  test('denied: a token that is 20.1 s old', async () => {
    await t.seed({ [tokenPath()]: { validFrom: h.ago(20100) } });
    await assertFails(setDoc(ref(t.as(STUDENT)), h.liveMark()));
  });

  test('denied: a direct write with a token that was never issued', async () => {
    const forged = h.liveMark(STUDENT, { token: 'AAAAAAAAAAAAAAAAAAAAAA' });
    await assertFails(setDoc(ref(t.as(STUDENT)), forged));
  });

  test('denied: a token issued for a different session', async () => {
    await assertFails(setDoc(ref(t.as(STUDENT)), h.liveMark(STUDENT, { token: OTHER_TOKEN })));
  });

  test('denied: a student who is not enrolled', async () => {
    await assertFails(setDoc(ref(t.as(OUTSIDER), OUTSIDER), h.liveMark(OUTSIDER)));
  });

  test('denied: the session has been ended', async () => {
    // End time deliberately still ahead, so only the status check can deny it.
    const ended = h.sessionDoc({ status: 'ENDED', endsAt: h.fromNow(10 * MINUTE) });
    await t.seed({ [`sessions/${SESSION}`]: ended });
    await assertFails(setDoc(ref(t.as(STUDENT)), h.liveMark()));
  });

  test('denied: the session is still ACTIVE but past its end time', async () => {
    await t.seed({ [`sessions/${SESSION}`]: h.sessionDoc({ endsAt: h.ago(SECOND) }) });
    await assertFails(setDoc(ref(t.as(STUDENT)), h.liveMark()));
  });

  test.each([
    ['the location is outside the geofence', { lat: h.GEOFENCE.lat + 0.002 }],
    ['accuracy is 0', { accuracyM: 0 }],
    ['accuracy is above 100 m', { accuracyM: 100.5 }],
    ['latitude is a string', { lat: '19.076' }],
    ['a coordinate is missing', { lng: DROP }],
    ['accuracy is missing', { accuracyM: DROP }],
    ['studentId is another student', { studentId: OTHER_STUDENT.uid }],
    ['courseId is not the session course', { courseId: 'courseB' }],
    ['teacherId is not the session teacher', { teacherId: OTHER_TEACHER.uid }],
    ['sessionId does not match the document id', { sessionId: OTHER_SESSION, token: OTHER_TOKEN }],
    ['the status is LATE_SYNC', { status: 'LATE_SYNC' }],
    ['the status is MANUAL_PRESENT', { status: 'MANUAL_PRESENT' }],
    ['the source is MANUAL', { source: 'MANUAL' }],
    ['the source is QUEUED without scannedAt', { source: 'QUEUED', status: 'LATE_SYNC' }],
    ['a LIVE mark carries scannedAt', { scannedAt: h.ago(SECOND) }],
    ['markedAt is a client-chosen time', { markedAt: h.ago(MINUTE) }],
    ['a note is attached', { note: 'present, honest' }],
    ['correction fields are attached', { updatedBy: STUDENT.uid, updatedAt: serverTimestamp() }],
    ['the token is malformed', { token: 'short' }],
    ['the token is missing', { token: DROP }],
  ])('denied when %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(STUDENT)), h.patch(h.liveMark(), change)));
  });

  test('denied: writing the attendance document of another student', async () => {
    const db = t.as(STUDENT);
    await assertFails(setDoc(ref(db, OTHER_STUDENT), h.liveMark(OTHER_STUDENT)));
    await assertFails(setDoc(ref(db, OTHER_STUDENT), h.liveMark(STUDENT)));
  });
});

describe('INV-6 one record per student per session', () => {
  test('denied: marking twice, editing or deleting your own record', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(setDoc(ref(db), h.liveMark()));
    await assertFails(setDoc(ref(db), h.liveMark()));
    await assertFails(updateDoc(ref(db), { status: 'PRESENT', markedAt: serverTimestamp() }));
    await assertFails(deleteDoc(ref(db)));
  });

  test('denied: a queued mark after a live mark for the same session', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(setDoc(ref(db), h.liveMark()));
    await assertFails(setDoc(ref(db), h.queuedMark(h.ago(SECOND))));
  });
});

describe('INV-6 QUEUED mark', () => {
  // The token became valid 10 minutes ago; scans are placed relative to it.
  let validFromMs;
  beforeEach(async () => {
    validFromMs = Date.now() - 10 * MINUTE;
    await t.seed({ [tokenPath()]: { validFrom: at(validFromMs) } });
  });
  const scanned = (offsetMs) => h.queuedMark(at(validFromMs + offsetMs));
  const endSessionAt = (ms) =>
    t.seed({ [`sessions/${SESSION}`]: h.sessionDoc({ status: 'ENDED', endsAt: at(ms) }) });

  test('a scan inside the token window syncs later as LATE_SYNC', async () => {
    const db = t.as(STUDENT);
    await assertSucceeds(setDoc(ref(db), scanned(5 * SECOND)));
    expect((await getDoc(ref(db))).data().status).toBe('LATE_SYNC');
  });

  test('it also syncs after the session has ended', async () => {
    await endSessionAt(validFromMs + 5 * MINUTE);
    await assertSucceeds(setDoc(ref(t.as(STUDENT)), scanned(5 * SECOND)));
  });

  test.each([
    ['exactly when the token became valid', 0],
    ['19.9 s after the token became valid', 19900],
  ])('a scan %s is accepted', async (_, offsetMs) => {
    await assertSucceeds(setDoc(ref(t.as(STUDENT)), scanned(offsetMs)));
  });

  test.each([
    ['20 s after the token became valid', 20000],
    ['20.1 s after the token became valid', 20100],
    ['0.1 s before the token became valid', -100],
  ])('denied: a scan %s', async (_, offsetMs) => {
    await assertFails(setDoc(ref(t.as(STUDENT)), scanned(offsetMs)));
  });

  test('denied: a scan time in the future', async () => {
    const future = Date.now() + MINUTE;
    await t.seed({ [tokenPath()]: { validFrom: at(future) } });
    await assertFails(setDoc(ref(t.as(STUDENT)), h.queuedMark(at(future + 5 * SECOND))));
  });

  test('denied: a scan at or after the session end', async () => {
    await endSessionAt(validFromMs + 3 * SECOND);
    await assertFails(setDoc(ref(t.as(STUDENT)), scanned(5 * SECOND)));
  });

  test('a sync 29.9 min after the session end is accepted', async () => {
    const endMs = Date.now() - (30 * MINUTE - 6 * SECOND);
    await endSessionAt(endMs);
    await t.seed({ [tokenPath()]: { validFrom: at(endMs - MINUTE) } });
    const mark = h.queuedMark(at(endMs - MINUTE + 5 * SECOND));
    await assertSucceeds(setDoc(ref(t.as(STUDENT)), mark));
  });

  test('denied: a sync 30.1 min after the session end', async () => {
    const endMs = Date.now() - (30 * MINUTE + 6 * SECOND);
    await endSessionAt(endMs);
    await t.seed({ [tokenPath()]: { validFrom: at(endMs - MINUTE) } });
    const mark = h.queuedMark(at(endMs - MINUTE + 5 * SECOND));
    await assertFails(setDoc(ref(t.as(STUDENT)), mark));
  });

  test.each([
    ['the status is PRESENT', { status: 'PRESENT' }],
    ['the source is LIVE', { source: 'LIVE' }],
    ['scannedAt is a number', { scannedAt: 1700000000 }],
    ['scannedAt is missing', { scannedAt: DROP }],
    ['the location is outside the geofence', { lat: h.GEOFENCE.lat + 0.002 }],
    ['the token was never issued', { token: 'AAAAAAAAAAAAAAAAAAAAAA' }],
    ['markedAt is a client-chosen time', { markedAt: h.ago(MINUTE) }],
    ['a note is attached', { note: 'was there' }],
  ])('denied when %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(STUDENT)), h.patch(scanned(5 * SECOND), change)));
  });

  test('denied: a student who is not enrolled', async () => {
    const mark = h.queuedMark(at(validFromMs + 5 * SECOND), OUTSIDER);
    await assertFails(setDoc(ref(t.as(OUTSIDER), OUTSIDER), mark));
  });
});
