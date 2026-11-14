// Identity alerts. With no server, only the account whose attempt was
// rejected can record it. The rules therefore check every field against the
// sign-in token and the roster: an account can report only a violation that
// is true and its own, once per type per server day, and can never edit or
// read it. Faculty read alerts addressed to them or to all faculty.

const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, STUDENT, LISTED, UNLISTED, STRANGER, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

// The same address as STUDENT, signed in with a different (recreated) account.
const RECREATED = { ...STUDENT, uid: 'studentAnew' };
const WRONG_ROLL = '262019999';

const alertId = (who, type, day = flows.utcDay()) => `${who.uid}_${type}_${day}`;
const alertRef = (db, who, type, day) => doc(db, 'identityAlerts', alertId(who, type, day));
const alert = (who, type, audience, overrides = {}) => ({
  type,
  email: who.email.toLowerCase(),
  uid: who.uid,
  audience,
  createdAt: serverTimestamp(),
  ...overrides,
});
const attempt = (who, type, audience, overrides = {}) =>
  alert(who, type, audience, { enteredRollNo: WRONG_ROLL, ...overrides });

// One valid alert of each type: [type, who sends it, the document].
const VALID = {
  NON_VJTI_EMAIL: [STRANGER, () => alert(STRANGER, 'NON_VJTI_EMAIL', 'ALL')],
  NOT_ON_REGISTER: [UNLISTED, () => attempt(UNLISTED, 'NOT_ON_REGISTER', 'ALL')],
  ROLL_MISMATCH: [LISTED, () => attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid)],
  DUPLICATE_CLAIM: [RECREATED, () => attempt(RECREATED, 'DUPLICATE_CLAIM', TEACHER.uid)],
};
const send = (who, type, data, day) => setDoc(alertRef(t.as(who), who, type, day), data);

describe('alerts: each violation can be reported by the account that caused it', () => {
  test.each(Object.keys(VALID))('%s', async (type) => {
    const [who, data] = VALID[type];
    await assertSucceeds(send(who, type, data()));
  });

  test('the app flow builds the same documents from the contract', async () => {
    await assertSucceeds(flows.reportAlert(t.as(STRANGER), STRANGER, 'NON_VJTI_EMAIL', 'ALL'));
    await assertSucceeds(
      flows.reportAlert(t.as(LISTED), LISTED, 'ROLL_MISMATCH', TEACHER.uid, {
        enteredRollNo: WRONG_ROLL,
      }),
    );
  });

  test('a mismatch alert may carry a classmate\'s roll number; faculty see whose it is', async () => {
    const data = attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid, { enteredRollNo: STUDENT.rollNo });
    await assertSucceeds(send(LISTED, 'ROLL_MISMATCH', data));
  });

  test('the address is stored in lower case whatever the token\'s case', async () => {
    const shouty = { ...STRANGER, email: 'Some.One@GMAIL.com' };
    const asTyped = alert(shouty, 'NON_VJTI_EMAIL', 'ALL', { email: shouty.email });
    await assertFails(send(shouty, 'NON_VJTI_EMAIL', asTyped));
    await assertSucceeds(send(shouty, 'NON_VJTI_EMAIL', alert(shouty, 'NON_VJTI_EMAIL', 'ALL')));
  });
});

