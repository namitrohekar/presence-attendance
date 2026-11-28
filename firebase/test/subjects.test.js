// Subjects: the HOD makes a batch's subjects from its timetable and assigns
// each to a teacher by address. The teacher claims it with their account; the
// batch's students join it without a join code, a lab only in their own lab
// group. The weekly timetable is one document per batch and semester.

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
} = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, DEPUTY, STUDENT, OTHER_STUDENT, OUTSIDER, LISTED } = h;
const { STRANGER, COURSE, GEOFENCE, DROP, DAY } = h;
const { assertFails, assertSucceeds } = h;

// Operating System for MC26, semester 1: the lecture and two lab groups, all
// claimed by the other teacher; Web Technology, assigned but not claimed yet;
// a semester 2 subject; a subject of batch MC25.
const LECTURE = 'MC26S1OS';
const LAB_1 = 'MC26S1OSG1';
const LAB_2 = 'MC26S1OSG2';
const UNCLAIMED = 'MC26S1WT';
const NEXT_SEMESTER = 'MC26S2AI';
const OTHER_BATCH = 'MC25S3CC';

const subjectDoc = (overrides = {}) => ({
  teacherId: OTHER_TEACHER.uid,
  teacherEmail: OTHER_TEACHER.email,
  name: 'Operating System',
  code: 'R5MC5013T',
  threshold: 75,
  batchId: 'MC26',
  semester: 1,
  labGroup: 0,
  createdBy: TEACHER.uid,
  createdAt: h.ago(10 * DAY),
  ...overrides,
});

const t = h.setup(() => ({
  [h.registryPath(STUDENT)]: h.claimedRegistryDoc(STUDENT, { labGroup: 1 }),
  [h.registryPath(OTHER_STUDENT)]: h.claimedRegistryDoc(OTHER_STUDENT, { labGroup: 2 }),
  [`courses/${LECTURE}`]: subjectDoc(),
  [`courses/${LAB_1}`]: subjectDoc({ name: 'Operating System Lab', labGroup: 1 }),
  [`courses/${LAB_2}`]: subjectDoc({ name: 'Operating System Lab', labGroup: 2 }),
  [`courses/${UNCLAIMED}`]: subjectDoc({ teacherId: '', name: 'Web Technology' }),
  [`courses/${NEXT_SEMESTER}`]: subjectDoc({ semester: 2, name: 'Artificial Intelligence' }),
  [`courses/${OTHER_BATCH}`]: subjectDoc({ batchId: 'MC25', semester: 3, name: 'Cloud' }),
}));

const ref = (db, id) => doc(db, 'courses', id);
const membership = (db, id, who = STUDENT) => doc(db, 'memberships', `${id}_${who.uid}`);

/** A new, unclaimed subject exactly as the HOD's app writes it. */
const newSubject = (overrides = {}) => {
  const out = {
    ...subjectDoc({ teacherId: '', createdAt: serverTimestamp(), name: 'Mini Project' }),
    ...overrides,
  };
  for (const [key, value] of Object.entries(out)) {
    if (value === DROP) {
      delete out[key];
    }
  }
  return out;
};

const join = (id, who = STUDENT, overrides = {}) => ({
  courseId: id,
  teacherId: OTHER_TEACHER.uid,
  studentId: who.uid,
  name: who.name,
  email: who.email,
  rollNo: who.rollNo,
  cohort: who.cohort,
  joinedAt: serverTimestamp(),
  ...overrides,
});

