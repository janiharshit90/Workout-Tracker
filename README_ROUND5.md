# Round 5

Three Mentzer-specific features. **Database schema v5** (migration 4 -> 5 adds `workout_sets.durationSec`),
so back up from Settings before updating. Suggested release tag: **v1.3.0**.

## 1. Progression suggestions (Today)
Under "Last (...)" each exercise now shows a green hint based on your most recent session:

| Last top set | Hint |
|---|---|
| reps >= top of range | **Add weight** (+5 lb / +2.5 kg) and aim for the bottom of the range |
| reps inside range | **Beat it**: same weight, one more rep |
| reps below range | **Build reps**: same weight, get to the bottom of the range first |

Tap the hint to drop the suggested weight into every set row (reps stay blank - you earn those).
Forced reps are never counted. Range is configurable in Settings -> Heavy Duty (4-8, 6-10, 8-12; default 6-10).

## 2. Session duration
- A workout clock appears on Today after your first weight/reps/failure entry (survives app kills).
  It turns red past 45 min as a nudge to keep it brief. "Reset" restarts it if it started by accident.
- Duration is saved with the session and shown in History (red when over the target).
  Logging reports it too ("Heavy Duty session logged in 23 min").
- Clocks older than 3 h are treated as abandoned (stored as unknown rather than a bogus number).
- CSV export gains `To failure`, `Forced reps` and `Session (min)` columns; JSON backups include durations
  (older backups still import).

## 3. Edit failure / forced reps on logged sets
Tap any set in History: the edit dialog now has a "Taken to failure" switch and a forced-reps stepper.
