// Invariant 4: sessions. Only the course's teacher creates, with server start
// time, bounded duration and bounded geofence. The only update is
// ACTIVE -> ENDED by the teacher, and it never moves endsAt later.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OUTSIDER, COURSE, SESSION, GEOFENCE, DROP } = h;
const { MINUTE, SECOND, assertFails, assertSucceeds } = h;

const EXPIRED = 'sessExpired';
const ENDED = 'sessEnded';

const t = h.setup(() => ({
  // Still marked ACTIVE, but its end time passed a minute ago.
  [`sessions/${EXPIRED}`]: h.sessionDoc({ startedAt: h.ago(61 * MINUTE), endsAt: h.ago(MINUTE) }),
  [`sessions/${ENDED}`]: h.sessionDoc({
    status: 'ENDED',
    startedAt: h.ago(120 * MINUTE),
    endsAt: h.ago(60 * MINUTE),
  }),
}));

const NEW = 'sessNew';
const newSession = (overrides = {}) => ({
  courseId: COURSE,
  teacherId: TEACHER.uid,
  status: 'ACTIVE',
  startedAt: serverTimestamp(),
  endsAt: h.fromNow(60 * MINUTE),
  geofence: GEOFENCE,
  ...overrides,
});
const geofence = (change) => ({ geofence: h.patch(GEOFENCE, change) });
const ref = (db, id = NEW) => doc(db, 'sessions', id);

describe('INV-4 session create', () => {
  test('the course teacher starts a session', async () => {
    await assertSucceeds(setDoc(ref(t.as(TEACHER)), newSession()));
  });

  // Bounds are [now + 4 min, now + 181 min]: 5 and 180 minutes with 1 minute
  // of slack for the gap between the device clock and the server clock.
  test.each([
    ['4 min 5 s (just above the minimum)', 4 * MINUTE + 5 * SECOND],
    ['180 min 55 s (just below the maximum)', 181 * MINUTE - 5 * SECOND],
  ])('a duration of %s is accepted', async (_, ms) => {
    await assertSucceeds(setDoc(ref(t.as(TEACHER)), newSession({ endsAt: h.fromNow(ms) })));
  });

  test.each([
    ['3 min 55 s (just below the minimum)', 4 * MINUTE - 5 * SECOND],
    ['181 min 5 s (just above the maximum)', 181 * MINUTE + 5 * SECOND],
    ['a time in the past', -MINUTE],
  ])('denied: a duration of %s', async (_, ms) => {
    await assertFails(setDoc(ref(t.as(TEACHER)), newSession({ endsAt: h.fromNow(ms) })));
  });

  test.each([30, 1000])('a geofence radius of %i m is accepted', async (radiusM) => {
    await assertSucceeds(setDoc(ref(t.as(TEACHER)), newSession(geofence({ radiusM }))));
  });

  test.each([
    ['radius is 29.9 m', geofence({ radiusM: 29.9 })],
    ['radius is 1000.1 m', geofence({ radiusM: 1000.1 })],
    ['radius is a string', geofence({ radiusM: '100' })],
    ['latitude is above 90', geofence({ lat: 90.1 })],
    ['longitude is below -180', geofence({ lng: -180.1 })],
    ['cosLat is 0', geofence({ cosLat: 0 })],
    ['cosLat is negative', geofence({ cosLat: -0.5 })],
    ['cosLat is above 1', geofence({ cosLat: 1.01 })],
    ['the geofence has an extra key', geofence({ altitude: 10 })],
    ['the geofence is missing a key', geofence({ cosLat: DROP })],
    ['the geofence is not a map', { geofence: 'campus' }],
    ['the status is ENDED', { status: 'ENDED' }],
    ['startedAt is a client-chosen time', { startedAt: h.ago(MINUTE) }],
    ['endsAt is not a timestamp', { endsAt: 'later' }],
    ['teacherId is someone else', { teacherId: OTHER_TEACHER.uid }],
    ['the course does not exist', { courseId: 'ghost' }],
    ['an extra field is present', { title: 'Lecture 4' }],
    ['a required field is missing', { geofence: DROP }],
  ])('denied when %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(TEACHER)), h.patch(newSession(), change)));
  });

  test('denied: another teacher starts a session for a course they do not own', async () => {
    const data = newSession({ teacherId: OTHER_TEACHER.uid });
    await assertFails(setDoc(ref(t.as(OTHER_TEACHER)), data));
  });

  test('denied: an enrolled student starts a session', async () => {
    const data = newSession({ teacherId: STUDENT.uid });
    await assertFails(setDoc(ref(t.as(STUDENT)), data));
  });
});

