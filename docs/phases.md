# Delivery phases

One phase at a time. A phase is done when its automated gate is green and the
manual run-checks pass on your device. The next phase starts only after you
say so.

Standing gate, run at the end of every phase:

    ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug assembleRelease

From Phase 1 onward, also `npm test` in `firebase/`.

Status: Phases 0 to 5 and the identity extension (Stages A to D) are built and
pass the automated gates, plus most of Phase 6 (session roster, manual
correction, percentages, student history, at-risk list; not yet: threshold
setting, CSV export, low-attendance notification). Phases 0 to 2 were reviewed on your device. The rest
is waiting for your run: follow `docs/testing-guide.md`.

| Phase | Delivers | Demo steps unlocked |
|-------|----------|---------------------|
| 0 | Scaffold, design tokens, build tooling | none |
| 1 | Contracts, security rules, rules tests | 6 |
| 2 | Auth, roles, courses | 1 |
| 3 | Session, rotating QR, live roster | 2 |
| 4 | Scan, geofence, submit, result | 3, 4, 5 |
| 5 | Offline queue | 7 |
| 6 | Reports, alerts, manual correction, CSV | 8, 9 |
| 7 | Polish, accessibility, docs, seed, audit | full run |

## Phase 0: scaffold, design tokens, build tooling

Delivers: Java 17 + XML project (`com.presence.attendance`, app name
"Presence"), version catalog, Spotless (google-java-format AOSP), Lint
warnings-as-errors, R8 release build, conditional Google services plugin,
Safe Args, colour/type/spacing/radius tokens for light and dark, bundled Inter,
button styles with spring press, adaptive icon, splash, edge-to-edge single
activity, sign-in screen layout, `docs/decisions.md`.

You need to provide: nothing.

Automated gate: standing gate green with no `google-services.json` present.

Run-checks:
1. App installs as "Presence" with a flat blue icon; splash shows the icon only.
2. Sign-in screen shows the wordmark, one statement and "Continue with Google".
3. Tapping the button shows the inline "no Firebase configuration" message
   (sign-in itself is Phase 2).
4. Dark mode switches every colour; no purple Material defaults anywhere.
5. Landscape: nothing clipped, content column capped and centred.
6. Font size at maximum: text wraps, the screen scrolls, nothing is cut off.
7. Gesture and 3-button navigation: content clears the system bars.

## Phase 1: contracts, security rules, rules tests

Delivers: `contracts/constants.json`, `contracts/geofence-vectors.json`,
`firebase/firestore.rules`, `firestore.indexes.json`, `firebase.json`, Jest
rules suite on the Firestore emulator, `Constants.java`, pure-Java geofence
math, constants-parity and shared-vector JUnit tests.

You need to provide: nothing (the emulator runs locally; needs a JDK, present).

Automated gate:
- `npm test` green, with at least one positive and one negative test for each
  of the 8 rules invariants, plus: token age 19.9 s / 20.1 s, geofence edge,
  wrong role, non-member, closed session, duplicate, field-whitelist violation.
- JUnit: geofence passes every shared vector; constants parity test fails if
  `constants.json`, the rules file and `Constants.java` disagree (proved by
  changing one value and watching it fail).
- No rule path uses more than 4 `get()`/`exists()` calls (counted in the report).
- `npm run test:mutation`: every deliberately weakened rule is caught by a test.

Run-checks:
1. `cd firebase && npm install && npm test` prints the per-invariant test list,
   all passing.
2. `npm run test:mutation` reports every weakened rule as caught.
3. Change `ROTATION_S` in `contracts/constants.json`; `./gradlew testDebugUnitTest`
   fails. Change it back.

## Phase 2: auth, roles, courses

Delivers: Google sign-in via Credential Manager, role derivation, AppContainer,
AppError mapping, role-specific home, course create with unique join code,
join by code with confirm sheet, sign-out, connectivity gating, debug-only
emulator host config, `contracts/documents.json`.

Changed from the original plan: `joinCodes/{code}` gained a `teacherId` field
(D-017), so the rules must be redeployed. AppExecutors moves to the first phase
that does background work (nothing in this phase leaves the main thread except
inside the Firebase SDK).

You need to provide before review: rules redeployed (`cd firebase && npm run
deploy`), and both Google accounts added to the test device (Settings >
Passwords and accounts > Add account). An emulator without a Google account
opens Google's add-account flow instead of the account sheet.

Automated gate: standing gate + `npm test` + `npm run test:mutation`; JUnit for
join codes, AppError mapping, the four ViewModels and the document mappers.

Run-checks (demo step 1):
1. Allowlisted account lands on the faculty home ("+" in the toolbar); the
   other account on the student home ("Join" in the toolbar).
2. Faculty creates a course; a 6-character join code is shown, and again on the
   course row.
3. Student enters the code, sees course and teacher on the confirm step, joins;
   the course appears on the student home.
4. Wrong code shows "No course uses that code". Joining twice shows "You've
   already joined this course" and does not duplicate.
5. Airplane mode: create and join are disabled with an explanation.
6. Account menu > Sign out returns to sign-in; reopening the app while signed
   in goes straight to the courses.
7. In the Firebase console: `users/{uid}` has role FACULTY for the allowlisted
   account and STUDENT for the other.

## Phase 2 extension: student identity

A security-sensitive extension of Phase 2, in five stages, each reviewed
before the next. Phase 3 starts only after Stage E is verified. Design in
`docs/decisions.md`, D-023 to D-030.