describe('subjects: the HOD makes them', () => {
  const MINI = { batchId: 'MC26', semester: 1, code: 'R5MC5019D', labGroup: 3 };

  test('the HOD saves a lab subject, unclaimed, under its batch, semester, code and group', async () => {
    const db = t.as(TEACHER);
    const subject = { ...MINI, name: 'Mini Project', teacherEmail: OTHER_TEACHER.email };
    expect(await assertSucceeds(flows.saveSubject(db, TEACHER, subject))).toBe('CREATED');
    expect(flows.subjectId('MC26', 1, 'R5MC5019D', 3)).toBe('MC26S1R5MC5019DG3');
    expect((await getDoc(ref(db, 'MC26S1R5MC5019DG3'))).data()).toMatchObject({
      teacherId: '',
      teacherEmail: OTHER_TEACHER.email,
      labGroup: 3,
    });
    expect(await flows.saveSubject(db, TEACHER, subject)).toBe('UNCHANGED');
  });

  test('a subject may be left without a teacher, or given to an HOD who is not on the list', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(setDoc(ref(db, 'MC26S1MP'), newSubject({ teacherEmail: '' })));
    await assertSucceeds(setDoc(ref(db, 'MC26S1MQ'), newSubject({ teacherEmail: DEPUTY.email })));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('denied: %s makes a subject', async (_, user) => {
    await assertFails(setDoc(ref(t.as(user), 'MC26S1MP'), newSubject({ createdBy: user.uid })));
  });

  test.each([
    ['a teacher who is not on the faculty list', { teacherEmail: 'someone@example.edu' }],
    ['a teacher address in capitals', { teacherEmail: OTHER_TEACHER.email.toUpperCase() }],
    ['a subject already claimed', { teacherId: OTHER_TEACHER.uid }],
    ['a batch that does not exist', { batchId: 'MC27' }],
    ['semester 0', { semester: 0 }],
    ['a semester past the programme', { semester: 5 }],
    ['a negative lab group', { labGroup: -1 }],
    ['a lab group the batch does not have', { labGroup: 5 }],
    ['an empty name', { name: '' }],
    ['a threshold of 0', { threshold: 0 }],
    ['no threshold', { threshold: DROP }],
    ['a join code', { joinCode: 'ABC234' }],
    ['another creator', { createdBy: OTHER_TEACHER.uid }],
    ['a creation time from the phone', { createdAt: h.ago(0) }],
  ])('denied: a subject with %s', async (_, change) => {
    await assertFails(setDoc(ref(t.as(TEACHER), 'MC26S1MP'), newSubject(change)));
  });
});

describe('subjects: the HOD changes them', () => {
  test('renaming keeps the claim', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(updateDoc(ref(db, LECTURE), { name: 'Operating Systems' }));
    expect((await getDoc(ref(db, LECTURE))).data().teacherId).toBe(OTHER_TEACHER.uid);
  });

  test('another teacher starts unclaimed', async () => {
    const db = t.as(TEACHER);
    const subject = { batchId: 'MC26', semester: 1, code: 'OS', labGroup: 0 };
    expect(
      await assertSucceeds(
        flows.saveSubject(db, TEACHER, { ...subject, name: 'Operating System', teacherEmail: TEACHER.email }),
      ),
    ).toBe('UPDATED');
    expect((await getDoc(ref(db, LECTURE))).data()).toMatchObject({
      teacherId: '',
      teacherEmail: TEACHER.email,
    });
  });

  test.each([
    ['another teacher who keeps the old claim', { teacherEmail: TEACHER.email }],
    ['a claim made by the HOD', { teacherId: TEACHER.uid }],
    ['a teacher not on the list', { teacherEmail: 'someone@example.edu', teacherId: '' }],
    ['another batch', { batchId: 'MC25' }],
    ['another semester', { semester: 2 }],
    ['another lab group', { labGroup: 1 }],
  ])('denied: a change to %s', async (_, change) => {
    await assertFails(updateDoc(ref(t.as(TEACHER), LECTURE), change));
  });

  test('denied: a teacher who is not the HOD reassigns a subject', async () => {
    await assertFails(
      updateDoc(ref(t.as(OTHER_TEACHER), LECTURE), { teacherEmail: TEACHER.email, teacherId: '' }),
    );
  });

  test("denied: the HOD takes over a teacher's own course", async () => {
    await assertFails(
      updateDoc(ref(t.as(DEPUTY), COURSE), { teacherEmail: DEPUTY.email, teacherId: '' }),
    );
  });
});