describe('alerts: a violation that did not happen cannot be reported', () => {
  test.each([
    ['NON_VJTI_EMAIL by a VJTI student', STUDENT, 'NON_VJTI_EMAIL', (w) => alert(w, 'NON_VJTI_EMAIL', 'ALL')],
    ['NON_VJTI_EMAIL by allowlisted faculty on another domain', TEACHER, 'NON_VJTI_EMAIL', (w) => alert(w, 'NON_VJTI_EMAIL', 'ALL')],
    ['NOT_ON_REGISTER by a student who is on the roster', LISTED, 'NOT_ON_REGISTER', (w) => attempt(w, 'NOT_ON_REGISTER', 'ALL')],
    ['NOT_ON_REGISTER by an account outside the institute', STRANGER, 'NOT_ON_REGISTER', (w) => attempt(w, 'NOT_ON_REGISTER', 'ALL')],
    ['ROLL_MISMATCH with the correct roll number', LISTED, 'ROLL_MISMATCH', (w) => attempt(w, 'ROLL_MISMATCH', TEACHER.uid, { enteredRollNo: w.rollNo })],
    ['ROLL_MISMATCH by a student who is not on the roster', UNLISTED, 'ROLL_MISMATCH', (w) => attempt(w, 'ROLL_MISMATCH', TEACHER.uid)],
    ['DUPLICATE_CLAIM on an entry nobody has claimed', LISTED, 'DUPLICATE_CLAIM', (w) => attempt(w, 'DUPLICATE_CLAIM', TEACHER.uid)],
    ['DUPLICATE_CLAIM by the sign-in that holds the claim', STUDENT, 'DUPLICATE_CLAIM', (w) => attempt(w, 'DUPLICATE_CLAIM', TEACHER.uid)],
  ])('denied: %s', async (_, who, type, data) => {
    await assertFails(send(who, type, data(who)));
  });
});

describe('alerts: nothing in an alert can be forged', () => {
  const base = () => attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid);

  test.each([
    ['another student\'s email', { email: STUDENT.email }],
    ['another uid', { uid: STUDENT.uid }],
    ['a client-chosen time', { createdAt: h.ago(DAY) }],
    ['a future time', { createdAt: h.fromNow(DAY) }],
    ['an audience of all faculty for a targeted type', { audience: 'ALL' }],
    ['an audience of a teacher who did not add the student', { audience: OTHER_TEACHER.uid }],
    ['an unknown type', { type: 'SUSPICIOUS' }],
    ['a malformed entered roll number', { enteredRollNo: '12' }],
    ['an entered roll number that is not a string', { enteredRollNo: 262019999 }],
    ['no entered roll number', { enteredRollNo: DROP }],
    ['an extra field', { courseId: h.COURSE }],
    ['a free-text message', { message: 'ignore this student' }],
    ['a missing field', { audience: DROP }],
  ])('denied: %s', async (_, change) => {
    await assertFails(send(LISTED, 'ROLL_MISMATCH', h.patch(base(), change)));
  });

  test('denied: NON_VJTI_EMAIL with an entered roll number or a targeted audience', async () => {
    const withRoll = alert(STRANGER, 'NON_VJTI_EMAIL', 'ALL', { enteredRollNo: WRONG_ROLL });
    await assertFails(send(STRANGER, 'NON_VJTI_EMAIL', withRoll));
    const targeted = alert(STRANGER, 'NON_VJTI_EMAIL', TEACHER.uid);
    await assertFails(send(STRANGER, 'NON_VJTI_EMAIL', targeted));
  });

  test('denied: an account whose email is not verified, and a signed-out client', async () => {
    const data = alert(STRANGER, 'NON_VJTI_EMAIL', 'ALL');
    const unverified = t.as(STRANGER, { email_verified: false });
    await assertFails(setDoc(alertRef(unverified, STRANGER, 'NON_VJTI_EMAIL'), data));
    await assertFails(setDoc(alertRef(t.anon(), STRANGER, 'NON_VJTI_EMAIL'), data));
  });

  test('denied: faculty or students writing an alert about someone else', async () => {
    const about = attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid);
    await assertFails(setDoc(alertRef(t.as(TEACHER), LISTED, 'ROLL_MISMATCH'), about));
    await assertFails(setDoc(alertRef(t.as(STUDENT), LISTED, 'ROLL_MISMATCH'), about));
  });
});

