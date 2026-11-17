# Decisions

Each entry: context, decision, alternatives, consequence. Newest last.

## D-001 Scan with CameraX + ML Kit, not ZXing

- Context: the submitted proposal lists ZXing for scanning. The usual Android
  wrapper, zxing-android-embedded, is built on the deprecated Camera1 API.
- Decision: scan with CameraX `ImageAnalysis` and ML Kit barcode-scanning
  (bundled model). ZXing `core` is kept for QR generation only.
- Alternatives: zxing-android-embedded (deprecated camera stack); ZXing core
  decoding fed by CameraX frames (more code, weaker detection than ML Kit).
- Consequence: a deviation from the proposal, to be stated in the viva. The
  bundled model adds APK size but needs no download on first scan.

## D-002 Delivery in reviewed phases

- Context: the build is reviewed by running it, not by reading it.
- Decision: eight phases in `docs/phases.md`, each with an automated gate and
  manual run-checks. Work on a phase starts only after the previous one is
  accepted.
- Alternatives: build all stages in one pass.
- Consequence: dependencies and components are added in the phase that first
  uses them; the version catalog already lists every allowed library.

## D-003 Toolchain versions

- Context: versions must be real and current. Checked against Google Maven and
  Maven Central on 2026-11-11.
- Decision: keep the Android Studio template's AGP 9.4.1 and Gradle 9.6.0;
  compileSdk and targetSdk 37; library versions as in `gradle/libs.versions.toml`.
  Room stays on 2.8.x (`androidx.room`), which supports Java annotation
  processing.
- Alternatives: AGP 9.5.1 (newest, but not what the installed Android Studio
  generated, so IDE sync support is unconfirmed).
- Consequence: upgrading AGP is a one-line change once the team's Studio
  supports it.

## D-004 Java 17 language level on the Gradle daemon JDK

- Context: all code must be Java 17. The template pins the Gradle daemon to
  JDK 25 through `gradle/gradle-daemon-jvm.properties` (auto-provisioned).
- Decision: `sourceCompatibility`/`targetCompatibility` 17, compiled by the
  daemon JDK with `-Xlint:all -Werror`. `dangling-doc-comments` is excluded
  because AGP's generated `BuildConfig.java` triggers it.
- Alternatives: a separate Java 17 toolchain (second JDK download per machine
  for no change in output).
- Consequence: Java 18+ syntax fails to compile. Zero-warning builds are
  enforced, including on generated Safe Args and ViewBinding code.

## D-005 Lint scope

- Context: the brief asks for warnings-as-errors on correctness and security.
  Lint has no per-category switch in the Gradle DSL.
- Decision: `warningsAsErrors = true` for every check, minus
  `GradleDependency`, `NewerVersionAvailable` and `AndroidGradlePluginVersion`
  (they start failing whenever a library ships a release), and minus
  `UnusedResources` until Phase 7 (tokens are defined before the screens that
  use them).
- Alternatives: enumerate correctness and security check IDs by hand (drifts
  with every lint release).
- Consequence: stricter than requested. `UnusedResources` is re-enabled in the
  Phase 7 audit.

## D-006 Contrast: text variants of semantic colours

- Context: the palette must meet WCAG AA on every pairing. Measured: accent
  #007AFF on white is 4.0:1; danger #FF3B30 on #F2F2F7 is about 3.2:1; success
  and warning are lower. All fail the 4.5:1 required for body-size text.
- Decision: semantic and accent colours are used as fills, icons, rings and for
  text at 17sp semibold or larger (3:1 applies). Smaller status text uses a
  darker text variant: `danger_text` #D70015 light / #FF6961 dark. Further
  `*_text` variants are added when a phase first needs them. Badges put
  primary text colour on a tinted semantic fill.
- Alternatives: darken the palette itself (departs from the specified tokens).
- Consequence: one extra token per semantic colour that appears as small text.

## D-007 Google sign-in button follows Google's branding, not the tokens

- Context: Google's guidelines fix the button's fill, stroke, text colour, logo
  and font (Roboto Medium 14/20).
