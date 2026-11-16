# Testing guide

What to do, in order, to test everything built so far (Phases 0 to 5 and the
student identity extension). Each check lists what you should see. Nothing here
has been run against the production project by me: the rules below are not
deployed yet, and I cannot sign in as you.

## 0. What has and has not been verified

| Area | Automated | On an emulator with stand-in data | Real Firebase, real accounts |
|------|-----------|-----------------------------------|------------------------------|
| Security rules | 555 Jest tests, 76 of 76 mutations caught | n/a | Phase 2 rules only (yours) |
| Sign-in, courses | JUnit | yes | yes (yours, before the identity extension) |
| Roster, CSV import, alerts, registration | JUnit + rules | yes | **no** |
| Session, rotating QR, end session | JUnit + rules | yes | **no** |
| Scanner, result screens | JUnit + rules | yes (simulated scan) | **no** |
| Offline queue | JUnit + rules | yes (airplane mode, stand-in server) | **no** |
| Session roster, manual correction | JUnit + rules | **no** | **no** |
| Camera decoding a real QR | none | no | **no** |

So sections 3 to 9 are the first real end-to-end run. Expect to find things.

## 1. One-time setup

1. Deploy the rules and indexes (production still has the Phase 2 rules, and
   the current app cannot register a student against them):

       cd firebase
       npx firebase login
       npm run deploy

   Index builds take a few minutes. Until they finish, the course screen and
   the alerts screen show "Couldn't load…".

2. Delete the one membership created before the roster existed. Firebase
   console > Firestore > `memberships` > the document named
   `{courseId}_{your student uid}` > Delete document. It has no `rollNo` or
   `cohort`, so the new rules would never let it be read as a valid enrolment.
   Leave `users`, `courses`, `joinCodes` and `teacherAllowlist` alone.

3. Delete your student profile so the account registers fresh: `users` > the
   document whose `email` is `nrohekar_mc26@mc.vjti.ac.in` > Delete document.
   Do **not** delete the faculty profile.

4. Optional, for the "Campus" option when starting a session. Create
   `appConfig/campus` in the console with four number fields:

   | Field | Value |
   |-------|-------|
   | `lat` | latitude of the campus centre |
   | `lng` | longitude |
   | `radiusM` | 30 to 1000 |
   | `cosLat` | cosine of `lat` in radians, e.g. `0.9453923589838183` for 19.0222 |

   Take the coordinates from Maps; I have not looked yours up. Without this
   document, choosing Campus says "The campus location isn't set up yet. Choose
   Here instead."

5. Two devices: the faculty phone shows the QR, the student phone scans it.
   Both need Location on. An emulator works as the faculty device.

## 2. Automated gates

    ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug assembleRelease
    cd firebase && npm test
    cd firebase && npm run test:mutation

Expected: build successful; rules tests all pass; every mutation caught.

## 3. Roster (faculty account)

1. Sign in with the allowlisted account. Account icon > Roster.
2. "+" > add `nrohekar_mc26@mc.vjti.ac.in`, roll `262010016`, cohort `MC26`.
   The row appears under "Cohort MC26".
3. Add the same address again: "This address is already on the roster."
4. Add another address with the same roll number and cohort: "This roll number
   belongs to another student in this cohort."
5. Add `someone@gmail.com`: the form refuses it before sending.
6. Import CSV: make a file with the header `email,rollNo,cohort` and rows that
   include one valid new student, a repeat of a row, a Gmail address, a roll
   number that is not 9 digits, and a row with two columns. The preview marks
   each bad row with its reason; only the valid row is offered. Import, then
   check the result list.
7. Tap a student > change the roll number > Save without a reason: refused.
   With a reason: saved. In the console, `registryCorrections` has one new
   document.

## 4. Registration and identity (student account)

1. Sign in with a personal Gmail account that is not on the allowlist: "Please
   sign in using your official VJTI student ID." You are signed out again.
2. Sign in with `nrohekar_mc26@mc.vjti.ac.in`. The Register screen shows the
   address and asks for the roll number.
3. Enter `262010017`: "That roll number doesn't match the roster for this
   account."
4. Enter `262010016`: you land on Courses.
5. Join with the existing join code. The confirm step says "You'll join as roll
   number 262010016."
6. Sign out and back in: straight to Courses, no Register screen.
7. Faculty account > Account > Alerts: one alert for the Gmail attempt and one
   for the wrong roll number. A second wrong attempt of the same kind on the
   same day does not add a second alert.

## 5. Session and rotating QR (faculty)

1. Courses > the course > Start session. Choose Here, allow precise location,
   wait for "Location ready", Start.