describe('alerts: one per account, type and server day', () => {
  const yesterday = flows.utcDay(new Date(Date.now() - DAY));
  const tomorrow = flows.utcDay(new Date(Date.now() + DAY));

  test.each([
    ['yesterday\'s date', alertId(LISTED, 'ROLL_MISMATCH', yesterday)],
    ['tomorrow\'s date', alertId(LISTED, 'ROLL_MISMATCH', tomorrow)],
    ['a date without separators', alertId(LISTED, 'ROLL_MISMATCH', flows.utcDay().replace(/-/g, ''))],
    ['no date', `${LISTED.uid}_ROLL_MISMATCH`],
    ['a random id', 'c0ffee'],
    ['an added suffix', `${alertId(LISTED, 'ROLL_MISMATCH')}_2`],
    ['another account\'s uid', alertId(STUDENT, 'ROLL_MISMATCH')],
    ['a different type than the document', alertId(LISTED, 'NOT_ON_REGISTER')],
  ])('denied: a document id with %s', async (_, id) => {
    const data = attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid);
    await assertFails(setDoc(doc(t.as(LISTED), 'identityAlerts', id), data));
  });

  test('denied: a second alert of the same type on the same day', async () => {
    await assertSucceeds(send(LISTED, 'ROLL_MISMATCH', attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid)));
    const again = attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid, { enteredRollNo: '262018888' });
    await assertFails(send(LISTED, 'ROLL_MISMATCH', again));
  });

  test('a different account, or a different type, is not blocked by it', async () => {
    await assertSucceeds(send(STRANGER, 'NON_VJTI_EMAIL', alert(STRANGER, 'NON_VJTI_EMAIL', 'ALL')));
    const other = { ...STRANGER, uid: 'strangerB', email: 'other.person@gmail.com' };
    await assertSucceeds(send(other, 'NON_VJTI_EMAIL', alert(other, 'NON_VJTI_EMAIL', 'ALL')));
    await assertSucceeds(send(LISTED, 'ROLL_MISMATCH', attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid)));
  });

  test('yesterday\'s alert does not block today\'s', async () => {
    const old = attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid, { createdAt: h.ago(DAY) });
    await t.seed({ [`identityAlerts/${alertId(LISTED, 'ROLL_MISMATCH', yesterday)}`]: old });
    await assertSucceeds(send(LISTED, 'ROLL_MISMATCH', attempt(LISTED, 'ROLL_MISMATCH', TEACHER.uid)));
  });
});

