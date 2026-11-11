// Invariant 1: the optional INSTITUTE_DOMAIN restriction. The shipped rules
// leave it empty (disabled), so this file loads a copy with it switched on.

const fs = require('fs');
const { doc, getDoc } = require('firebase/firestore');
const h = require('./helpers');

const DISABLED = "function INSTITUTE_DOMAIN() { return ''; }";
const ENABLED = "function INSTITUTE_DOMAIN() { return 'example.edu'; }";
const shipped = fs.readFileSync(h.RULES_PATH, 'utf8');

const t = h.setup(() => ({}), shipped.replace(DISABLED, ENABLED));

describe('INV-1 institute domain', () => {
  test('the shipped rules have the restriction disabled', () => {
    expect(shipped).toContain(DISABLED);
  });

  test('an account from the institute domain is accepted', async () => {
    await h.assertSucceeds(getDoc(doc(t.as(h.STUDENT), 'appConfig/campus')));
  });

  test('the domain comparison ignores case', async () => {
    const upper = { ...h.STUDENT, email: 'Meera.Joshi@EXAMPLE.EDU' };
    await h.assertSucceeds(getDoc(doc(t.as(upper), 'appConfig/campus')));
  });

  test.each([
    ['another domain', 'meera.joshi@gmail.com'],
    ['a look-alike suffix', 'meera.joshi@notexample.edu'],
    ['a subdomain', 'meera.joshi@mail.example.edu'],
  ])('denied: an account from %s', async (_, email) => {
    const db = t.as({ ...h.STUDENT, email });
    await h.assertFails(getDoc(doc(db, 'appConfig/campus')));
  });
});
