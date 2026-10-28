# Round 9: summary, notes, warm-ups, weight jumps, feedback

**Includes a database change (v5 -> v6, written migration).** Back up from Settings before updating.
Suggested tag: **v1.7.0**.

## What's new
1. **Post-workout summary**: after you log, a full screen shows time, exercises, sets, PR count and, per
   exercise, your top set and how it compares with last time (+5 lbs, +1 rep, same, down). Replaces the snackbar.
2. **Notes**: a note field on every exercise during a workout. It's saved with that exercise's first working
   set, shown in History, and shown as a reminder next time (Today and the preview). Editable in History's set editor.
3. **Warm-up sets**: tag a set as a warm-up (Several-sets mode). Warm-ups are logged but never count toward PRs,
   "last time", progression targets or the progress chart. Heavy Duty one-set mode has no warm-up rows by design.
4. **Weight jump per exercise**: Settings -> Manage exercises -> "Weight jump". Used for the progression target
   and the -/+ buttons (1, 2.5, 5, 10 lbs or 1, 1.25, 2.5, 5 kg). Default stays 5 lbs / 2.5 kg.
5. **Feedback**: Settings -> Send feedback opens the share sheet with version and device attached. If the app
   crashed last time, "Share crash log" appears (the log stays on the phone until you share it).
6. **Release workflow**: tags with or without a leading `v` now trigger a release, and Actions -> Release ->
   Run workflow lets you type a version (it creates the tag). Unit tests now run in the Build workflow.

## Database
`MIGRATION_5_6`: `exercises.stepLbs REAL NOT NULL DEFAULT 0` and `workout_sets.isWarmup INTEGER NOT NULL DEFAULT 0`.
Backups include both (older backups restore fine). CSV gains a "Warm-up" column.

## Tests
`app/src/test`: JVM unit tests for progression (incl. warm-ups, custom jump, kg noise) and stats (PR rules,
change vs last time, warm-up exclusion, notes). A real *migration* test needs an emulator in CI, so it isn't
included; the migration is two plain `ALTER TABLE ... DEFAULT` statements like the previous two.