Every stage ends with: `npm test`, `npm run test:mutation`, JUnit, and the
standing Gradle gate.

**Stage A: rules and contracts.** No app screens. Not deployed.
- Student domain or allowlist on every rule; allowlist precedence.
- Roster (`studentRegistry`, `rollIndex`), audited corrections, registration
  claim, roster-verified memberships, identity alerts, retention.
- Review: run the two rules suites. The transactions the app will perform are
  in `firebase/test/flows.js`.

**Stage B: roster (faculty).** Roster screen, Add Student, CSV import
(`email,rollNo,cohort`) with full-file validation, preview and per-row result,
correction and removal with a required reason.
- Review: add the demo student in-app (`262010016`, `MC26`); import a CSV with
  a duplicate, a wrong domain and a malformed row and see each rejected.

**Stage C: student registration.** FACULTY-first sign-in, registration screen
(verified address shown, roll number entered once), join without a roll field,
alert written after each rejected attempt. Deploy the rules after this stage.
- Review: a Gmail account sees "Please sign in using your official VJTI
  student ID."; a wrong roll number is refused; the right one registers.

**Stage D: faculty alerts.** Alerts screen with a live listener and unread
count, retention purge, optional background check with a local notification
labelled best-effort.

**Stage E: migration and verification.** Redeploy rules and indexes; delete
the one pre-roster membership in the console; register and re-join; recheck
the alerts query on production. Faculty account, course and join code are
untouched.

## Phase 3: session, rotating QR, live roster

Delivers: start-session sheet, token writer (12 s from ack, one write in
flight), QR rendering, countdown ring, brightness and keep-screen-on, live
roster listener, end-session confirmation, resume after process death.

You need to provide: `appConfig/campus` document (seed script placeholder is fine).

Automated gate: standing gate + `npm test`; JUnit for token generator, QR
payload build/parse, session state machine, token-writer scheduling, session
ViewModel.

Run-checks (demo step 2):
1. Start a session with "Campus" and with "Here"; the QR appears only after the
   first token is acknowledged.
2. The QR changes every 12 s with no flicker; the ring counts down.
3. Airplane mode on the faculty phone: "Reconnecting…", QR blanks within 20 s,
   recovers when back online.
4. Kill the app and reopen: the live session resumes.
5. End session asks for confirmation; afterwards the QR is gone and the course
   no longer shows "Live".
6. A generic QR reader decodes `prs1:<sessionId>:<22-char token>`.

## Phase 4: scan, geofence, submit, result

Delivers: scanner (CameraX + ML Kit), location acquisition and mock rejection,
permission flows, client pre-checks, LIVE transaction with 8 s budget, result
screen with drawn check and haptics.

You need to provide: two devices (or one phone plus an emulator).

Automated gate: standing gate + `npm test`; JUnit for location-fix selection,
pre-check ordering and messages, scan ViewModel.

Run-checks (demo steps 3, 4, 5):
1. Student inside the geofence scans: "Attendance marked", check animation,
   haptic; the faculty roster updates without refresh.
2. Scanning again: already-marked message, no second record.
3. Photo of an old QR after 20 s: "This QR has expired. Scan the current one."
4. Outside the radius: "You're outside the allowed area".
5. Mock location app enabled: rejected with a specific message.
6. Approximate-only location permission: blocked with explanation and settings link.
7. Non-member and ended session each show their own message.

## Phase 5: offline queue

Delivers: Room `PendingMark`, queue state machine, WorkManager sync with
backoff, idempotent reconcile, queue states in history, dismiss.

Automated gate: standing gate + `npm test`; JUnit for the queue state machine
and reconcile against a fake repository.

Run-checks (demo step 7):
1. Airplane mode, scan: "Saved. It will sync when you're back online."
2. Reconnect: the item syncs without opening the app screen again; the faculty
   roster shows the student with "Late sync".
3. Queue a scan, let the session pass `endsAt + 30 min` (or use a seeded short
   case): the item shows Expired and stays until dismissed.
4. Kill the app while offline with a pending item; it still syncs after reconnect.

## Phase 6: reports, alerts, manual correction, CSV

Delivers: student course screen (ring, history by month), faculty Sessions |
Students, session roster, correction sheet, at-risk list, threshold setting,
low-attendance notification with daily refresh, CSV export and share.

Automated gate: standing gate + `npm test`; JUnit for attendance percentage,
CSV builder (quotes, commas, newlines, BOM, formula neutralisation),
notification dedupe, report ViewModels.

Run-checks (demo steps 8, 9):
1. Faculty marks a student present/absent with a note; a blank note is refused.
2. Percentages match hand calculation on a seeded course; a course with no
   sessions shows "No sessions yet".
3. At-risk list contains exactly the students below the threshold; changing the
   threshold updates it.
4. Student below threshold gets one notification; a second refresh the same day
   does not repeat it. Permission is requested in context, not at launch.
5. Exported CSV opens in Sheets and Excel with correct columns and no formula
   execution for a name starting with `=`.

## Phase 7: polish, accessibility, docs, seed, audit

Delivers: transitions, remaining empty/error states, TalkBack pass, README,
`docs/` set (setup, architecture, threat-model, design-system, test-matrix,
demo-runbook, audit), `tools/seed/`, unused-resource lint re-enabled.

Automated gate: standing gate + `npm test`; `docs/test-matrix.md` and
`docs/audit.md` list a proving test for every invariant and feature.

Run-checks: the full 9-step demo scenario on two physical phones, once with
gesture navigation and once with 3-button navigation, plus TalkBack through
sign-in, scan and result.