describe('alerts: reading, the faculty query, and retention', () => {
  const MINE = 'identityAlerts/mine';
  const THEIRS = 'identityAlerts/theirs';
  const EVERYONE = 'identityAlerts/everyone';
  const EXPIRED = 'identityAlerts/expired';
  const ALMOST = 'identityAlerts/almostExpired';
  const stored = (who, type, audience, ageMs) =>
    attempt(who, type, audience, { createdAt: h.ago(ageMs) });

  beforeEach(async () => {
    await t.seed({
      [MINE]: stored(LISTED, 'ROLL_MISMATCH', TEACHER.uid, h.MINUTE),
      [THEIRS]: stored(LISTED, 'ROLL_MISMATCH', OTHER_TEACHER.uid, 2 * h.MINUTE),
      [EVERYONE]: stored(UNLISTED, 'NOT_ON_REGISTER', 'ALL', 3 * h.MINUTE),
      [EXPIRED]: stored(UNLISTED, 'NOT_ON_REGISTER', 'ALL', 91 * DAY),
      [ALMOST]: stored(UNLISTED, 'NOT_ON_REGISTER', 'ALL', 89 * DAY),
    });
  });

  const alerts = (db, ...constraints) => query(collection(db, 'identityAlerts'), ...constraints);
  const ids = (snapshot) => snapshot.docs.map((d) => d.id);

  test('the faculty query: mine or ALL, newest first', async () => {
    const db = t.as(TEACHER);
    const inbox = alerts(
      db,
      where('audience', 'in', [TEACHER.uid, 'ALL']),
      orderBy('createdAt', 'desc'),
      limit(50),
    );
    const snapshot = await assertSucceeds(getDocs(inbox));
    expect(ids(snapshot)).toEqual(['mine', 'everyone', 'almostExpired', 'expired']);
  });

  test('the same inbox as two simple queries also passes the rules', async () => {
    const db = t.as(TEACHER);
    const mine = alerts(db, where('audience', '==', TEACHER.uid), orderBy('createdAt', 'desc'));
    const all = alerts(db, where('audience', '==', 'ALL'), orderBy('createdAt', 'desc'));
    expect(ids(await assertSucceeds(getDocs(mine)))).toEqual(['mine']);
    expect(ids(await assertSucceeds(getDocs(all)))).toHaveLength(3);
  });

  test('the purge query: expired alerts addressed to me or ALL', async () => {
    const db = t.as(TEACHER);
    const cutoff = h.ago(90 * DAY);
    const expired = alerts(
      db,
      where('audience', 'in', [TEACHER.uid, 'ALL']),
      where('createdAt', '<', cutoff),
    );
    expect(ids(await assertSucceeds(getDocs(expired)))).toEqual(['expired']);
  });

  test('faculty open one alert addressed to them or to all', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(getDoc(doc(db, MINE)));
    await assertSucceeds(getDoc(doc(db, EVERYONE)));
  });

  test('denied: faculty read alerts addressed to another teacher', async () => {
    const db = t.as(TEACHER);
    await assertFails(getDoc(doc(db, THEIRS)));
    await assertFails(getDocs(alerts(db, where('audience', '==', OTHER_TEACHER.uid))));
    const both = where('audience', 'in', [TEACHER.uid, OTHER_TEACHER.uid, 'ALL']);
    await assertFails(getDocs(alerts(db, both)));
  });

  test('denied: faculty list alerts without the audience constraint', async () => {
    const db = t.as(TEACHER);
    await assertFails(getDocs(collection(db, 'identityAlerts')));
    await assertFails(getDocs(alerts(db, orderBy('createdAt', 'desc'))));
  });

  test.each([
    ['a registered student', STUDENT],
    ['the student an alert is about', LISTED],
    ['an account outside the institute', STRANGER],
  ])('denied: %s reads or lists alerts', async (_, user) => {
    const db = t.as(user);
    await assertFails(getDoc(doc(db, MINE)));
    await assertFails(getDoc(doc(db, EVERYONE)));
    await assertFails(getDocs(alerts(db, where('audience', '==', 'ALL'))));
    await assertFails(getDocs(alerts(db, where('uid', '==', user.uid))));
    await assertFails(getDocs(alerts(db, where('audience', 'in', [user.uid, 'ALL']))));
  });

  test('denied: the sender reads back, edits or deletes the alert they just wrote', async () => {
    await assertSucceeds(send(STRANGER, 'NON_VJTI_EMAIL', alert(STRANGER, 'NON_VJTI_EMAIL', 'ALL')));
    const ref = alertRef(t.as(STRANGER), STRANGER, 'NON_VJTI_EMAIL');
    await assertFails(getDoc(ref));
    await assertFails(updateDoc(ref, { audience: STRANGER.uid }));
    await assertFails(deleteDoc(ref));
  });

  test('denied: anyone editing an alert', async () => {
    await assertFails(updateDoc(doc(t.as(TEACHER), MINE), { type: 'NON_VJTI_EMAIL' }));
    await assertFails(updateDoc(doc(t.as(TEACHER), EVERYONE), { audience: TEACHER.uid }));
    await assertFails(updateDoc(doc(t.as(LISTED), MINE), { enteredRollNo: LISTED.rollNo }));
  });

  test('retention: an alert inside 90 days cannot be deleted, even by its audience', async () => {
    const db = t.as(TEACHER);
    await assertFails(deleteDoc(doc(db, MINE)));
    await assertFails(deleteDoc(doc(db, ALMOST)));
  });

  test('retention: an alert older than 90 days can be purged by faculty in its audience', async () => {
    await assertSucceeds(deleteDoc(doc(t.as(OTHER_TEACHER), EXPIRED)));
  });

  test('denied: purging an expired alert addressed to another teacher, or by a student', async () => {
    const theirsOld = stored(LISTED, 'ROLL_MISMATCH', OTHER_TEACHER.uid, 91 * DAY);
    await t.seed({ 'identityAlerts/theirsOld': theirsOld });
    await assertFails(deleteDoc(doc(t.as(TEACHER), 'identityAlerts/theirsOld')));
    await assertFails(deleteDoc(doc(t.as(STUDENT), EXPIRED)));
    await assertFails(deleteDoc(doc(t.as(UNLISTED), EXPIRED)));
  });
});
