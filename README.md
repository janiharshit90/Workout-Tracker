# Rarelift

*Lift hard. Lift rarely.* A workout tracker for Android built around high-intensity training in the
style of Mike Mentzer's Heavy Duty: one all-out set per
exercise, taken to failure, then enough rest to actually recover. It does the bookkeeping so you can
focus on the set: what to beat today, when you're recovered, when a lift has stalled, and whether
you're getting stronger.

## What it does

**During a workout**
- Pick a workout, check it over, tap Start. Then one exercise at a time with a big **target**
  ("add weight / beat it / build reps") based on your last session and rep range.
- Large weight and reps fields with -/+ steppers, *to failure* marking, and **beyond-failure**
  techniques: forced reps, negatives, rest-pause and static holds.
- **Pre-exhaust supersets**: linked exercises tell you to go straight into the next one, no rest.
- Rest timer that keeps going with the phone locked (lock-screen countdown, +30s / Stop, alert when done).
- Session clock with a gentle nudge past 45 minutes, notes per exercise, warm-up sets.

**Between workouts**
- Recovery status per workout and a suggested next one; a this-week strip and a calendar.
- **Stall detection** with Heavy Duty advice: rest more, or back off and rebuild.
- **Stats**: consistency, average rest, strength trend per exercise, body weight, personal records,
  and a chart for every exercise.
- Share any workout as an image. Export everything as CSV.
- Ready-made **routines** (Mentzer's Ideal Routine, a Consolidation routine) in one tap.

**Your data**
- Everything stays on the phone. Back up to a JSON file and restore it from Settings; do this before
  reinstalling or switching phones.
- Weights are stored in lbs and shown in lbs or kg.

## Install

Grab the latest `Rarelift-x.y.z.apk` from
[Releases](https://github.com/janiharshit90/Workout-Tracker/releases) and open it on the phone
(Android 7.0+). After that the app checks for updates itself and installs them in a tap.

### Coming from "Mentzer Tracker"?
Version 2.0 gave the app a new name and a new identity on Android (`com.rarelift.app`), so Android treats
it as a different app and the old one can't update into it. Move over once, by hand:
1. In the old app: **Settings → Back up data**, save the file.
2. Install `Rarelift-2.x.x.apk` from Releases. It installs alongside the old app.
3. In Rarelift: **Settings → Restore from backup**, pick the file.
4. Check your history is there, then uninstall the old app. Rarelift updates itself from then on.

## Two editions
- **GitHub edition** (`github` flavor): the APK on Releases. Updates itself from GitHub, everything unlocked.
- **Play edition** (`play` flavor): for Google Play. No self-updater; Stats trends, routines and sharing
  are **Rarelift Pro**, a one-time in-app purchase (product ID `rarelift_pro`).

Launching on Play, step by step: [docs/LAUNCH.md](docs/LAUNCH.md). Privacy policy: [PRIVACY.md](PRIVACY.md).

## Releasing a new version

```
git tag v1.11.0
git push origin v1.11.0
```

The **Release** workflow builds a signed APK (version `1.11.0`, versionCode `11100`) and publishes a
GitHub Release; the app finds it on its next check. The same run also produces the signed Play App
Bundle as a downloadable artifact. Hand-written notes in `docs/release-notes/v<version>.md` are put at
the top of the release (the in-app update dialog shows them). You can also run it from
Actions → Release → Run workflow and type the version.

Signing needs four repository secrets: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
`Make-Keystore.ps1` creates a keystore and prints them (one time only; keep the keystore safe, since
updates must be signed with the same key forever).

## Development

- Android Studio (or just the Gradle wrapper), JDK 17, Android SDK 35.
- Open the `MentzerTracker/` folder.
- Unit tests (plain JVM, no emulator): `./gradlew testGithubDebugUnitTest`
- Debug APKs: `./gradlew assembleGithubDebug assemblePlayDebug`. Every push and pull request is built and tested by the
  **Build APK** workflow; the APK is attached to the run.
- For the in-app updater to work in a local build, add `updateRepo=janiharshit90/Workout-Tracker`
  to `gradle.properties` (CI fills it in automatically).

### Where things live

```
MentzerTracker/app/src/main/java/com/example/mentzertracker/
  MainActivity.kt        navigation, tabs, edge-to-edge
  WorkoutViewModel.kt    app state and every user action
  data/                  Room entities, DAOs, migrations, JSON backup
  ui/                    Compose screens (Today flow, History, Stats, Settings...)
  util/                  plain-Kotlin logic: progression, stalls, stats, routines, share image
  timer/                 lock-screen rest timer (alarm + notification)
  update/                self-updater (GitHub Releases)
```

Logic that decides anything (targets, PRs, stalls, trends, supersets) lives in `util/` as plain
Kotlin with unit tests in `app/src/test`. Screens stay thin.

### Changing the database

Bump `version` in `data/AppDatabase.kt` **and** add a `Migration` for it. There's deliberately no
destructive fallback: forgetting the migration crashes on launch instead of wiping people's history.
Add any new fields to `data/Backup.kt` too, with defaults so older backups still restore.

## History

See [CHANGELOG.md](CHANGELOG.md). Detailed notes from each development round are in
[`docs/rounds/`](docs/rounds/).

The Heavy Duty principles in the app are paraphrased from the general HIT literature. This isn't
medical advice; listen to your body.
