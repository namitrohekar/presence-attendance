// Proves the suite has teeth: weakens one rule at a time and requires the
// matching test file to fail. Run with `npm run test:mutation`.

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const RULES_PATH = path.join(__dirname, '..', 'firestore.rules');
const original = fs.readFileSync(RULES_PATH, 'utf8');

// [what is weakened, test file that must catch it, text to find, replacement]
const MUTATIONS = [
  // ---- identity and eligibility
  ['email verification not required', 'identity', '&& request.auth.token.email_verified == true', ''],
  ['any verified account may use the app', 'eligibility', 'return verified() && (hasStudentEmail() || isAllowlisted());', 'return verified();'],
  ['sub-domains and look-alike domains accepted', 'eligibility', "return email().split('@').size() == 2\n        && email().split('@')[1] == STUDENT_DOMAIN();", "return email().matches('.*' + STUDENT_DOMAIN() + '.*');"],
  ['a second @ in the address accepted', 'eligibility', "return email().split('@').size() == 2\n        && email()", 'return email()'],
  ['faculty role without the allowlist', 'identity', "&& data.role == 'FACULTY'\n          && isAllowlisted();", "&& data.role == 'FACULTY';"],

  // ---- registration
  ['student profile with any roll number', 'registration', '&& entry.rollNo == data.rollNo\n          && entry.cohort == data.cohort\n          && entry.registeredUid == uid();', '&& entry.cohort == data.cohort\n          && entry.registeredUid == uid();'],
  ['student profile with any cohort', 'registration', '&& entry.rollNo == data.rollNo\n          && entry.cohort == data.cohort\n          && entry.registeredUid == uid();', '&& entry.rollNo == data.rollNo\n          && entry.registeredUid == uid();'],
  ['student profile without claiming the roster entry', 'registration', '&& entry.cohort == data.cohort\n          && entry.registeredUid == uid();', '&& entry.cohort == data.cohort;'],
  ['allowlisted address may hold a student profile', 'registration', '&& hasStudentEmail()\n          && !isAllowlisted()\n          && entry.rollNo', '&& hasStudentEmail()\n          && entry.rollNo'],
  ['claim of an entry that is already claimed', 'registration', "&& !('registeredUid' in resource.data)\n", '\n'],
  ['claim of a classmate\'s entry', 'registration', 'return entryEmail == email()\n          && hasStudentEmail()', 'return hasStudentEmail()'],
  ['claim may also change the roll number', 'registration', ".hasOnly(['registeredUid', 'registeredAt'])", ".hasOnly(['registeredUid', 'registeredAt', 'rollNo', 'cohort'])"],
  ['claim on behalf of another sign-in', 'registration', '&& request.resource.data.registeredUid == uid()\n          && request.resource.data.registeredAt == request.time;', '&& request.resource.data.registeredAt == request.time;'],
  ['allowlisted address may claim a roster entry', 'registration', "&& hasStudentEmail()\n          && !isAllowlisted()\n          && !('registeredUid'", "&& hasStudentEmail()\n          && !('registeredUid'"],

  // ---- roster
  ['roster entry without its roll index', 'roster', '&& request.resource.data.revision == 0\n        && rollIndexAfter(request.resource.data.cohort, request.resource.data.rollNo).email\n            == entryEmail;', '&& request.resource.data.revision == 0;'],
  ['roll index that does not match its roster entry', 'roster', '&& key == rollIndexKey(\n            rosterEntryAfter(request.resource.data.email).cohort,\n            rosterEntryAfter(request.resource.data.email).rollNo);', ';'],
  ['roster address outside the student domain', 'roster', "&& v.split('@')[0].matches('^[a-z0-9._-]{1,64}$')\n        && v.split('@')[1] == STUDENT_DOMAIN();", "&& v.split('@')[0].matches('^[a-z0-9._-]{1,64}$');"],
  ['roster roll number of any length', 'roster', "return v is string && v.matches('^[0-9]{9}$');", "return v is string && v.matches('^[0-9]+$');"],
  ['roster edit without an audit record', 'roster', "&& record.correctedBy == uid()\n          && record.correctedAt == request.time\n          && ((record.action == 'UPDATE'\n                && changed", '&& ((changed'],
  ['old roll index left behind by a correction', 'roster', '\n                && !rollIndexExistsAfter(before.cohort, before.rollNo))', ')'],
  ['correction may keep the student\'s claim', 'roster', '&& after.keys().hasOnly(rosterBase().concat(rosterDetails()))\n          && after.keys().hasAll(rosterBase())', '&& after.keys().hasAll(rosterBase())'],
  ['correction may write an invalid name, batch or emoji', 'roster', '&& after.keys().hasAll(rosterBase())\n          && validDetails(after)\n', '&& after.keys().hasAll(rosterBase())\n'],
  ['roster entry without a name or emoji', 'roster', ".keys().hasAll(rosterBase().concat(['name', 'emoji']))", '.keys().hasAll(rosterBase())'],
  ['roster entry with unknown fields', 'roster', '&& request.resource.data.keys().hasOnly(rosterBase().concat(rosterDetails()))\n        && request.resource.data.keys().hasAll', '&& request.resource.data.keys().hasAll'],
  ['roster name of any shape', 'roster', "return (!('name' in data) || isName(data.name))\n        && ", 'return '],
  ['roster name with surrounding spaces', 'roster', 'return isText(v, 1, NAME_MAX_LEN()) && v.trim() == v;', 'return isText(v, 1, NAME_MAX_LEN());'],
  ['roster batch of any value', 'roster', "&& (!('batch' in data) || isBatch(data.batch))\n", '\n'],
  ['roster emoji of any value', 'roster', "\n        && (!('emoji' in data) || isEmoji(data.emoji));", ';'],
  ['students may edit roster details', 'roster', 'return isAllowlisted()\n          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(rosterDetails())', 'return request.resource.data.diff(resource.data).affectedKeys().hasOnly(rosterDetails())'],
  ['details edit may change the roll number', 'roster', '.affectedKeys().hasOnly(rosterDetails())', ".affectedKeys().hasOnly(rosterDetails().concat(['rollNo', 'cohort']))"],
  ['details edit may remove the name or emoji', 'roster', "&& request.resource.data.keys().hasAll(['name', 'emoji'])\n          && validDetails(request.resource.data);", '&& validDetails(request.resource.data);'],
  ['audit record may misstate the previous roll number', 'roster', '&& data.beforeRollNo == before.rollNo\n', '\n'],
  ['audit record may misstate the new roll number', 'roster', '&& data.afterRollNo == rosterEntryAfter(data.email).rollNo\n', '\n'],
  ['audit record without a reason', 'roster', '&& isText(data.reason, 1, NOTE_MAX_LEN())\n          && data.correctedBy', '&& data.correctedBy'],
  ['audit record with an arbitrary id', 'roster', '&& recordId == correctionId(data.email, before)\n', '\n'],
  ['audit record without a roster change', 'roster', '&& rosterEntryAfter(data.email).revision == before.revision + 1\n', '\n'],
  ['roster removal without an audit record', 'roster', "&& correctionAfter(entryEmail, resource.data).action == 'REMOVE'\n        && correctionAfter(entryEmail, resource.data).correctedBy == uid()\n        && correctionAfter(entryEmail, resource.data).correctedAt == request.time\n", '\n'],
  ['roster removal leaves its roll index', 'roster', "&& correctionAfter(entryEmail, resource.data).correctedAt == request.time\n        && !rollIndexExistsAfter(resource.data.cohort, resource.data.rollNo);", '&& correctionAfter(entryEmail, resource.data).correctedAt == request.time;'],
  ['audit records deletable inside the retention period', 'roster', "\n        && resource.data.correctedAt\n            < request.time - duration.value(CORRECTION_RETENTION_DAYS(), 'd');", ';'],
  ['students may read any roster entry', 'roster', 'allow get: if signedIn() && (entryEmail == email() || isAllowlisted());', 'allow get: if signedIn();'],
  ['students may list the roster', 'roster', 'allow list: if signedIn() && isAllowlisted();\n\n      // Lookups: 2 (allowlist, roll index after this write).', 'allow list: if signedIn();\n\n      // Lookups: 2 (allowlist, roll index after this write).'],
  ['students may read the roll index', 'roster', 'allow get: if signedIn() && isAllowlisted();\n\n      // Lookups: 2 (allowlist, roster entry after this write).', 'allow get: if signedIn();\n\n      // Lookups: 2 (allowlist, roster entry after this write).'],
  ['students may read the audit trail', 'roster', 'allow read: if signedIn() && isAllowlisted();\n\n      // Lookups: 3 (allowlist, roster entry before, roster entry after).', 'allow read: if signedIn();\n\n      // Lookups: 3 (allowlist, roster entry before, roster entry after).'],

  // ---- identity alerts
  ['alert id not tied to the server day', 'alerts', "&& alertId == uid() + '_' + request.resource.data.type + '_' + serverDay()\n", '\n'],
  ['alert carrying another uid', 'alerts', '&& request.resource.data.uid == uid()\n        && request.resource.data.email == email()', '&& request.resource.data.email == email()'],
  ['alert carrying another email', 'alerts', '&& request.resource.data.uid == uid()\n        && request.resource.data.email == email()', '&& request.resource.data.uid == uid()'],
  ['alert with a client-chosen time', 'alerts', "&& request.resource.data.createdAt == request.time\n        && ((request.resource.data.type == 'NON_VJTI_EMAIL'", "&& ((request.resource.data.type == 'NON_VJTI_EMAIL'"],
  ['non-VJTI alert from an eligible account', 'alerts', "&& data.audience == 'ALL'\n          && !hasStudentEmail()\n          && !isAllowlisted();", "&& data.audience == 'ALL';"],
  ['not-on-register alert from a student who is on it', 'alerts', '&& hasStudentEmail()\n          && !exists(/databases/$(database)/documents/studentRegistry/$(email()));', '&& hasStudentEmail();'],
  ['mismatch alert when the roll number was correct', 'alerts', '&& rosterEntry(email()).rollNo != data.enteredRollNo\n', '\n'],
  ['mismatch alert addressed to any audience', 'alerts', '&& rosterEntry(email()).rollNo != data.enteredRollNo\n          && data.audience == rosterEntry(email()).addedBy;', '&& rosterEntry(email()).rollNo != data.enteredRollNo;'],
  ['duplicate-claim alert when no other sign-in holds the claim', 'alerts', '&& rosterEntry(email()).registeredUid != uid()\n', '\n'],
  ['alert with extra fields', 'alerts', 'return hasExactly(data, attemptFields())\n          && isRollNo(data.enteredRollNo)\n          && hasStudentEmail()\n          && rosterEntry(email()).rollNo', 'return isRollNo(data.enteredRollNo)\n          && hasStudentEmail()\n          && rosterEntry(email()).rollNo'],
  ['students may read alerts', 'alerts', 'allow read: if signedIn() && isAllowlisted() && isAudience();', 'allow read: if signedIn() && isAudience();'],
  ['faculty may read alerts addressed to others', 'alerts', 'allow read: if signedIn() && isAllowlisted() && isAudience();', 'allow read: if signedIn() && isAllowlisted();'],
  ['alerts editable', 'alerts', "      // Retention: kept for at least ALERT_RETENTION_DAYS, then a faculty app\n      // may purge it. No update rule exists.", "      allow update: if signedIn();\n      // Retention: kept for at least ALERT_RETENTION_DAYS, then a faculty app\n      // may purge it. No update rule exists."],
  ['alerts deletable inside the retention period', 'alerts', "\n        && resource.data.createdAt\n            < request.time - duration.value(ALERT_RETENTION_DAYS(), 'd');", ';'],

  // ---- courses and memberships
  ['course create open to non-faculty', 'courses', "allow create: if signedIn()\n        && isAllowlisted()\n        && hasExactly(request.resource.data,\n            ['teacherId', 'name', 'code', 'threshold', 'joinCode', 'createdAt'])", "allow create: if signedIn()\n        && hasExactly(request.resource.data,\n            ['teacherId', 'name', 'code', 'threshold', 'joinCode', 'createdAt'])"],
  ['course create without its join code', 'courses', '&& joinCodeAfter(request.resource.data.joinCode).courseId == courseId', ''],
  ['join code may name any teacher', 'courses', '&& data.teacherId == courseAfter(data.courseId).teacherId', ''],
  ['membership without resolving the join code', 'memberships', '&& get(/databases/$(database)/documents/joinCodes/$(request.resource.data.joinCode)).data.courseId\n            == request.resource.data.courseId', ''],
  ['membership with a client-chosen join time', 'memberships', '&& request.resource.data.joinedAt == request.time', ''],
  ['membership with an unverified roll number', 'memberships', 'return entry.registeredUid == uid()\n          && entry.rollNo == data.rollNo\n          && entry.cohort == data.cohort;', 'return entry.registeredUid == uid()\n          && entry.cohort == data.cohort;'],
  ['membership with an unverified cohort', 'memberships', '&& entry.rollNo == data.rollNo\n          && entry.cohort == data.cohort;\n      }\n\n      // Lookups: 3 (join code, course, roster entry).', '&& entry.rollNo == data.rollNo;\n      }\n\n      // Lookups: 3 (join code, course, roster entry).'],
  ['membership without registering first', 'memberships', 'return entry.registeredUid == uid()\n          && entry.rollNo == data.rollNo', 'return entry.rollNo == data.rollNo'],

  // ---- sessions and tokens
  ['session start time chosen by the client', 'sessions', '&& request.resource.data.startedAt == request.time', ''],
  ['session maximum duration unbounded', 'sessions', "&& request.resource.data.endsAt\n            <= request.time + duration.value(SESSION_MAX_MIN() + SESSION_SKEW_MIN(), 'm')", ''],
  ['session end may extend the session', 'sessions', '(request.time < resource.data.endsAt && request.resource.data.endsAt == request.time)', '(request.resource.data.endsAt == request.time)'],
  ['token written for an ended session', 'tokens', "&& session().status == 'ACTIVE'", ''],
  ['token readable by signed-in users', 'tokens', 'allow create: if signedIn()\n          && isToken(token)', 'allow get: if signedIn();\n        allow create: if signedIn()\n          && isToken(token)'],

  // ---- attendance
  ['token validity widened to 21 s', 'attendance-student', 'function TOKEN_VALIDITY_S() { return 20; }', 'function TOKEN_VALIDITY_S() { return 21; }'],
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
  ['mark without a claim on its phone', 'devices', '&& deviceClaimAfter(data.sessionId, data.deviceId).studentId == uid()\n', '\n'],
  ['phone claim without its mark', 'devices', "&& request.resource.data.studentId == uid()\n          && getAfter(/databases/$(database)/documents/attendance/$(sessionId + '_' + uid())).data.deviceId\n              == deviceId;", '&& request.resource.data.studentId == uid();'],
  ['phone claim in another student\'s name', 'devices', "&& hasExactly(request.resource.data, ['studentId'])\n          && request.resource.data.studentId == uid()\n          && getAfter", "&& hasExactly(request.resource.data, ['studentId'])\n          && getAfter"],
  ['phone claim with extra fields', 'devices', "&& hasExactly(request.resource.data, ['studentId'])\n          && request.resource.data.studentId == uid()\n          && getAfter", '&& request.resource.data.studentId == uid()\n          && getAfter'],
  ['malformed phone id accepted', 'devices', "&& isToken(deviceId)\n          && hasExactly(request.resource.data, ['studentId'])", "&& hasExactly(request.resource.data, ['studentId'])"],
  ['phone claims listable', 'devices', "allow get: if signedIn();\n\n        // Lookups: 1 (the writer's own mark after the write).", "allow read: if signedIn();\n\n        // Lookups: 1 (the writer's own mark after the write)."],
  ['any record may be revoked', 'review', "\n          && (request.resource.data.status != 'REVOKED'\n              || resource.data.source in ['LIVE', 'QUEUED'])", ''],
  ['correction without its history entry', 'review', '\n          && historyWrittenAfter(request.resource.data.revision);', ';'],
  ['manual create without its history entry', 'review', '\n          && historyWrittenAfter(1);', ';'],
  ['correction revision may skip', 'review', "\n          && request.resource.data.revision == resource.data.get('revision', 0) + 1", ''],
  ['history entry with any previous status', 'review', "\n          && (exists(recordPath(data.recordId))\n              ? data.from == get(recordPath(data.recordId)).data.status\n              : data.from == 'NONE');", ';'],
  ['history entry with any new status', 'review', '\n          && data.to == after.status', ''],
  ['history entry with any note', 'review', '\n          && data.note == after.note', ''],
  ['history entry without a change to the record', 'review', '\n          && after.revision == data.revision', ''],
  ['history entry filed under any id', 'review', "\n          && changeId == data.recordId + '_' + string(data.revision)", ''],
  ['history entry by anyone', 'review', '\n          && data.by == uid()', ''],
  ['history readable by anyone', 'review', 'allow read: if signedIn()\n        && (resource.data.teacherId == uid() || resource.data.studentId == uid());\n\n      allow create: if signedIn() && validChange', 'allow read: if signedIn();\n\n      allow create: if signedIn() && validChange'],
  ['headcount while the session is running', 'review', "\n        && (resource.data.status == 'ENDED' || request.time >= resource.data.endsAt)", ''],
  ['headcount above the maximum', 'review', '\n        && request.resource.data.headcount <= HEADCOUNT_MAX();', ';'],
  ['headcount may come with other changes', 'review', ".hasOnly(['headcount'])", ".hasOnly(['headcount', 'endsAt', 'status'])"],
  ['headcount by any teacher', 'review', "\n        && resource.data.teacherId == uid()\n        && (resource.data.status == 'ENDED' || request.time", "\n        && (resource.data.status == 'ENDED' || request.time"],
  ['manual create for non-members', 'attendance-manual', '&& isMemberOf(session.courseId, data.studentId)', ''],
  ['manual correction without a note', 'attendance-manual', 'return isText(data.note, 1, NOTE_MAX_LEN())\n          && data.updatedBy', 'return data.updatedBy'],
  ['manual correction may change any field', 'attendance-manual', "&& request.resource.data.diff(resource.data).affectedKeys()\n              .hasOnly(['status', 'note', 'updatedBy', 'updatedAt', 'revision'])", ''],
  ['attendance listable by any signed-in user', 'attendance-read', 'allow list: if signedIn()\n        && (resource.data.studentId == uid() || resource.data.teacherId == uid());\n\n      allow create: if signedIn()\n        && (studentMark', 'allow list: if signedIn();\n\n      allow create: if signedIn()\n        && (studentMark'],
];

function jestFails(file) {
  const run = spawnSync('npx', ['jest', '--runInBand', '--silent', `test/${file}.test.js`], {
    cwd: path.join(__dirname, '..'),
    encoding: 'utf8',
  });
  return run.status !== 0;
}

// Optional argument: run only the mutations whose description contains it.
const only = process.argv[2];
const selected = MUTATIONS.filter(([what]) => !only || what.includes(only));

let survivors = 0;
try {
  for (const [what, file, find, replacement] of selected) {
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

console.log(`\n${selected.length - survivors} of ${selected.length} weakened rules were caught.`);
process.exit(survivors === 0 ? 0 : 1);