- Decision: a dedicated `Widget.Presence.Button.Google` style with Google's
  light and dark values, unmodified four-colour "G", app corner radius and
  52 dp height (scaling and rounded corners are permitted).
- Alternatives: accent-filled primary button with a white "G" (not permitted).
- Consequence: the only place Roboto and non-token colours appear.

## D-008 Inter as static files

- Context: Inter must be bundled in weights 400 and 600.
- Decision: static `Inter-Regular.ttf` and `Inter-SemiBold.ttf` from the
  official rsms/inter v4.1 release; licence in `docs/licenses/Inter-OFL.txt`.
  Fonts are applied only through text appearances: a theme-level `fontFamily`
  outranks text appearances and flattened semibold styles to regular.
- Alternatives: the variable font with `fontVariationSettings` (one file, but
  axis handling varies across OEM builds).
- Consequence: about 830 KB of fonts in the APK.

## D-009 Names

- Context: the repository folder is `Cadence`; the product is "Presence".
- Decision: Gradle root project, application ID (`com.presence.attendance`) and
  app label use Presence. The folder name is left alone.
- Alternatives: rename the folder (breaks the open Android Studio project).
- Consequence: none at runtime.

## D-010 `google-services.json` and the shared debug keystore

- Context: Google sign-in only works for builds signed with a key whose
  SHA-1/SHA-256 is registered in Firebase, and the app needs the project's
  `google-services.json` at runtime.
- Decision: commit one debug keystore (`keystore/debug.keystore`, standard
  debug credentials) and sign every debug build with it. Keep
  `app/google-services.json` out of git; teammates receive it directly. The
  Firebase project ID lives in `firebase/.firebaserc`.
- Alternatives: per-developer debug keys (each must be registered); committing
  `google-services.json` (not a secret, but a public repository triggers
  exposed-key alerts from Google).
- Consequence: a fresh clone builds, lints and tests, and shows "no Firebase
  configuration" on sign-in until the file is added. Release signing is not
  configured; release builds are unsigned.

## D-011 Ending a session never moves its end time later

- Context: the brief says the only session update is ACTIVE to ENDED with
  `endsAt == request.time`. Applied to a session whose end time has already
  passed, that would push `endsAt` forward and reopen the window in which
  queued scans are accepted.
- Decision: two forms of the same transition. Before `endsAt`: status ENDED and
  `endsAt == request.time` (ending early). At or after `endsAt`: status ENDED
  with `endsAt` unchanged (closing an expired session). Token writes also
  require `request.time < endsAt`, not only status ACTIVE.
- Alternatives: the literal rule (lets a teacher extend an expired session);
  forbidding any update after expiry (leaves stale ACTIVE documents forever).
- Consequence: `endsAt` only ever decreases. Clients must treat "ACTIVE and past
  `endsAt`" as ended, since nothing flips the status automatically.

## D-012 Role is derived in the rules on every write

- Context: role must never be client-chosen, and clients cannot read the
  allowlist.
- Decision: the rules compute the role from `exists(teacherAllowlist/<email>)`
  wherever it matters (profile create and update, course create) and never
  trust `users/{uid}.role`. The profile's `name` and `email` must equal the
  sign-in token's claims. The email is lower-cased for the allowlist lookup.
- Alternatives: read `users/{uid}.role` in other rules (one more lookup, and a
  stale role after an allowlist change).
- Consequence: allowlist document ids must be lower-case. `users.role` is a
  display hint for choosing the home screen; removing an allowlist entry takes
  effect immediately for writes.

## D-013 Own composite ids can be read before they exist

- Context: the offline queue reconciles by reading
  `attendance/{sessionId}_{uid}`, and the scan pre-check reads
  `memberships/{courseId}_{uid}`. A rule written against `resource.data` denies
  a read of a missing document, which is indistinguishable from a real denial.
- Decision: `get` on memberships and attendance is allowed when the document id
  ends in `_<own uid>`, so a missing document returns "not found". Create rules
  guarantee such ids only ever hold that user's data.
- Alternatives: treat PERMISSION_DENIED as "not found" in the client (hides
  genuine denials).
- Consequence: precise client messages and a reliable reconcile.

## D-014 Join code carries the course name and must follow a rename

