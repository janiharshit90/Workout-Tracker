# Round 11: rename to Ironset (new app ID) — DO NOT MERGE UNTIL YOU'RE READY TO SWITCH

Neutral name for the Play Store: **Ironset**, app ID **`com.ironset.app`**. Suggested tag when ready: **v2.0.0**.

## Why this is a one-time manual switch
Changing the app ID makes Android treat it as a **different app**. Existing installs can't update into it, and
the in-app updater deliberately refuses an APK with another package name. So for you and each friend:
1. Open **Settings -> Back up data** in the old app and save the file.
2. Install the new APK from the GitHub release page (once, by hand). It installs next to the old one.
3. Open Ironset -> **Settings -> Restore from backup** and pick the file.
4. Uninstall the old "Mentzer Tracker". From then on Ironset updates itself as usual.

## What changed
- `applicationId` -> `com.ironset.app`; app label -> "Ironset"; user-facing strings, download file name and CI
  artifact names updated.
- Kept on purpose: the code package/namespace (`com.example.mentzertracker`, internal only) and the backup file tag,
  so backups from the old app restore into Ironset.

## Before publishing to Google Play
- The app ID can **never** change after publishing. Check "Ironset" and the ID are free on Play and as a trademark.
- Still to do for Play: target API 35+, privacy policy, Data Safety form, a build without the self-updater
  (Play doesn't allow apps to install their own updates), and a new launcher icon with the final name.
