// Invariant 5: tokens. Only the session's teacher creates one, while the
// session is live, stamped with the server time and a 22-character base64url
// id. Nobody can read, list, update or delete a token.

const {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, OTHER_TEACHER, STUDENT, OUTSIDER, SESSION, TOKEN } = h;
const { MINUTE, assertFails, assertSucceeds } = h;

const EXPIRED = 'sessExpired';
const ENDED = 'sessEnded';
const t = h.setup(() => ({
  [`sessions/${EXPIRED}`]: h.sessionDoc({ startedAt: h.ago(61 * MINUTE), endsAt: h.ago(MINUTE) }),
  // End time deliberately still ahead, so only the status check can deny it.
  [`sessions/${ENDED}`]: h.sessionDoc({ status: 'ENDED', endsAt: h.fromNow(10 * MINUTE) }),
}));

const NEW_TOKEN = 'newToken_0000000-00002';
const fresh = () => ({ validFrom: serverTimestamp() });
const ref = (db, token = NEW_TOKEN, session = SESSION) =>
  doc(db, 'sessions', session, 'tokens', token);

describe('INV-5 token create', () => {
  test('the session teacher writes a token while the session is live', async () => {
    await assertSucceeds(setDoc(ref(t.as(TEACHER)), fresh()));
  });

  test.each([
    ['an enrolled student', STUDENT],
    ['another teacher', OTHER_TEACHER],
    ['a non-member', OUTSIDER],
  ])('denied: %s writes a token', async (_, user) => {
    await assertFails(setDoc(ref(t.as(user)), fresh()));
  });

  test.each([
    ['validFrom is a client-chosen time', { validFrom: h.fromNow(10 * MINUTE) }],
    ['validFrom is missing', {}],
    ['an extra field is present', { validFrom: serverTimestamp(), validFor: 3600 }],
  ])('denied when %s', async (_, data) => {
    await assertFails(setDoc(ref(t.as(TEACHER)), data));
  });

  test.each([
    ['21 characters', 'a'.repeat(21)],
    ['23 characters', 'a'.repeat(23)],
    ['a character outside base64url', `${'a'.repeat(21)}+`],
    ['base64 padding', `${'a'.repeat(21)}=`],
  ])('denied when the token id has %s', async (_, token) => {
    await assertFails(setDoc(ref(t.as(TEACHER), token), fresh()));
  });

  test.each([
    ['has been ended', ENDED],
    ['has passed its end time', EXPIRED],
    ['does not exist', 'ghost'],
  ])('denied when the session %s', async (_, session) => {
    await assertFails(setDoc(ref(t.as(TEACHER), NEW_TOKEN, session), fresh()));
  });

  test('denied: rewriting an existing token to renew it', async () => {
    await assertFails(setDoc(ref(t.as(TEACHER), TOKEN), fresh()));
    await assertFails(updateDoc(ref(t.as(TEACHER), TOKEN), fresh()));
  });
});

describe('INV-5 tokens are unreadable', () => {
  test.each([
    ['the session teacher', TEACHER],
    ['an enrolled student', STUDENT],
    ['a non-member', OUTSIDER],
  ])('denied: %s gets, lists or deletes tokens', async (_, user) => {
    const db = t.as(user);
    await assertFails(getDoc(ref(db, TOKEN)));
    await assertFails(getDocs(collection(db, 'sessions', SESSION, 'tokens')));
    await assertFails(getDocs(collectionGroup(db, 'tokens')));
    await assertFails(deleteDoc(ref(db, TOKEN)));
  });
});
