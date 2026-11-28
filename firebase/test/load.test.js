// A full class at once: 60 enrolled students mark the same session at the same
// moment while the teacher's phone keeps rotating the code, and everyone who
// should be refused tries too. This exercises the rules and the transactions
// under concurrency on the emulator. It says nothing about a real network.

const { collection, getDocs, query, where } = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, STUDENT, assertFails } = h;
const CLASS_SIZE = 60;

const classmates = Array.from({ length: CLASS_SIZE }, (_, i) => {
  const n = String(i + 1).padStart(2, '0');
  return {
    uid: `load${n}`,
    email: `load${n}_mc26@mc.vjti.ac.in`,
    name: `Load Student ${n}`,
    rollNo: `2620200${n}`,
    cohort: 'MC26',
  };
});

const t = h.setup(() =>
  Object.assign(
    {},
    ...classmates.map((s) => ({
      ...h.rosterDocs(s, true),
      [`memberships/${h.COURSE}_${s.uid}`]: h.membershipDoc(s),
      [`users/${s.uid}`]: h.studentProfileDoc(s, { deviceId: h.deviceOf(s) }),
    })),
  ),
);

const outcome = (promise) => promise.then((value) => value, () => 'REFUSED');

describe('a class of 60 marks at once', () => {
  jest.setTimeout(120000);

  test('exactly one record per enrolled student, and nobody else gets in', async () => {
    const session = { id: h.SESSION, ...h.sessionDoc() };
    const faculty = t.as(TEACHER);
    const NEXT_TOKEN = 'loadToken_000000-00002';
    const OLD_TOKEN = 'loadToken_000000-00000';
    await t.seed({ [`sessions/${h.SESSION}/tokens/${OLD_TOKEN}`]: { validFrom: h.ago(21 * h.SECOND) } });
    await flows.writeToken(faculty, h.SESSION, NEXT_TOKEN);

    // Each student submits twice at the same instant (a double tap), half with the code
    // that was on screen a moment ago and half with the new one.
    const marks = classmates.flatMap((s, i) => {
      const db = t.as(s);
      const token = i % 2 === 0 ? h.TOKEN : NEXT_TOKEN;
      return [
        outcome(flows.markLive(db, s, session, token, h.INSIDE)),
        outcome(flows.markLive(db, s, session, token, h.INSIDE)),
      ];
    });
    // Meanwhile the teacher's phone keeps issuing codes.
    const rotation = ['loadToken_000000-00003', 'loadToken_000000-00004'].map((token) =>
      flows.writeToken(faculty, h.SESSION, token),
    );
    // And everyone who must be refused tries at the same time.
    const outside = { ...h.INSIDE, lat: h.INSIDE.lat + 0.01 };
    const refused = [
      flows.markLive(t.as(h.OUTSIDER), h.OUTSIDER, session, h.TOKEN, h.INSIDE), // not enrolled
      flows.markLive(t.as(h.LISTED), h.LISTED, session, h.TOKEN, h.INSIDE), // never registered
      flows.markLive(t.as(h.STRANGER), h.STRANGER, session, h.TOKEN, h.INSIDE), // not a student
      flows.markLive(t.as(STUDENT), STUDENT, session, OLD_TOKEN, h.INSIDE), // stale code
      flows.markLive(t.as(h.OTHER_STUDENT), h.OTHER_STUDENT, session, h.TOKEN, outside), // far away
      flows.markLive(t.as(STUDENT), h.OTHER_STUDENT, session, h.TOKEN, h.INSIDE), // for a friend
    ].map(outcome);

    const [markResults, , refusedResults] = await Promise.all([
      Promise.all(marks),
      Promise.all(rotation),
      Promise.all(refused),
    ]);

    // Of each pair of submissions exactly one created the record.
    for (let i = 0; i < CLASS_SIZE; i++) {
      const pair = [markResults[2 * i], markResults[2 * i + 1]];
      expect(pair.filter((r) => r === 'MARKED')).toHaveLength(1);
      expect(['ALREADY_MARKED', 'REFUSED']).toContain(pair.find((r) => r !== 'MARKED'));
    }
    expect(refusedResults).toEqual(Array(refused.length).fill('REFUSED'));

    // What the teacher's roster shows.
    const roster = await getDocs(
      query(
        collection(faculty, 'attendance'),
        where('sessionId', '==', h.SESSION),
        where('teacherId', '==', TEACHER.uid),
      ),
    );
    const marked = roster.docs.map((d) => d.data().studentId).sort();
    expect(marked).toEqual(classmates.map((s) => s.uid).sort());
    expect(new Set(roster.docs.map((d) => d.id)).size).toBe(CLASS_SIZE);

    // A latecomer after the fact cannot overwrite anyone's record.
    await assertFails(
      flows.markLive(t.as(classmates[0]), classmates[1], session, NEXT_TOKEN, h.INSIDE),
    );
    expect(roster.docs.every((d) => d.data().status === 'PRESENT')).toBe(true);
  });
});
