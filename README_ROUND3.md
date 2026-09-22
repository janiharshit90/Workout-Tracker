# Round 3: reliable self-updates, logic fixes, better workout flow

This is a **complete** replacement of your `app/src/main` Kotlin sources, the
manifest, `app/build.gradle.kts`, and the GitHub workflows. No database schema
change, so your history, PRs and templates carry over untouched.

## Install

1. **Delete** these folders first (they're fully replaced, and leftover files
   from earlier rounds would cause "redeclaration" build errors):
   - `app/src/main/java/com/example/mentzertracker/util/`
   - `app/src/main/java/com/example/mentzertracker/update/`
   - `app/src/main/java/com/example/mentzertracker/ui/` **except `ui/theme/`**
2. Unzip this over your project root.
3. **Kept from your project (not in this zip):** `data/Entities.kt`,
   `data/SeedData.kt`, `ui/theme/Theme.kt`, your launcher icons.
4. If your existing `AppDatabase.kt` says a `version` other than **2**, change
   the number in the new file to match yours before building.

## Setting up updates (one time, ~5 minutes)

Why the old updater could never really work:
- `versionCode`/`versionName` were hard-coded to `1`/`1.0`, so no build was ever "newer".
- CI built **debug** APKs, which are signed with a random key per runner. Android
  refuses to install an update signed with a different key ("App not installed").
- The workflow used `secrets` inside `if:` (GitHub doesn't allow that) and the
  keystore path resolved to the wrong folder, so signed builds never ran.

Fix, once:
1. From the project root on Windows: `powershell -ExecutionPolicy Bypass -File Make-Keystore.ps1`
2. Add the 4 secrets it prints to GitHub (repo -> Settings -> Secrets and variables -> Actions).
3. The repo must be **public** (the app checks GitHub without a login).
4. For local builds, add `updateRepo=YOUR_USER/YOUR_REPO` to `gradle.properties`.
   In GitHub Actions this is filled in automatically.

To ship an update from then on:
```
git tag v1.1.0
git push origin v1.1.0
```
The Release workflow builds a signed APK, sets versionName `1.1.0` / versionCode
`10100`, and publishes a GitHub Release. The app finds it on next launch
(checks at most every 6h) or immediately via Settings -> Check for updates.

**One-time switchover:** the app currently on your phone was signed with a debug
key, so the first signed release can't install over it. The app detects this
and tells you. Do: Settings -> Back up data -> uninstall -> install the release
APK -> Settings -> Restore from backup. After that, every update is one tap.

## What the updater does now
Checks GitHub -> shows version + release notes -> downloads with a progress bar
(cancellable) -> **verifies the APK before installing** (same app, higher
versionCode, same signing key, with a plain-English reason if not) -> walks you
through the one-time "allow installs from this app" permission -> opens the
installer. "Skip this version" and an auto-check toggle are in the dialog/Settings.

## Logic bugs fixed
- **Stale PRs:** deleting or editing a set never updated the PR. PRs are now
  computed live from your history, so they're always correct.
- **Weight auto-fill:** typing "135" in set 1 left sets 2-3 stuck at "1"
  (only the first keystroke copied over). Fixed.
- **Lost workouts:** typed-in sets vanished if Android killed the app mid-session.
  The draft is now saved continuously and restored.
- **Template tap wiped your entries.** Switching templates now only filters.
- **Template order ignored:** exercises now appear in the template's order.
- **Double-tap on LOG** could save the session twice. Guarded.
- **Crash risk:** logging a draft that referenced a since-deleted exercise
  violated a foreign key. Now skipped.
- **Comma decimals** ("22,5") silently failed to parse on many phone locales. Fixed.
- **Set numbering** had gaps when a middle set was blank. Now sequential.
- **Rest timer** reset when you switched tabs or scrolled it off-screen, and
  never alerted you. It now lives in the ViewModel and vibrates + chimes when done.
- **Snackbars** could be dropped or shown twice (each screen had its own
  collector). Now one app-wide host.
- **Data-wipe landmine:** `fallbackToDestructiveMigration()` would have deleted
  all history on any future schema change. Removed (see comment in AppDatabase.kt).
- Template edit multi-step writes are now transactional; the edit dialog opens
  pre-filled instantly (no more async gap from the last round).
- CSV export escaped nothing, so an exercise name with a comma broke the file.

## New features
- **"Last time" on every exercise card** (weights & reps + when), used as field
  placeholders, with a one-tap **Use last** - the core of progressive overload.
- **PR line on each card**, and a **"New PR!"** callout when you log one.
- **+ Set / - Set** per exercise; "Days since last workout" on Today.
- **History:** tap a set to edit/delete it; session volume; **Undo** on deletes.
- **Progress:** Top set / Est. 1RM / Volume views, date axis, session list.
- **Backup & restore** (JSON, full fidelity) and **CSV share** in Settings.
- **Reorder exercises** (arrows in Manage Exercises).
- **Keep screen on** while on Today or while the timer runs (toggle).
- Heavy Duty mode and your selected template are remembered across launches.