2. The QR appears only after the first code is saved, then changes every 12 s.
   The ring next to "Ends at" counts down each code.
3. Turn on airplane mode: "Reconnecting…", and the QR disappears within 20 s.
   Turn it off: a new QR appears.
4. Swipe the app away and reopen it: the course row shows "Live", and "Open
   live session" brings the QR back.
5. Read the QR with any QR reader: `prs1:<sessionId>:<22 characters>`.
6. End session > "End this session?" > End session. "Session ended"; the course
   no longer shows Live.

## 6. Scan and result (student, second device)

Start a fresh session on the faculty device first.

1. Courses > Scan QR. First time: "Camera and precise location are needed" >
   Allow. Choose "Precise" and "While using the app".
2. Scan the faculty screen from inside the area: "Attendance marked", a drawn
   green check, a haptic. The faculty screen's count goes up without a refresh
   and your name is under "Latest marks".
3. Scan again: "You've already marked attendance for this session."
4. Photograph the QR with a third phone, wait 25 s, scan the photo with a
   student who has not marked: "This QR has expired. Scan the current one."
5. Start a session with Here and the smallest radius, walk well outside it (or
   start it on an emulator with a faraway location): "You're outside the
   allowed area".
6. Developer options > Select mock location app, with a mock app running:
   "Mock location is turned on. Turn it off to mark attendance."
7. App info > Permissions > Location > turn off "Use precise location": the
   scanner shows the permission explanation with "Open settings".
8. A student who has not joined the course: "You're not enrolled in this
   course."
9. Scan after the session ended: "This session has ended."
10. Scan a QR that is not an attendance code (a URL): nothing happens; the
    scanner keeps looking.

## 7. Offline queue (student)

Keep the app open on Courses before going offline (see Known gaps).

1. Faculty starts a session. On the student phone turn on airplane mode, then
   Scan QR and scan the code: "Saved. It will sync when you're back online."
2. Courses now shows "1 saved scan" above Scan QR. Open it: the scan is
   "Waiting".
3. Swipe the app away, turn airplane mode off, wait about a minute. Open the
   app > saved scans: "Sent · Recorded as a late sync". The faculty roster
   shows the student with "Late sync".
4. Tap the sent row: it is removed from the list.
5. Expiry: save a scan offline, end the session on the faculty phone, and stay
   offline for more than 30 minutes after the end. Reconnect: the row shows
   "Expired" and stays until tapped.
6. Stale code: save a scan offline from a photo of a code that was already
   more than 20 s old. Reconnect: "Not accepted · This QR has expired."
7. Set the student phone's clock a minute wrong, save a scan offline,
   reconnect: "Not accepted". This is by design (D-037).

## 8. Session roster and manual correction (faculty)

1. Courses > the course > tap an ended session. Every enrolled student is
   listed with Present, Late sync, Marked present, Marked absent or Absent, and
   the footer says "Present: X of Y".
2. Tap an absent student > Mark present with an empty reason: "Enter a reason."
   With a reason: the sheet closes and the row changes to "Marked present"
   without a refresh.
3. Tap a present student > Mark absent with a reason: "Marked absent"; the
   count drops by one.
4. Airplane mode: the button is disabled with an explanation.
5. In the console the record keeps its original `source` and gains `note`,
   `updatedBy` and `updatedAt`.

## 9. Things to look at on every screen

- Dark mode, landscape, largest font size, gesture and 3-button navigation.
- No button under the system bar; no text cut off.
- Airplane mode: actions that need the network are disabled with a reason.

## 10. Known gaps

- Opening the app cold with no connection shows no Scan button (the profile
  has to load once). Open the app first, then go offline.
- Force stop (Settings > Apps) cancels background sync until the app is opened
  again. Swiping the app away does not.
- Alerts: no unread count and no background notification. Alerts appear when
  the Alerts screen is open (live while it is open).
- Phase 6 is partly built: the session roster and manual correction are in.
  Percentages, the at-risk list, the student's own history, CSV export and the
  low-attendance notification are not.
- The correction note is stored but not shown on the roster.

## 11. If something fails

- "You don't have permission to do that" on a first action after deploy
  usually means the rules deploy did not finish, or the account's `users`
  document predates the identity extension (delete it and sign in again).
- "Couldn't load…" on the course or alerts screen right after deploy: the
  index is still building (console > Firestore > Indexes).
- Run with the emulators instead of production: `cd firebase && npm run
  emulators`, add `firebase.emulator.host=10.0.2.2` to `local.properties`
  (debug builds only), rebuild.