describe('subjects: the assigned teacher claims them', () => {
  test('signing in claims every subject assigned to the address, then sessions work', async () => {
    const db = t.as(OTHER_TEACHER);
    expect(await assertSucceeds(flows.claimSubjects(db, OTHER_TEACHER))).toEqual([UNCLAIMED]);
    expect((await getDoc(ref(db, UNCLAIMED))).data().teacherId).toBe(OTHER_TEACHER.uid);
    expect(await flows.claimSubjects(db, OTHER_TEACHER)).toEqual([]);
    await assertSucceeds(flows.startSession(db, OTHER_TEACHER, 'sessWT', UNCLAIMED, 60, GEOFENCE));
  });

  test('denied: a session on a subject nobody has claimed', async () => {
    await assertFails(flows.startSession(t.as(TEACHER), TEACHER, 'sessWT', UNCLAIMED, 60, GEOFENCE));
  });

  test.each([
    ['a teacher it is not assigned to', DEPUTY, { teacherId: DEPUTY.uid }],
    ['the assigned teacher, for another account', OTHER_TEACHER, { teacherId: 'someoneElse' }],
    ['the assigned teacher, renaming it too', OTHER_TEACHER, { teacherId: OTHER_TEACHER.uid, name: 'X' }],
    ['a student', STUDENT, { teacherId: STUDENT.uid }],
  ])('denied: a claim by %s', async (_, user, change) => {
    await assertFails(updateDoc(ref(t.as(user), UNCLAIMED), change));
  });

  test('denied: claiming a subject someone has already claimed', async () => {
    await t.seed({ [`courses/${UNCLAIMED}`]: subjectDoc({ teacherEmail: DEPUTY.email }) });
    await assertFails(updateDoc(ref(t.as(DEPUTY), UNCLAIMED), { teacherId: DEPUTY.uid }));
  });

  test('a removed teacher can no longer claim', async () => {
    await t.env.withSecurityRulesDisabled((ctx) =>
      deleteDoc(doc(ctx.firestore(), 'faculty', OTHER_TEACHER.email)),
    );
    await assertFails(
      updateDoc(ref(t.as(OTHER_TEACHER), UNCLAIMED), { teacherId: OTHER_TEACHER.uid }),
    );
  });
});

describe('subjects: who can read them', () => {
  const list = (db, ...filters) => getDocs(query(collection(db, 'courses'), ...filters));

  test('a teacher lists the subjects assigned to them', async () => {
    const found = await assertSucceeds(
      list(t.as(OTHER_TEACHER), where('teacherEmail', '==', OTHER_TEACHER.email)),
    );
    expect(found.size).toBe(6);
  });

  test('the HOD lists a batch, and a student lists their own batch', async () => {
    await assertSucceeds(list(t.as(DEPUTY), where('batchId', '==', 'MC25')));
    const mine = await assertSucceeds(
      list(t.as(STUDENT), where('batchId', '==', 'MC26'), where('semester', '==', 1)),
    );
    expect(mine.docs.map((d) => d.id).sort()).toEqual([LECTURE, LAB_1, LAB_2, UNCLAIMED].sort());
    await assertSucceeds(getDoc(ref(t.as(STUDENT), LAB_2)));
  });

  test('a teacher still lists the courses they own', async () => {
    await assertSucceeds(list(t.as(TEACHER), where('teacherId', '==', TEACHER.uid)));
  });

  test.each([
    ['a student lists another batch', STUDENT, where('batchId', '==', 'MC25')],
    ['a teacher lists a batch', OTHER_TEACHER, where('batchId', '==', 'MC26')],
    ["a teacher lists someone else's subjects", OTHER_TEACHER, where('teacherEmail', '==', DEPUTY.email)],
    ['a stranger lists a batch', STRANGER, where('batchId', '==', 'MC26')],
  ])('denied: %s', async (_, user, filter) => {
    await assertFails(list(t.as(user), filter));
  });

  test("the HOD reads any course, even one that does not exist yet", async () => {
    await t.seed({ 'courses/courseB': h.courseDoc({ teacherId: OTHER_TEACHER.uid, joinCode: 'QRS567' }) });
    await assertSucceeds(getDoc(ref(t.as(DEPUTY), 'courseB')));
    await assertSucceeds(getDoc(ref(t.as(DEPUTY), 'MC26S1NEW')));
  });

  test.each([
    ['a teacher', OTHER_TEACHER],
    ['a student', STUDENT],
  ])('denied: %s reads a course that does not exist', async (_, user) => {
    await assertFails(getDoc(ref(t.as(user), 'MC26S1NEW')));
  });

  test("denied: a student reads another batch's subject", async () => {
    await assertFails(getDoc(ref(t.as(STUDENT), OTHER_BATCH)));
  });
});

