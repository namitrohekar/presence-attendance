// Feature 5: geofence verification. Runs every vector in
// contracts/geofence-vectors.json through the rules. GeofenceTest.java runs
// the same file through the app's math, so the two cannot disagree silently.

const fs = require('fs');
const path = require('path');
const { doc, setDoc } = require('firebase/firestore');
const h = require('./helpers');

const { vectors } = JSON.parse(
  fs.readFileSync(path.join(h.CONTRACTS_DIR, 'geofence-vectors.json'), 'utf8'),
);
const t = h.setup();

describe('geofence shared vectors (rules)', () => {
  test('the vector file covers both outcomes', () => {
    expect(vectors.filter((v) => v.pass).length).toBeGreaterThanOrEqual(10);
    expect(vectors.filter((v) => !v.pass).length).toBeGreaterThanOrEqual(10);
  });

  test.each(vectors.map((v) => [v.pass ? 'allows' : 'denies', v.name, v]))(
    '%s: %s',
    async (_, __, v) => {
      const geofence = { lat: v.lat0, lng: v.lng0, radiusM: v.radiusM, cosLat: v.cosLat0 };
      await t.seed({ [`sessions/${h.SESSION}`]: h.sessionDoc({ geofence }) });
      const mark = h.liveMark(h.STUDENT, { lat: v.lat, lng: v.lng, accuracyM: v.accuracyM });
      const write = setDoc(doc(t.as(h.STUDENT), 'attendance', h.attendanceId()), mark);
      await (v.pass ? h.assertSucceeds(write) : h.assertFails(write));
    },
  );
});
