// Invariant 1: who may use the app at all. A verified Google account that is
// either on the faculty allowlist or has an address in exactly the student
// domain. Everything else is refused, on every rule.

const { collection, doc, getDoc, getDocs } = require('firebase/firestore');
const h = require('./helpers');

const { TEACHER, STUDENT, UNLISTED, STRANGER, assertFails, assertSucceeds } = h;
const t = h.setup();

// Any signed-in user may read the campus config, so it shows eligibility alone.
const probe = (db) => getDoc(doc(db, 'appConfig/campus'));
const withEmail = (email) => t.as({ ...UNLISTED, email });

describe('INV-1 eligibility', () => {
  test('a student address in the VJTI domain is accepted', async () => {
    await assertSucceeds(probe(t.as(STUDENT)));
    await assertSucceeds(probe(t.as(UNLISTED)));
  });

  test('the domain comparison ignores case', async () => {
    await assertSucceeds(probe(withEmail('SShah_MC26@MC.VJTI.AC.IN')));
  });

  test('allowlisted faculty are accepted on any domain', async () => {
    await assertSucceeds(probe(t.as(TEACHER)));
  });

  test.each([
    ['a Gmail address', 'sshah_mc26@gmail.com'],
    ['the parent domain', 'sshah_mc26@vjti.ac.in'],
    ['another department', 'sshah_ce26@ce.vjti.ac.in'],
    ['a sub-domain', 'sshah_mc26@mail.mc.vjti.ac.in'],
    ['a look-alike prefix', 'sshah_mc26@xmc.vjti.ac.in'],
    ['a look-alike suffix', 'sshah_mc26@mc.vjti.ac.in.example.com'],
    ['the domain used as the local part', 'mc.vjti.ac.in@gmail.com'],
    ['the domain followed by a second @', 'sshah_mc26@mc.vjti.ac.in@gmail.com'],
    ['a second @ before the domain', 'sshah_mc26@gmail.com@mc.vjti.ac.in'],
  ])('denied: %s', async (_, email) => {
    await assertFails(probe(withEmail(email)));
  });

  test('denied: a VJTI address that is not verified, and a signed-out client', async () => {
    await assertFails(probe(t.as(STUDENT, { email_verified: false })));
    await assertFails(probe(t.anon()));
  });

  test('denied: an ineligible account reads nothing the app uses', async () => {
    const db = t.as(STRANGER);
    await assertFails(getDoc(doc(db, 'joinCodes', h.JOIN_CODE)));
    await assertFails(getDoc(doc(db, 'users', STRANGER.uid)));
    await assertFails(getDoc(doc(db, 'studentRegistry', STRANGER.email)));
    await assertFails(getDocs(collection(db, 'studentRegistry')));
    await assertFails(getDoc(doc(db, 'memberships', `${h.COURSE}_${STRANGER.uid}`)));
    await assertFails(getDoc(doc(db, 'attendance', `${h.SESSION}_${STRANGER.uid}`)));
  });
});