- Context: the join confirm sheet shows course and teacher before the student
  is a member, so that data is denormalised on `joinCodes/{code}`.
- Decision: a course rename is only accepted in a batch that also updates
  `joinCodes/{code}.courseName`; the rules check both documents after the write.
- Alternatives: let the join code go stale (the sheet would show the old name).
- Consequence: renames are a two-document batch. `teacherName` is the teacher's
  token name at creation and is not refreshed.

## D-015 Rules tooling beyond the three named packages

- Context: allowed Node tooling is firebase-tools, @firebase/rules-unit-testing
  and Jest.
- Decision: also install `firebase` (the JS SDK), which rules-unit-testing
  declares as a required peer dependency. Add `npm run test:mutation`, a script
  that weakens 27 rules one at a time and requires a test to fail for each.
- Alternatives: none for the peer dependency. Without the mutation check there
  is no evidence that the negative tests fail for the right reason; it already
  found two tests that passed only because another condition masked the one
  under test.
- Consequence: dev-only; nothing ships in the app.

## D-016 Contract files are parsed with a small regex reader in JUnit

- Context: JUnit must read `contracts/*.json`, and the allowed test libraries
  include no JSON parser (`org.json` is a stub in local unit tests).
- Decision: `testutil/Contracts.java` reads flat objects of scalars with two
  regular expressions. The contract files are kept flat for this reason.
- Alternatives: add `org.json:json` as a test dependency.
- Consequence: if a contract ever needs nesting, add the library and delete the
  reader.

## D-017 Join codes carry `teacherId` (contract change)

- Context: a membership must store the course's `teacherId`, and the rules
  check it against the course. But a student cannot read a course before
  joining it, and the specified join code document
  (`courseId, courseName, teacherName`) does not contain the teacher's id. As
  specified, no client could ever write a valid membership.
- Decision: `joinCodes/{code}` is `{courseId, courseName, teacherName,
  teacherId}`. The rules require `teacherId` to equal the course's teacher on
  create and update.
- Alternatives: let any signed-in user read any course (weaker: exposes the
  join code and threshold to anyone who learns a course id).
- Consequence: the only change to a Phase 1 rule. A uid is revealed to people
  who already hold the join code. Covered by three new rules tests and one new
  mutation; the rules must be redeployed.

## D-018 The app discovers its role by asking the rules

- Context: role is derived from an allowlist no client can read, so the app
  cannot know which role to write into the profile.
- Decision: at sign-in the app proposes a role in a transaction: the stored
  role, or STUDENT for a new profile. If the rules deny it, it proposes the
  other role. A second denial means the account is not allowed and the user is
  signed out again. The home screen is chosen from the accepted role.
- Alternatives: a callable function (no server allowed); letting clients read
  their own allowlist entry (leaks which emails are faculty by probing).
- Consequence: a faculty member's first sign-in costs one denied write. The
  role is refreshed at sign-in, not while the app stays signed in, so an
  allowlist change needs a sign-out and sign-in to change the home screen.
  Writes are unaffected: the rules re-derive the role every time.

## D-019 `contracts/documents.json`

- Context: the rules whitelist each document's exact fields, and the Android
  mappers build those documents by hand. A mismatch only shows at runtime as a
  bare PERMISSION_DENIED.
- Decision: a third contract file lists the fields of every document the app
  writes. JUnit holds the Java mappers to it; the rules suite builds its
  app-flow documents from it and replays the app's transactions.
- Alternatives: instrumented tests against the emulators (no instrumentation
  libraries are in the allowed list).
- Consequence: Java, contract and rules cannot drift apart silently. Each later
  phase adds its documents to the file.

## D-020 One home fragment for both roles

- Context: faculty and students must see different home screens.
- Decision: one `HomeFragment` and `HomeViewModel`, configured by role: toolbar
  action ("+" or "Join"), which sheet opens, which courses load, what a row
  shows (the join code only for faculty), and the empty-state copy.
