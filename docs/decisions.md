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
