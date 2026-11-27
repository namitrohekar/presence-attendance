# Presence

Attendance by scanning a QR code shown in class. The code changes every 12
seconds and a scan counts only from inside the classroom's geofence, so a
photo sent to a friend, or a scan from the hostel, does not work.

Android (Java 17, XML views) on Firebase's free Spark plan: Google sign-in,
Firestore and Security Rules. There is no server code. The security rules are
the only verifier and the server clock is the only clock.

## What works today

| Area | State |
|------|-------|
| Sign-in, faculty allowlist, official student domain only | built |
| Class roster (add, CSV import, audited corrections), student registration by roll number | built |
| Roster names, lab batches and emojis; CSV import by column names; names and emojis in faculty lists | built |
| Identity alerts to faculty for rejected attempts | built |
| Courses and join codes | built |
| Session with rotating QR, live roster, end session | built |
| Scan, geofence and mock-location checks, result | built |
| Offline queue with background sync | built |
| Session roster, manual correction with a reason | built |
| Percentages, student history, at-risk list, CSV export | built |
| One phone per student per session | built |
| Session summary, headcount, review flags, revoke as proxy with a penalty, change history | built |
| Month calendar against the requirement, per-student view for faculty | built |
| Threshold setting, low-attendance notification | not built |
| Polish pass, TalkBack pass, seed tool, remaining docs | not built |

"Built" means it passes the automated gates below.

## How it stays honest

- A mark is one document, `attendance/{sessionId}_{studentId}`. Its id makes a
  second mark for the same student and session impossible.
- The rules accept a mark only if: the writer is that student, enrolled in the
  course, the token they scanned was issued less than 20 seconds ago by the
  server's clock, the session is live, and their coordinates fall inside the
  session's geofence.
- One phone can mark only one student in a session: the mark is written
  together with a claim on the phone's id, and a claimed phone cannot be
  claimed again.
- Roles are never taken from the client. Faculty are an allowlist no client can
  read; a student is an address in the official domain that faculty have put
  on the roster, bound once to one sign-in.
- Every value the rules depend on lives in `contracts/constants.json`. Tests
  fail if the rules file or `Constants.java` drifts from it.
- Each rule has tests for what it allows and what it refuses, and a mutation
  check weakens the rules one at a time and expects a test to catch each.

## Layout

    app/                      Android app, package com.presence.attendance
      core/                   container, errors, executors, shared UI
      auth/ course/ roster/   sign-in, courses, class roster and alerts
      session/ scan/          faculty QR side, student scanner side
      attendance/             marks, offline queue, reports
    contracts/                constants, document shapes, geofence vectors
    firebase/                 firestore.rules, indexes, rules tests
    docs/licenses/            the Inter font's licence

Each feature has `domain` (plain Java, unit-tested), `data` (Firestore or Room
behind an interface) and `ui` (fragment plus ViewModel). ViewModels take
interfaces in their constructors and are tested against in-memory fakes.

## Build and test

    ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug assembleRelease
    cd firebase && npm install && npm test
    cd firebase && npm run test:mutation

Last run: 289 unit tests, 666 rules tests (one of them 60 students marking at
once), 107 of 107 weakened rules caught.
Lint and javac treat warnings as errors.

To run the app you need `app/google-services.json` from the Firebase project
(not in git) and the rules deployed: `cd firebase && npm run deploy`.