describe('INV-4 session end', () => {
  const end = { status: 'ENDED', endsAt: serverTimestamp() };

  test('the teacher ends an active session at the server time', async () => {
    const db = t.as(TEACHER);
    const before = (await getDoc(ref(db, SESSION))).data().endsAt;
    await assertSucceeds(updateDoc(ref(db, SESSION), end));
    const after = (await getDoc(ref(db, SESSION))).data();
    expect(after.status).toBe('ENDED');
    expect(after.endsAt.toMillis()).toBeLessThan(before.toMillis());
  });

  test.each([
    ['another teacher', OTHER_TEACHER],
    ['an enrolled student', STUDENT],
  ])('denied: %s ends the session', async (_, user) => {
    await assertFails(updateDoc(ref(t.as(user), SESSION), end));
  });

  test.each([
    ['ending with a client-chosen end time', { status: 'ENDED', endsAt: h.ago(SECOND) }],
    ['ending without stamping the end time', { status: 'ENDED' }],
    ['extending a running session', { endsAt: h.fromNow(170 * MINUTE) }],
    ['changing the geofence while ending', { ...end, geofence: { ...GEOFENCE, radiusM: 1000 } }],
    ['moving the session to another course', { ...end, courseId: 'courseB' }],
    ['an unknown status', { status: 'PAUSED', endsAt: serverTimestamp() }],
  ])('denied: %s', async (_, change) => {
    await assertFails(updateDoc(ref(t.as(TEACHER), SESSION), change));
  });

  test('denied: reopening or re-ending an ended session', async () => {
    const db = t.as(TEACHER);
    await assertFails(updateDoc(ref(db, ENDED), { status: 'ACTIVE' }));
    await assertFails(updateDoc(ref(db, ENDED), end));
  });

  test('denied: "ending" an expired session at the server time, which would extend it', async () => {
    await assertFails(updateDoc(ref(t.as(TEACHER), EXPIRED), end));
  });

  test('an expired session is closed with its original end time', async () => {
    await assertSucceeds(updateDoc(ref(t.as(TEACHER), EXPIRED), { status: 'ENDED' }));
  });

  test('denied: deleting a session', async () => {
    await assertFails(deleteDoc(ref(t.as(TEACHER), SESSION)));
  });
});

describe('INV-4 session reads', () => {
  test('the teacher and an enrolled student read the session', async () => {
    await assertSucceeds(getDoc(ref(t.as(TEACHER), SESSION)));
    await assertSucceeds(getDoc(ref(t.as(STUDENT), SESSION)));
  });

  test('an enrolled student lists the sessions of the course, newest first', async () => {
    const db = t.as(STUDENT);
    const history = query(
      collection(db, 'sessions'),
      where('courseId', '==', COURSE),
      orderBy('startedAt', 'desc'),
    );
    const snap = await assertSucceeds(getDocs(history));
    expect(snap.docs.map((d) => d.id)).toEqual([SESSION, EXPIRED, ENDED]);
  });

  test('the teacher lists their sessions for the course', async () => {
    const db = t.as(TEACHER);
    const mine = query(
      collection(db, 'sessions'),
      where('teacherId', '==', TEACHER.uid),
      where('courseId', '==', COURSE),
      orderBy('startedAt', 'desc'),
    );
    const snap = await assertSucceeds(getDocs(mine));
    expect(snap.size).toBe(3);
  });

  test('denied: a non-member or another teacher reads or lists sessions', async () => {
    await assertFails(getDoc(ref(t.as(OUTSIDER), SESSION)));
    await assertFails(getDoc(ref(t.as(OTHER_TEACHER), SESSION)));
    const db = t.as(OUTSIDER);
    const history = query(collection(db, 'sessions'), where('courseId', '==', COURSE));
    await assertFails(getDocs(history));
    await assertFails(getDocs(collection(t.as(TEACHER), 'sessions')));
  });
});