describe('subjects: students join without a join code', () => {
  test("a student joins this semester's claimed subjects for the batch and their lab group", async () => {
    const db = t.as(STUDENT);
    expect(await assertSucceeds(flows.joinSubjects(db, STUDENT))).toEqual({
      joined: [LECTURE, LAB_1].sort(),
      moved: [],
    });
    expect(await flows.joinSubjects(db, STUDENT)).toEqual({ joined: [], moved: [] });
    expect(await flows.joinSubjects(t.as(OTHER_STUDENT), OTHER_STUDENT)).toEqual({
      joined: [LECTURE, LAB_2].sort(),
      moved: [],
    });
  });

  test('a student without a lab group joins only the lectures', async () => {
    expect(await flows.joinSubjects(t.as(OUTSIDER), OUTSIDER)).toEqual({ joined: [LECTURE], moved: [] });
  });

  test('the teacher then sees them, and they can mark', async () => {
    await flows.joinSubjects(t.as(STUDENT), STUDENT);
    const members = await assertSucceeds(
      getDocs(
        query(
          collection(t.as(OTHER_TEACHER), 'memberships'),
          where('teacherId', '==', OTHER_TEACHER.uid),
          where('courseId', '==', LAB_1),
        ),
      ),
    );
    expect(members.size).toBe(1);
  });

  test.each([
    ["another lab group's lab", LAB_2, STUDENT, {}],
    ['a lab, without a lab group', LAB_1, OUTSIDER, {}],
    ['a subject nobody has claimed', UNCLAIMED, STUDENT, { teacherId: '' }],
    ['a subject with another teacher named', LECTURE, STUDENT, { teacherId: TEACHER.uid }],
    ["another batch's subject", OTHER_BATCH, STUDENT, {}],
    ['as an unregistered student', LECTURE, LISTED, {}],
    ['with another roll number', LECTURE, STUDENT, { rollNo: '262010009' }],
    ['with another name', LECTURE, STUDENT, { name: 'Someone' }],
    ['with a join time from the phone', LECTURE, STUDENT, { joinedAt: h.ago(0) }],
    ['with a join code', LECTURE, STUDENT, { joinCode: 'ABC234' }],
  ])('denied: joining %s', async (_, id, who, change) => {
    await assertFails(setDoc(membership(t.as(who), id, who), join(id, who, change)));
  });

  test("denied: joining a teacher's own course without its join code", async () => {
    await assertFails(
      setDoc(membership(t.as(OUTSIDER), COURSE, OUTSIDER), join(COURSE, OUTSIDER, { teacherId: TEACHER.uid })),
    );
  });

  test('denied: joining for a classmate', async () => {
    await assertFails(setDoc(membership(t.as(STUDENT), LECTURE, OUTSIDER), join(LECTURE, OUTSIDER)));
  });
});

