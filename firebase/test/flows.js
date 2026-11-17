// The Firestore transactions the Android app performs, written once so the
// rules tests can replay them exactly. Every document is built from
// contracts/documents.json, the same field lists the app's mappers are held
// to by MapperContractTest, so app, contract and rules cannot drift apart.
//
// Each function makes the same client-side checks as the app and resolves to
// the outcome the app would show. Tests that need to hit a rule directly
// write their own unchecked batches instead.

const fs = require('fs');
const path = require('path');
const { doc, runTransaction, serverTimestamp, setDoc, Timestamp } = require('firebase/firestore');
const { CONTRACTS_DIR } = require('./helpers');

const contract = JSON.parse(fs.readFileSync(path.join(CONTRACTS_DIR, 'documents.json'), 'utf8'));

/** A document with exactly the contract's fields for a shape, plus any optional ones given. */
function build(shape, values, optional = {}) {
  const out = {};
  for (const field of contract[shape].split(',')) {
    if (!(field in values)) {
      throw new Error(`${shape}.${field} is in the contract but the caller gave no value`);
    }
    out[field] = values[field];
  }
  const allowed = (contract[`${shape}.optional`] ?? '').split(',');
  for (const [field, value] of Object.entries(optional)) {
    if (!allowed.includes(field)) {
      throw new Error(`${shape}.${field} is not an optional field in the contract`);
    }
    out[field] = value;
  }
  return out;
}

const entryRef = (db, email) => doc(db, 'studentRegistry', email.toLowerCase());
const indexRef = (db, cohort, rollNo) => doc(db, 'rollIndex', `${cohort}_${rollNo}`);

/** The day part of an alert id, as the rules derive it from the server clock (UTC). */
function utcDay(date = new Date()) {
  return `${date.getUTCFullYear()}-${date.getUTCMonth() + 1}-${date.getUTCDate()}`;
}

/** The fixed id of the audit record for the next change to a roster entry. */
function correctionId(entry) {
  return `${entry.email}_${entry.addedAt.toMillis()}_${entry.revision + 1}`;
}

/**
 * Runs a transaction and, if the rules refuse it, runs it once more.
 *
 * When two clients race, the loser's write is evaluated against the winner's
 * committed data and is denied by the rules rather than retried by the SDK.
 * The denial says nothing about why, so the flow reads again: the second run
 * sees the new state and returns the precise outcome without writing.
 */
async function decideAgainIfDenied(db, body) {
  try {
    return await runTransaction(db, body);
  } catch (error) {
    if (error.code !== 'permission-denied') {
      throw error;
    }
    return runTransaction(db, body);
  }
}

/**
 * Sign-in: write the profile of an allowlisted teacher. The app proposes this
 * first; a denial means the account is not faculty.
 */
function writeFacultyProfile(db, user) {
  const ref = doc(db, 'users', user.uid);
  return runTransaction(db, async (tx) => {
    const identity = { name: user.name, email: user.email, role: 'FACULTY' };
    if ((await tx.get(ref)).exists()) {
      tx.update(ref, identity);
    } else {
      tx.set(ref, build('users', { ...identity, createdAt: serverTimestamp() }));
    }
  });
}

/**
 * Faculty add one student: roster entry and roll index together.
 * Resolves to ADDED, EMAIL_EXISTS or ROLL_TAKEN. Nothing is overwritten.
 */
function addStudent(db, teacher, who) {
  const entry = entryRef(db, who.email);
  const index = indexRef(db, who.cohort, who.rollNo);
  return decideAgainIfDenied(db, async (tx) => {
    if ((await tx.get(entry)).exists()) {
      return 'EMAIL_EXISTS';
    }
    if ((await tx.get(index)).exists()) {
      return 'ROLL_TAKEN';
    }
    tx.set(
      entry,
      build('studentRegistry', {
        email: who.email,
        rollNo: who.rollNo,
        cohort: who.cohort,
        addedBy: teacher.uid,
        addedAt: serverTimestamp(),
        revision: 0,
      }),
    );
    tx.set(index, build('rollIndex', { email: who.email }));
    return 'ADDED';
  });
}

/**
 * A student registers by entering their roll number once. The email is the
 * sign-in token's, never typed. Resolves to REGISTERED, NOT_ON_REGISTER,
 * DUPLICATE_CLAIM or ROLL_MISMATCH.
 */
function register(db, who, enteredRollNo) {
  const entry = entryRef(db, who.email);
  const profile = doc(db, 'users', who.uid);
  return decideAgainIfDenied(db, async (tx) => {
    const entrySnapshot = await tx.get(entry);
    if (!entrySnapshot.exists()) {
      return 'NOT_ON_REGISTER';
    }
    const roster = entrySnapshot.data();
    if (roster.registeredUid && roster.registeredUid !== who.uid) {
      return 'DUPLICATE_CLAIM';
    }
    if (roster.rollNo !== enteredRollNo) {
      return 'ROLL_MISMATCH';
    }
    const profileExists = (await tx.get(profile)).exists();
    if (!roster.registeredUid) {
      tx.update(
        entry,
        build('studentRegistry.claim', { registeredUid: who.uid, registeredAt: serverTimestamp() }),
      );
    }
    // The profile takes the roster's roll number and cohort, not what was typed.
    const fields = {
      name: who.name,
      email: who.email,
      role: 'STUDENT',
      rollNo: roster.rollNo,
      cohort: roster.cohort,
    };
    if (profileExists) {
      tx.update(profile, fields);
    } else {
      tx.set(profile, build('users.student', { ...fields, createdAt: serverTimestamp() }));
    }
    return 'REGISTERED';
  });
}