- Alternatives: two fragments sharing a layout and adapter.
- Consequence: less duplication now. Split if later phases make the two diverge
  further (the student's sticky "Scan QR", the faculty "Live" indicator).

## D-021 Debug emulator support

- Context: debug builds should be able to use local Firebase emulators,
  including from a phone over the LAN, without a committed address or any
  cleartext allowance in release.
- Decision: `firebase.emulator.host` in `local.properties` becomes
  `BuildConfig.FIREBASE_EMULATOR_HOST` in debug builds only (always empty in
  release). A debug-only manifest adds a network security config that permits
  cleartext. `npm run emulators` starts Auth and Firestore.
- Alternatives: a fixed `10.0.2.2` entry (works only on the Android emulator).
- Consequence: `firebase.json` binds the emulators to 127.0.0.1, which the
  Android emulator reaches as `10.0.2.2`. For a phone over the LAN, change the
  two `host` values to `0.0.0.0` locally. Verified: the release APK contains no
  network security config. Not verified: an app session against the emulators,
  which still needs a real Google account on the device.

## D-022 Libraries: one transitive use, one unused allowance

- Context: every library outside the allowed list needs a justification.
- Decision: `CoordinatorLayout` (for the collapsing large title) is used
  through Material Components, which depends on it; no new dependency is
  declared. Mockito is allowed but not added: hand-written fakes of the two
  repository interfaces are shorter and clearer than stubbing them.
- Alternatives: declare `androidx.coordinatorlayout` explicitly.
- Consequence: none.

## D-023 Students must use the VJTI student domain; faculty are allowlisted

- Context: Presence is institution-specific. Until now any Google account
  could become a student, and the optional `INSTITUTE_DOMAIN` constant would
  have applied to faculty as well.
- Decision: an account may use the app only if its verified address is on the
  faculty allowlist or is in exactly `mc.vjti.ac.in` (`STUDENT_DOMAIN`, in the
  contract). The match is on the text after a single `@`, lower-cased, so
  sub-domains, look-alikes and a second `@` fail. Where the role matters the
  allowlist takes precedence: an allowlisted address is FACULTY and can
  neither claim a roster entry nor hold a student profile.
- Alternatives: a hosted-domain hint on the Google account sheet (client-side
  only, and faculty use other domains).
- Consequence: `INSTITUTE_DOMAIN` is gone. Faculty on other domains cost one
  allowlist lookup per request, since the cheap domain test fails for them.
  The lookup budget is now at most 4 per request (roster corrections).

## D-024 A faculty-managed roster is the authority for roll numbers

- Context: a typed email and roll number cannot be assumed to belong together,
  and the address does not encode the roll number.
- Decision: `studentRegistry/{email}` maps an official address to one roll
  number and a faculty-supplied cohort. `rollIndex/{cohort}_{rollNo}` makes a
  roll number unique within its cohort. Only allowlisted faculty write them,
  always as a pair in one write, and the roster is shared by all faculty. A
  roll number is exactly 9 digits and is treated as opaque: the app never
  decodes or shows the year, programme or gender digits it contains. Students
  can read their own entry and nothing else; nobody can list the roll index.
- Alternatives: first-come self-registration (a student could take someone
  else's number); deriving the cohort from the address suffix (rejected by the
  project owner: faculty supply it).
- Consequence: a student cannot register until faculty have added them. One
  email has one roll number because the address is the document id; a second
  write to an address or to a roll key is an update, which is denied, so
  nothing is silently overwritten.

## D-025 Registration is a claim plus a profile that must equal the roster

- Context: a student enters their roll number once. The address must come
  from the sign-in token, never from input.
- Decision: registration is one transaction: the student binds the roster
  entry for their token's address to their sign-in (`registeredUid`), once,
  and writes a profile whose `rollNo` and `cohort` equal the roster's. The
  rules compare the profile with the roster on every profile write, so the
  stored roll number can only change if faculty correct the roster first.
  Memberships take the roll number and cohort from the roster too. The app
  proposes FACULTY first at sign-in; a denial sends a VJTI address to
  registration and any other address away.
- Alternatives: showing the roster's roll number for confirmation instead of
  asking for it (no mismatch could then be detected or reported).
- Consequence: the typed number adds no security; the roster decides. It is a
  cross-check that surfaces roster mistakes. A profile written before the
  roster existed is upgraded in place by registering.

## D-026 Roster changes are audited, atomic and release the claim

- Context: a claimed entry may be wrong, or a student may get a new Google
  account. Fixing paired roster and index documents by hand is error-prone.
- Decision: faculty change an existing entry only through one of three
  actions, UPDATE, RELEASE or REMOVE, each a single write that includes an
  immutable `registryCorrections` record with a required reason. The record's
  id is fixed by the rules as `{email}_{addedAt millis}_{next revision}`, so a
  change cannot exist without its record or the record without its change,
  and nothing depends on the client's clock. Every correction drops the
  claim; the student registers again against the corrected entry.
- Alternatives: editing in the Firebase console (no audit trail, not atomic).
- Consequence: memberships written before a correction keep the old roll
  number, since memberships are immutable. Reports must read roll numbers
  from the roster.

## D-027 Identity alerts are self-reported and verified by the rules

- Context: faculty must be told about rejected identity attempts, but with no
  server nothing observes a rejected write except the client that made it.
- Decision: the rejected account writes the alert. The rules accept it only if
  the address, uid and time equal the sign-in token and server clock, the
  violation is true (checked against the allowlist and roster), and the
  document id equals `{uid}_{type}_{server date}`. A second alert of the same
  type that day is an update, and there is no update rule. Types:
  `NON_VJTI_EMAIL` and `NOT_ON_REGISTER` (to all faculty), `ROLL_MISMATCH` and
  `DUPLICATE_CLAIM` (to the teacher who added the student). Students cannot
  read alerts, including their own.
- Alternatives: Cloud Functions on rejected writes (needs Blaze, not allowed).
  A separate alert type for "another student's roll number" was dropped: it
  would let students probe which numbers exist. The faculty app labels a
  mismatch by looking the entered number up in the roster instead.
- Consequence: alerts are best-effort. A modified app can skip sending one;
  the rejection itself still holds. Real push is impossible on Spark; the
  faculty app can only raise a local notification on its next background
  check. Alerts carry no course, because joins no longer take a typed roll
  number and so cannot produce an identity violation.

## D-028 Retention

- Context: roster data and alerts contain personal data and must not be kept
  or deleted arbitrarily. Spark has no scheduled jobs.
- Decision: identity alerts cannot be deleted for 90 days and correction
  records for 365 days (`ALERT_RETENTION_DAYS`, `CORRECTION_RETENTION_DAYS` in
  the contract). After that a faculty app may delete them, and does so when
  it opens the relevant screen. Roster entries do not expire; they are removed
  only through an audited REMOVE.
- Alternatives: time-to-live policies (a paid feature).
- Consequence: purging happens only when a faculty app runs. Nothing can be
  removed early, by anyone, from a client.

## D-029 A write that loses a race is denied, so flows read again once

- Context: when two clients race (two sign-ins for one address, or a double
  submit), the loser's write is evaluated against the winner's committed data
  and is refused by the rules rather than retried by the SDK. The refusal
  carries no reason.
- Decision: the registration and add-student transactions run once more after
  a PERMISSION_DENIED. The second run reads the new state and returns the
  precise outcome (for example DUPLICATE_CLAIM or ROLL_TAKEN) without writing.
- Alternatives: showing a generic error for a race.
- Consequence: found by the race tests in Stage A; the Java repositories in
  Stages B and C must do the same, and `firebase/test/flows.js` is the
  reference they are held to.

## D-030 The contract is migrated in stages

- Context: Stage A changes the rules before the app. `contracts/documents.json`
  must describe what the rules accept, while the Java mappers still write the
  earlier membership shape.
- Decision: the file holds the final shapes. The shape the app still writes is
  kept under `memberships.legacy`, and the Java mapper test is bound to that
  key until Stage C replaces the mapper and deletes the key.
- Alternatives: changing rules and app in one step (not reviewable in stages).
- Consequence: between deploying the new rules and finishing Stage C, a build
  from before this extension cannot sign a student in or join a course.

## D-031 Tokens are written only while the live screen is visible

- Context: a rotating token is what proves a scan happened now. With no server,
  the faculty phone is the only thing that can issue one.
- Decision: the live-session screen writes a token every `ROTATION_S` seconds
  after the previous write is acknowledged, one write in flight at a time, and
  shows a code only after its write is acknowledged. The code is blanked
  `TOKEN_VALIDITY_S` seconds after its write *started*, which is never later
  than the server would stop accepting it. Leaving the screen stops the writes.
  The schedule is a pure state machine (`TokenRotation`) with its own tests.
- Alternatives: a foreground service that keeps writing in the background
  (more permissions, and a QR nobody can see has no use).
- Consequence: a session with the faculty app in the background has no valid
  code; students see "This QR has expired" until the screen is reopened.

## D-032 Ending a session has two forms

- Context: the rules never let `endsAt` move later, and compare it with the
  server clock.
- Decision: ending early sets `status = ENDED` and `endsAt = serverTimestamp`.
  Closing a session whose time has already passed changes `status` only. The
  repository picks the form from the document it reads in the transaction and
  decides again once if the write is denied (D-029).
- Consequence: `endsAt` is always the moment marks stopped being accepted,
  which Phase 5 and 6 rely on.

## D-033 Scan pre-checks mirror the rules, and a denial after them means expired

- Context: the rules refuse a mark without saying why.
- Decision: before writing, the client checks in a fixed order: payload format,
  already marked, session readable (a denial here means not enrolled), session
  ended, location fix (age <= 15 s, accuracy <= `MAX_ACCURACY_M`, not mock, wait
  up to 6 s), and the geofence with the same arithmetic as the rules (shared
  vectors). Each has its own message. A PERMISSION_DENIED after all of them
  passed can only be the token, so it is shown as "This QR has expired".
- Alternatives: one generic "not marked" message.
- Consequence: the pre-checks are for messages only. A modified app that skips
  them gets the same refusal from the rules.

## D-034 Scanner details

- Frames are analysed on the main executor with keep-only-latest; ML Kit does
  its own work off the main thread and the callback touches the ViewModel.
- Only QR codes are decoded. Text that is not `prs1:<id>:<token>` is ignored
  and scanning continues, so a poster next to the screen cannot end the scan.
- The first valid code stops analysis; the mark has an 8 s budget and then
  reports a timeout rather than queueing silently (Firestore's own offline
  queue is never used for writes, see D-029).

## D-035 Build exclusions added for generated code

- `-Xlint` excludes `rawtypes` and `unchecked`: Safe Args generates raw
  `HashMap` code. Everything hand-written still compiles clean under `-Werror`.
- Mockito is allowed by the brief but unused: every collaborator is an
  interface with a hand-written fake in `testutil/`.

## D-036 Deferred from the identity extension

- The alerts screen has no unread count and there is no background check with
  a local notification. Both were optional in Stage D and neither affects
  enforcement. Alerts are live while the Alerts screen is open.

## D-037 Offline queue

- Context: a classroom can have no signal. A scan made then must still count,
  without weakening what the rules verify.
- Decision: when the server cannot be reached (no connection, or a step that
  times out, including a mark whose answer never came), the scan is saved in a
  Room table: session id, student id, code, fix, and the device clock at the
  moment the code was read. A refusal is never saved. A WorkManager job with a
  network constraint sends saved scans as a QUEUED mark; the rules accept it
  only if the saved scan time falls inside the window in which that code was
  current, and until `QUEUE_GRACE_MIN` after the session's end.
- Each saved scan ends in one state: synced, expired (past the grace period),
  rejected with the reason, or still pending. The logic is a pure class
  (`PendingMarkSync`) tested against fakes. Sending twice is harmless: the
  record id is fixed, and an existing record is reported, not rewritten.
- Offline the app knows nothing about the session, so enrolment, the session's
  end and the geofence are checked when the scan is sent. The message arrives
  late; the decision is the same.
- A settled row has its code and coordinates wiped. The database is excluded
  from backup. One row per student and session; a newer scan replaces an older
  one, and a late result for the older one is ignored.
- Consequence: a queued mark depends on the device clock. The window is 20 s
  and a code is on screen for about 12 s, so a clock more than a few seconds
  off makes the mark fail (closed, never open). A student can forge
  `scannedAt`, but only to a moment when the code they hold was current, which
  means they had the code while it was valid, and the geofence still applies.
- Known gap: the scanner needs the profile loaded once in the running app, so
  an app opened cold with no connection shows the courses error and no Scan
  button. Opening the app before going offline works.
- Build: `-Xlint:-processing` was added. With Room's annotation processor on
  the path javac warns about every annotation no processor claims.

## D-038 Manual correction is a write without a read

- Context: a teacher may read an attendance record only if its `teacherId` is
  theirs, so a record that does not exist cannot be read at all. A
  read-then-write correction is therefore refused for every absent student.
  Found by the flow test before the screen was run.
- Decision: the session roster is a live listener, so it already knows whether
  a record exists. The correction is a transaction with no read: an update of
  four fields if a record exists, a create of a MANUAL record if not. Marking
  absent a student with no record writes nothing, since no record means absent.
- A stale roster cannot damage data. A create over an existing record is a full
  overwrite, which the update rule refuses because it touches fields a teacher
  may not change; an update of a missing record fails. Both are tested.
- Alternatives: letting teachers read missing records. Rejected: it would have
  to be allowed for every signed-in user, and would reveal whether any student
  has a record for any session.
- Consequence: a correction keeps the original source, token and coordinates
  of a scanned mark, so the audit trail shows it was a scan that was overruled.

## D-039 How an attendance percentage is defined

- Decision: sessions attended divided by sessions held, for one student in one
  course. A session counts as held once it is over (ended, or its time has run
  out), so a class in progress never lowers a figure. Present, late sync and
  marked present count as attended; marked absent and no record do not. The
  figure is shown as a whole percent, halves rounded up.
- "Below the requirement" is compared exactly (`present * 100 < threshold *
  total`), not on the rounded figure: 74.6% displays as 75% and is still below
  a 75% requirement. Before any session is held nobody is at risk.
- The calculation is one pure class (`AttendanceStats`) used by the student's
  history and the teacher's report, so the two can never disagree.
- Known limits: a student who joined after some sessions were held is counted
  absent for those; the last 50 sessions of a course are loaded, so a longer
  course would be computed over its most recent 50. Both are acceptable for a
  semester course and would need `joinedAt` and paging to lift.

## D-040 Audit for a full class: geofence, scan path, load

- Context: an audit for one teacher and 60 students scanning at once, with
  nobody outside able to mark.
- Geofence: a mark is accepted within `radius + reported accuracy`. With
  accuracy allowed up to 100 m, a 30 m room accepted a phone 130 m away.
  `MAX_ACCURACY_M` is now 50, so the worst case is radius + 50. A fix that is
  recent but vaguer than that gets its own message (`LOCATION_INACCURATE`)
  instead of "couldn't get your location". The value must be checked in the
  real room: if honest phones report worse than 50 m indoors it is raised, and
  the teacher marks the exceptions by hand.
- Scan path: the separate "already marked" read is gone, because the mark
  transaction reports it anyway. A scan is now one session read and one
  transaction.
- Late delivery: if a LIVE mark is refused after every check the app can make
  has passed, the code stopped being current on the way. The same scan is
  sent once as a QUEUED mark with the time the code was read. The existing
  QUEUED rule accepts it only if the code was current at that time, so an old
  photo is still refused. The record shows as "Late sync".
- Load: marks never contend, since each is its own document and the session
  document is not written during class. `firebase/test/load.test.js` submits
  120 marks at once for 60 students while codes rotate, with six kinds of
  intruder, and expects exactly 60 records. It proves the rules and
  transactions under concurrency on the emulator, not a real network.
- Quota for a 60-student, 60-minute session: about 300 token writes, 60 marks,
  under 600 reads. The free plan allows 20,000 writes and 50,000 reads a day.
- Practice: one phone screen for 60 people is a queue. Mirror the teacher's
  phone to the projector so the room scans at once.
- Alternatives: capping the accuracy credit separately from the accuracy
  limit (two numbers to explain); hiding the geofence from students (the room
  is public knowledge, and the app could no longer say "outside the area").