describe('subjects: a reassigned subject', () => {
  async function reassign() {
    await flows.joinSubjects(t.as(STUDENT), STUDENT);
    const subject = { batchId: 'MC26', semester: 1, code: 'OS', labGroup: 0 };
    await flows.saveSubject(t.as(TEACHER), TEACHER, {
      ...subject,
      name: 'Operating System',
      teacherEmail: TEACHER.email,
    });
  }

  test("the student's app moves the membership once the new teacher has claimed it", async () => {
    await reassign();
    expect(await flows.joinSubjects(t.as(STUDENT), STUDENT)).toEqual({ joined: [], moved: [] });
    expect(await flows.claimSubjects(t.as(TEACHER), TEACHER)).toEqual([LECTURE]);
    expect(await assertSucceeds(flows.joinSubjects(t.as(STUDENT), STUDENT))).toEqual({
      joined: [],
      moved: [LECTURE],
    });
    expect((await getDoc(membership(t.as(STUDENT), LECTURE))).data().teacherId).toBe(TEACHER.uid);
  });

  test.each([
    ['to a teacher who is not the subject\'s', STUDENT, STUDENT, { teacherId: DEPUTY.uid }],
    ['with another field', STUDENT, STUDENT, { teacherId: TEACHER.uid, rollNo: '262010009' }],
    ["of a classmate's membership", OTHER_STUDENT, STUDENT, { teacherId: TEACHER.uid }],
  ])('denied: a move %s', async (_, user, owner, change) => {
    await reassign();
    await flows.claimSubjects(t.as(TEACHER), TEACHER);
    await assertFails(updateDoc(membership(t.as(user), LECTURE, owner), change));
  });
});

describe('timetables', () => {
  const SLOT = {
    day: 1,
    start: '10:30',
    end: '12:25',
    courseId: LAB_1,
    type: 'LAB',
    room: 'AL-301',
    faculty: 'GS',
  };
  const tt = (db, id = 'MC26S1') => doc(db, 'timetables', id);
  const newTimetable = (overrides = {}) => ({
    batchId: 'MC26',
    semester: 1,
    slots: [SLOT],
    updatedBy: TEACHER.uid,
    updatedAt: serverTimestamp(),
    ...overrides,
  });

  test('the HOD saves and replaces a timetable; everyone signed in reads it', async () => {
    const db = t.as(TEACHER);
    await assertSucceeds(flows.saveTimetable(db, TEACHER, 'MC26', 1, [SLOT]));
    await assertSucceeds(flows.saveTimetable(db, TEACHER, 'MC26', 1, [SLOT, { ...SLOT, day: 2 }]));
    expect((await getDoc(tt(t.as(STUDENT)))).data().slots).toHaveLength(2);
    await assertSucceeds(getDoc(tt(t.as(OTHER_TEACHER))));
  });

  test('denied: a stranger reads one', async () => {
    await flows.saveTimetable(t.as(TEACHER), TEACHER, 'MC26', 1, [SLOT]);
    await assertFails(getDoc(tt(t.as(STRANGER))));
  });

  test.each([
    ['a teacher who is not the HOD', OTHER_TEACHER, 'MC26S1', {}],
    ['a student', STUDENT, 'MC26S1', {}],
    ['the HOD, under another id', TEACHER, 'MC26S2', {}],
    ['the HOD, for a batch that does not exist', TEACHER, 'MC27S1', { batchId: 'MC27' }],
    ['the HOD, for a semester past the programme', TEACHER, 'MC26S5', { semester: 5 }],
    ['the HOD, with slots that are not a list', TEACHER, 'MC26S1', { slots: SLOT }],
    ['the HOD, with too many slots', TEACHER, 'MC26S1', { slots: Array(201).fill(SLOT) }],
    ['the HOD, naming another author', TEACHER, 'MC26S1', { updatedBy: OTHER_TEACHER.uid }],
    ['the HOD, with an extra field', TEACHER, 'MC26S1', { note: 'x' }],
  ])('denied: a timetable written by %s', async (_, user, id, change) => {
    await assertFails(setDoc(tt(t.as(user), id), newTimetable({ updatedBy: user.uid, ...change })));
  });
});