/** A registered student joins a course. Resolves to false when already a member. */
function joinCourse(db, who, info) {
  const ref = doc(db, 'memberships', `${info.courseId}_${who.uid}`);
  return runTransaction(db, async (tx) => {
    if ((await tx.get(ref)).exists()) {
      return false;
    }
    tx.set(
      ref,
      build('memberships', {
        courseId: info.courseId,
        teacherId: info.teacherId,
        studentId: who.uid,
        name: who.name,
        email: who.email,
        rollNo: who.rollNo,
        cohort: who.cohort,
        joinCode: info.code,
        joinedAt: serverTimestamp(),
      }),
    );
    return true;
  });
}

/**
 * Faculty change an existing roster entry, atomically with its audit record.
 * action is UPDATE (new rollNo and/or cohort), RELEASE (free the claim) or
 * REMOVE. Resolves to DONE, NOT_FOUND or ROLL_TAKEN.
 */
function correct(db, teacher, email, action, reason, change = {}) {
  const entry = entryRef(db, email);
  return runTransaction(db, async (tx) => {
    const snapshot = await tx.get(entry);
    if (!snapshot.exists()) {
      return 'NOT_FOUND';
    }
    const before = snapshot.data();
    const after = {
      rollNo: change.rollNo ?? before.rollNo,
      cohort: change.cohort ?? before.cohort,
    };
    const oldIndex = indexRef(db, before.cohort, before.rollNo);
    const newIndex = indexRef(db, after.cohort, after.rollNo);
    if (action === 'UPDATE' && (await tx.get(newIndex)).exists()) {
      return 'ROLL_TAKEN';
    }
    const optional = action === 'REMOVE' ? {} : { afterRollNo: after.rollNo, afterCohort: after.cohort };
    if (before.registeredUid) {
      optional.releasedUid = before.registeredUid;
    }
    tx.set(
      doc(db, 'registryCorrections', correctionId(before)),
      build(
        'registryCorrections',
        {
          email: before.email,
          action,
          reason,
          correctedBy: teacher.uid,
          correctedAt: serverTimestamp(),
          beforeRollNo: before.rollNo,
          beforeCohort: before.cohort,
        },
        optional,
      ),
    );
    if (action === 'REMOVE') {
      tx.delete(entry);
      tx.delete(oldIndex);
      return 'DONE';
    }
    // A full replace: the claim fields are dropped, so the student registers again.
    tx.set(
      entry,
      build('studentRegistry', {
        email: before.email,
        rollNo: after.rollNo,
        cohort: after.cohort,
        addedBy: before.addedBy,
        addedAt: before.addedAt,
        revision: before.revision + 1,
      }),
    );
    if (action === 'UPDATE') {
      tx.delete(oldIndex);
      tx.set(newIndex, build('rollIndex', { email: before.email }));
    }
    return 'DONE';
  });
}

/**
 * The account whose attempt was rejected reports it. Best effort in the app:
 * a failure here is ignored. `extra` may carry enteredRollNo.
 */
function reportAlert(db, who, type, audience, extra = {}) {
  const id = `${who.uid}_${type}_${utcDay()}`;
  return setDoc(
    doc(db, 'identityAlerts', id),
    build(
      'identityAlerts',
      {
        type,
        email: who.email.toLowerCase(),
        uid: who.uid,
        audience,
        createdAt: serverTimestamp(),
      },
      extra,
    ),
  );
}

/** FirestoreSessionRepository.startSession: the device picks the end, the server the start. */
function startSession(db, teacher, sessionId, courseId, durationMin, geofence) {
  const ref = doc(db, 'sessions', sessionId);
  return runTransaction(db, async (tx) => {
    tx.set(
      ref,
      build('sessions', {
        courseId,
        teacherId: teacher.uid,
        status: 'ACTIVE',
        startedAt: serverTimestamp(),
        endsAt: Timestamp.fromMillis(Date.now() + durationMin * 60 * 1000),
        geofence: build('sessions.geofence', geofence),
      }),
    );
  });
}

/** FirestoreSessionRepository.writeToken: a blind write in a transaction, so it never queues. */
function writeToken(db, sessionId, token) {
  const ref = doc(db, 'sessions', sessionId, 'tokens', token);
  return runTransaction(db, async (tx) => {
    tx.set(ref, build('tokens', { validFrom: serverTimestamp() }));
  });
}

