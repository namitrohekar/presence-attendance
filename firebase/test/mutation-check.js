// Proves the suite has teeth: weakens one rule at a time and requires the
// matching test file to fail. Run with `npm run test:mutation`.

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const RULES_PATH = path.join(__dirname, '..', 'firestore.rules');
const original = fs.readFileSync(RULES_PATH, 'utf8');

// [what is weakened, test file that must catch it, text to find, replacement]
const MUTATIONS = [
  ['token validity widened to 21 s', 'attendance-student', 'function TOKEN_VALIDITY_S() { return 20; }', 'function TOKEN_VALIDITY_S() { return 21; }'],
  ['email verification not required', 'identity', '&& request.auth.token.email_verified == true', ''],
  ['role accepted as written by the client', 'identity', "&& data.role == (isAllowlisted() ? 'FACULTY' : 'STUDENT')", "&& data.role in ['FACULTY', 'STUDENT']"],
  ['course create open to non-faculty', 'courses', '&& isAllowlisted()\n', '\n'],
  ['course create without its join code', 'courses', '&& joinCodeAfter(request.resource.data.joinCode).courseId == courseId', ''],
  ['join code may name any teacher', 'courses', '&& data.teacherId == courseAfter(data.courseId).teacherId', ''],
  ['membership without resolving the join code', 'memberships', '&& get(/databases/$(database)/documents/joinCodes/$(request.resource.data.joinCode)).data.courseId\n            == request.resource.data.courseId', ''],
  ['membership with a client-chosen join time', 'memberships', '&& request.resource.data.joinedAt == request.time', ''],
  ['session start time chosen by the client', 'sessions', '&& request.resource.data.startedAt == request.time', ''],
  ['session maximum duration unbounded', 'sessions', "&& request.resource.data.endsAt\n            <= request.time + duration.value(SESSION_MAX_MIN() + SESSION_SKEW_MIN(), 'm')", ''],
  ['session end may extend the session', 'sessions', '(request.time < resource.data.endsAt && request.resource.data.endsAt == request.time)', '(request.resource.data.endsAt == request.time)'],
  ['token written for an ended session', 'tokens', "&& session().status == 'ACTIVE'", ''],
  ['token readable by signed-in users', 'tokens', 'allow create: if signedIn()\n          && isToken(token)', 'allow get: if signedIn();\n        allow create: if signedIn()\n          && isToken(token)'],
  ['attendance without membership', 'attendance-student', '&& isMemberOf(session.courseId, uid())\n          && data.markedAt', '&& data.markedAt'],
  ['attendance with a client-chosen mark time', 'attendance-student', '&& data.markedAt == request.time\n          && inGeofence', '&& inGeofence'],
  ['attendance without the geofence', 'geofence', '&& inGeofence(session.geofence, data.lat, data.lng, data.accuracyM)', ''],
  ['geofence boundary made exclusive', 'geofence', '&& dx * dx + dy * dy <= reach * reach', '&& dx * dx + dy * dy < reach * reach'],
  ['geofence ignores cosLat', 'geofence', ' * float(g.cosLat);', ';'],
  ['live mark for an ended session', 'attendance-student', "&& session.status == 'ACTIVE'\n          && request.time < session.endsAt", '&& request.time < session.endsAt'],
  ['live mark after the session end time', 'attendance-student', '&& request.time < session.endsAt\n          && request.time < validFrom', '&& request.time < validFrom'],
  ['extra fields allowed on a mark', 'attendance-student', 'return data.keys().hasOnly(fields) && data.keys().hasAll(fields);', 'return data.keys().hasAll(fields);'],
  ['queued scan window made inclusive', 'attendance-student', "&& data.scannedAt < validFrom + duration.value(TOKEN_VALIDITY_S(), 's')", "&& data.scannedAt <= validFrom + duration.value(TOKEN_VALIDITY_S(), 's')"],
  ['queued scan time may be in the future', 'attendance-student', '&& data.scannedAt <= request.time', ''],
  ['queued sync has no grace limit', 'attendance-student', "&& request.time < session.endsAt + duration.value(QUEUE_GRACE_MIN(), 'm')", ''],
  ['manual create for non-members', 'attendance-manual', '&& isMemberOf(session.courseId, data.studentId)', ''],
  ['manual correction without a note', 'attendance-manual', 'return isText(data.note, 1, NOTE_MAX_LEN())\n          && data.updatedBy', 'return data.updatedBy'],
  ['manual correction may change any field', 'attendance-manual', "&& request.resource.data.diff(resource.data).affectedKeys()\n              .hasOnly(['status', 'note', 'updatedBy', 'updatedAt'])", ''],
  ['attendance listable by any signed-in user', 'attendance-read', 'allow list: if signedIn()\n        && (resource.data.studentId == uid() || resource.data.teacherId == uid());\n\n      allow create: if signedIn()\n        && (studentMark', 'allow list: if signedIn();\n\n      allow create: if signedIn()\n        && (studentMark'],
];

function jestFails(file) {
  const run = spawnSync('npx', ['jest', '--runInBand', '--silent', `test/${file}.test.js`], {
    cwd: path.join(__dirname, '..'),
    encoding: 'utf8',
  });
  return run.status !== 0;
}

let survivors = 0;
try {
  for (const [what, file, find, replacement] of MUTATIONS) {
    if (original.split(find).length !== 2) {
      throw new Error(`Mutation "${what}" must match the rules exactly once.`);
    }
    fs.writeFileSync(RULES_PATH, original.replace(find, replacement));
    const caught = jestFails(file);
    survivors += caught ? 0 : 1;
    console.log(`${caught ? 'caught  ' : 'SURVIVED'}  ${what}  (${file}.test.js)`);
  }
} finally {
  fs.writeFileSync(RULES_PATH, original);
}

console.log(`\n${MUTATIONS.length - survivors} of ${MUTATIONS.length} weakened rules were caught.`);
process.exit(survivors === 0 ? 0 : 1);
