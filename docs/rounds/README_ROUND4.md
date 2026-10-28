# Round 4: Heavy Duty features (recovery, failure logging, principles)

Built directly on top of your Round 3 code (cloned from
github.com/janiharshit90/Workout-Tracker) — every file here is a full,
ready-to-drop-in replacement of the same file in your repo. New files just
get added.

Unzip over `MentzerTracker/app/` and rebuild.

## Files touched

**Replaced:**
- `data/Entities.kt` — `WorkoutSet` gets two new columns: `toFailure`, `forcedReps`
- `data/AppDatabase.kt` — version bumped 3 → 4, **with an actual `Migration(3, 4)`** this time (see the note below)
- `data/Daos.kt` — the two `SetWithExercise` queries now select the new columns
- `data/Backup.kt` — backup/restore now carries `toFailure`/`forcedReps`; old backup files still import fine (they just default to `false`/`0`)
- `WorkoutViewModel.kt` — new state, mutators, draft persistence for the above (details below)
- `ui/TodayScreen.kt` — the actual UI for all of this
- `ui/SettingsScreen.kt` — new "Heavy Duty" section
- `ui/HistoryScreen.kt` — shows a flame icon on sets logged to failure
- `MainActivity.kt` — one new route (`principles`)

**New:**
- `ui/PrinciplesScreen.kt` — static reference screen

## About the migration

This is the same kind of change that broke Round 3 (a `@Database(version = ...)`
bump), except this time there's a real `Migration(3, 4)` registered in
`AppDatabase.kt` that runs two `ALTER TABLE` statements to add the columns
with safe defaults. Existing installs will upgrade cleanly with their full
history intact — this is what "bump the version *and* write the migration"
is supposed to look like.

## What's new, feature by feature

**Recovery badge (Today, per exercise)** — Mentzer's whole model is that
growth happens during recovery, not during the workout, so training the
same exercise again too soon is actively counterproductive. Each exercise
card now shows "Recovering · N days left" or "Recovered · ready to train"
based on your last session for that exercise and a recovery target you set
in Settings (3/5/7/10 days, default 5). Purely informational — it never
blocks you from logging, since you know your own body better than a badge
does.

**Training to failure + forced reps (Today, per set)** — a small flame
toggle next to each set marks whether it was taken to genuine failure. Tap
it on and a forced-reps counter (+/-) appears, for reps completed with a
spotter's help past failure. Shows up afterward in History as a flame icon
next to the set. This is the actual signal HIT is built around, so it
seemed worth making a first-class field instead of stuffing it into the
existing free-text notes column. There's an on/off switch for the whole
feature in Settings → Heavy Duty, in case a friend testing the APK doesn't
want the extra taps mid-set and just wants weight/reps.

**Heavy Duty Principles screen** (Settings → Heavy Duty Principles) — five
short cards on training to failure, recovery, why more sets isn't better,
keeping sessions brief, and progress as the only real signal. Paraphrased
in my own words from general HIT training concepts, not quoted from any
one source — reinforces what makes this app different from a generic
tracker, costs nothing to maintain.

## Things I deliberately left out of this round

- Editing `toFailure`/`forcedReps` on an already-logged set (the History
  edit dialog still only does weight/reps) — keeps that dialog simple;
  can add if it turns out you want to correct these after the fact.
- Any stats derived from failure data (e.g. "% of sets taken to failure"
  on the Progress screen) — the data's there now if you want that later.
- Session-length/brevity tracking ("keep it brief") — didn't want to touch
  the schema twice in one round. Natural next feature once this is tested.
