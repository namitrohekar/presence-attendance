// One phone per account: a student's profile is bound to the phone they first
// sign in on, marks are accepted only from that phone, and only the HOD can
// move the account to a new one.

const { deleteField, doc, getDoc, updateDoc } = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, LISTED } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const NEW_PHONE = 'studentANewPhone000000';
const profileRef = (db, who = STUDENT) => doc(db, 'users', who.uid);
const markRef = (db, who = STUDENT) => doc(db, 'attendance', `${h.SESSION}_${who.uid}`);
const unbound = () =>
  t.seed({ [`users/${STUDENT.uid}`]: h.studentProfileDoc(STUDENT) });

describe('phones: binding', () => {
  test('registering binds the account to the phone it registers on', async () => {
    const db = t.as(LISTED);
    expect(await flows.register(db, LISTED, LISTED.rollNo)).toBe('REGISTERED');
    expect((await getDoc(profileRef(db, LISTED))).data().deviceId).toBe(h.deviceOf(LISTED));
  });

  test('an account from before binding is bound at its next sign-in', async () => {
    await unbound();
    const db = t.as(STUDENT);
    expect(await assertSucceeds(flows.bindPhone(db, STUDENT, NEW_PHONE))).toBe('BOUND');
    expect(await flows.bindPhone(db, STUDENT, NEW_PHONE)).toBe('SAME_PHONE');
    expect(await flows.bindPhone(db, STUDENT, h.deviceOf(STUDENT))).toBe('OTHER_PHONE');
  });

  test('a profile rewritten at sign-in keeps its phone', async () => {
    await assertSucceeds(
      updateDoc(profileRef(t.as(STUDENT)), { name: STUDENT.name, deviceId: h.deviceOf(STUDENT) }),
    );
  });

  test.each([
    ['moves it to another phone', { deviceId: NEW_PHONE }],
    ['removes its phone', { deviceId: deleteField() }],
  ])('denied: the student %s', async (_, change) => {
    await assertFails(updateDoc(profileRef(t.as(STUDENT)), change));
  });

  test('denied: binding to something that is not a phone id', async () => {
    await unbound();
    await assertFails(updateDoc(profileRef(t.as(STUDENT)), { deviceId: 'my phone' }));
  });
});

describe('phones: marking', () => {
  test('a mark from the bound phone is accepted', async () => {
    await assertSucceeds(h.submit(markRef(t.as(STUDENT)), h.liveMark()));
  });

  test('denied: a mark from another phone, signed in as the student', async () => {
    await assertFails(
      h.submit(markRef(t.as(STUDENT)), h.liveMark(STUDENT, { deviceId: NEW_PHONE })),
    );
  });

  test('denied: a mark from an account that is not bound to any phone', async () => {
    await unbound();
    await assertFails(h.submit(markRef(t.as(STUDENT)), h.liveMark()));
  });

  test('denied: a saved scan sent later from another phone', async () => {
    await assertFails(
      h.submit(
        markRef(t.as(STUDENT)),
        h.queuedMark(h.ago(h.SECOND), STUDENT, { deviceId: NEW_PHONE }),
      ),
    );
  });
});

describe('phones: the HOD moves an account to a new phone', () => {
  test('after the HOD releases it, the new phone is bound and marks', async () => {
    await assertSucceeds(flows.releasePhone(t.as(TEACHER), STUDENT.uid));
    const db = t.as(STUDENT);
    expect(await flows.bindPhone(db, STUDENT, NEW_PHONE)).toBe('BOUND');
    await assertSucceeds(h.submit(markRef(db), h.liveMark(STUDENT, { deviceId: NEW_PHONE })));
  });

  test('a second HOD may release one too', async () => {
    await assertSucceeds(flows.releasePhone(t.as(DEPUTY), STUDENT.uid));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER, { deviceId: deleteField() }],
    ['the student', STUDENT, { deviceId: deleteField() }],
    ['the HOD, choosing the new phone', TEACHER, { deviceId: NEW_PHONE }],
    ['the HOD, changing more than the phone', TEACHER, { deviceId: deleteField(), name: 'X' }],
  ])('denied: a release by %s', async (_, user, change) => {
    await assertFails(updateDoc(profileRef(t.as(user)), change));
  });

  test("denied: the HOD 'releases' a teacher's profile", async () => {
    await t.seed({
      [`users/${OTHER_TEACHER.uid}`]: {
        name: OTHER_TEACHER.name,
        email: OTHER_TEACHER.email,
        role: 'FACULTY',
        createdAt: h.ago(h.DAY),
      },
    });
    await assertFails(flows.releasePhone(t.as(TEACHER), OTHER_TEACHER.uid));
  });
});
