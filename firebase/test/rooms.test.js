// Rooms: a teacher saves the geofence of the room they stand in, under the
// room's name as the timetable writes it, and later sessions there copy it.
// Faculty read and write rooms; students never see them.

const { doc, getDoc, getDocs, collection, serverTimestamp, setDoc } = require('firebase/firestore');
const h = require('./helpers');
const flows = require('./flows');

const { TEACHER, OTHER_TEACHER, STUDENT, STRANGER, GEOFENCE } = h;
const { assertFails, assertSucceeds } = h;
const t = h.setup();

const ref = (db, id = 'AL301') => doc(db, 'rooms', id);
const room = (who, overrides = {}) => ({
  name: 'AL-301',
  geofence: GEOFENCE,
  savedBy: who.uid,
  savedAt: serverTimestamp(),
  ...overrides,
});

describe('rooms', () => {
  test('a teacher saves a room under its name without punctuation, and saves it again', async () => {
    const db = t.as(OTHER_TEACHER);
    expect(flows.roomId('AL-301')).toBe('AL301');
    await assertSucceeds(flows.saveRoom(db, OTHER_TEACHER, 'AL-301', GEOFENCE));
    await assertSucceeds(
      flows.saveRoom(t.as(TEACHER), TEACHER, 'AL-301', { ...GEOFENCE, radiusM: 40 }),
    );
    expect((await getDoc(ref(db))).data().geofence.radiusM).toBe(40);
    await assertSucceeds(getDocs(collection(db, 'rooms')));
  });

  test.each([
    ['a student', STUDENT],
    ['a stranger', STRANGER],
  ])('denied: %s saves or reads a room', async (_, user) => {
    await flows.saveRoom(t.as(TEACHER), TEACHER, 'AL-301', GEOFENCE);
    await assertFails(setDoc(ref(t.as(user)), room(user)));
    await assertFails(getDoc(ref(t.as(user))));
  });

  test.each([
    ['another id than its name', 'AL302', {}],
    ['an id with punctuation', 'AL-301', {}],
    ['a lower-case id', 'al301', { name: 'al301' }],
    ['an empty name', 'X', { name: '' }],
    ['a name over 40 characters', 'A'.repeat(41), { name: 'A'.repeat(41) }],
    ['a radius under the minimum', 'AL301', { geofence: { ...GEOFENCE, radiusM: 10 } }],
    ['a geofence with extra fields', 'AL301', { geofence: { ...GEOFENCE, floor: 3 } }],
    ['a geofence that is not a map', 'AL301', { geofence: 'here' }],
    ['another teacher named as saving it', 'AL301', { savedBy: OTHER_TEACHER.uid }],
    ['a time from the phone', 'AL301', { savedAt: h.ago(0) }],
    ['an extra field', 'AL301', { floor: 3 }],
  ])('denied: a room with %s', async (_, id, change) => {
    await assertFails(setDoc(ref(t.as(TEACHER), id), room(TEACHER, change)));
  });
});
