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