/**
 * FirestoreSessionRepository.endSession. Ending early stamps the server time; closing an
 * expired session leaves the end alone. The app tries the form its own clock suggests and, if
 * the rules refuse it, the other one. Resolves to ENDED_EARLY, CLOSED or ALREADY_ENDED.
 */
async function endSession(db, sessionId) {
  const ref = doc(db, 'sessions', sessionId);
  const attempt = (followOwnClock) =>
    runTransaction(db, async (tx) => {
      const session = (await tx.get(ref)).data();
      if (session.status === 'ENDED') {
        return 'ALREADY_ENDED';
      }
      const stillRunning = Date.now() < session.endsAt.toMillis();
      if (stillRunning === followOwnClock) {
        tx.update(ref, { status: 'ENDED', endsAt: serverTimestamp() });
        return 'ENDED_EARLY';
      }
      tx.update(ref, { status: 'ENDED' });
      return 'CLOSED';
    });
  try {
    return await attempt(true);
  } catch (error) {
    if (error.code !== 'permission-denied') {
      throw error;
    }
    return attempt(false);
  }
}

/**
 * FirestoreAttendanceRepository.correct: a teacher marks a student present or absent by hand.
 * No read: a teacher cannot read a record that does not exist. hasRecord comes from the roster.
 * Resolves to true if something was written, false if there was nothing to change.
 */
function correctAttendance(db, teacher, courseId, sessionId, studentId, hasRecord, present, note) {
  if (!hasRecord && !present) {
    return Promise.resolve(false);
  }
  const ref = doc(db, 'attendance', `${sessionId}_${studentId}`);
  const correction = build('attendance.correction', {
    status: present ? 'MANUAL_PRESENT' : 'MANUAL_ABSENT',
    note,
    updatedBy: teacher.uid,
    updatedAt: serverTimestamp(),
  });
  return runTransaction(db, async (tx) => {
    if (hasRecord) {
      tx.update(ref, correction);
    } else {
      tx.set(
        ref,
        build('attendance.manual', {
          ...correction,
          sessionId,
          courseId,
          teacherId: teacher.uid,
          studentId,
          source: 'MANUAL',
          markedAt: serverTimestamp(),
        }),
      );
    }
    return true;
  });
}

/** The phone a fixture student marks from, unless a test says otherwise. */
const deviceOf = (uid) => `${uid}Phone0000000000000000`.slice(0, 22);

/**
 * FirestoreAttendanceRepository.write: creates the mark and claims the phone in one transaction.
 * Resolves to MARKED, ALREADY_MARKED, or DEVICE_USED when another student has already marked
 * this session from the same phone; rejects if the rules refuse it.
 */
function writeMark(db, studentId, sessionId, deviceId, document) {
  const ref = doc(db, 'attendance', `${sessionId}_${studentId}`);
  const claim = doc(db, 'sessions', sessionId, 'devices', deviceId);
  return runTransaction(db, async (tx) => {
    if ((await tx.get(ref)).exists()) {
      return 'ALREADY_MARKED';
    }
    const claimed = await tx.get(claim);
    if (claimed.exists() && claimed.data().studentId !== studentId) {
      return 'DEVICE_USED';
    }
    tx.set(ref, document);
    if (!claimed.exists()) {
      tx.set(claim, build('sessions.devices', { studentId }));
    }
    return 'MARKED';
  });
}

/** FirestoreAttendanceRepository.markLive: the student's mark, after the app's own checks. */
function markLive(db, student, session, token, fix, deviceId = deviceOf(student.uid)) {
  return writeMark(
    db,
    student.uid,
    session.id,
    deviceId,
    build('attendance.live', {
      sessionId: session.id,
      courseId: session.courseId,
      teacherId: session.teacherId,
      studentId: student.uid,
      status: 'PRESENT',
      source: 'LIVE',
      token,
      markedAt: serverTimestamp(),
      lat: fix.lat,
      lng: fix.lng,
      accuracyM: fix.accuracyM,
      deviceId,
    }),
  );
}

/** FirestoreAttendanceRepository.markQueued: a scan saved earlier, sent with its scan time. */
function markQueued(db, session, mark, deviceId = deviceOf(mark.studentId)) {
  return writeMark(
    db,
    mark.studentId,
    session.id,
    deviceId,
    build('attendance.queued', {
      sessionId: session.id,
      courseId: session.courseId,
      teacherId: session.teacherId,
      studentId: mark.studentId,
      status: 'LATE_SYNC',
      source: 'QUEUED',
      token: mark.token,
      markedAt: serverTimestamp(),
      scannedAt: Timestamp.fromMillis(mark.scannedAtMillis),
      lat: mark.lat,
      lng: mark.lng,
      accuracyM: mark.accuracyM,
      deviceId,
    }),
  );
}

module.exports = {
  markLive,
  correctAttendance,
  markQueued,
  startSession,
  writeToken,
  endSession,
  build,
  utcDay,
  correctionId,
  writeFacultyProfile,
  addStudent,
  register,
  joinCourse,
  correct,
  reportAlert,
};
