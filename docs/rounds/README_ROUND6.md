# Round 6: guided workout flow

Feedback from a tester: Today felt messy. Today is now a guided flow. **No database change**, so this is
safe to update over round 5. Suggested tag: **v1.4.0**.

## Before: start screen (no workout in progress)
- Big workout cards, one per template, with its exercises and a **Ready / Recovering** status.
  The ready workout you've gone longest without is marked **Suggested**. Tap a card to start.
- **Custom workout** to pick exercises as you go.
- Style (Heavy Duty one set / Several sets) lives here, once, instead of a card on every visit.
- First-launch card: "Heavy Duty in 3 steps" (dismissible; disappears after your first logged workout).

## During: one exercise at a time
- Header: workout name, live clock (red past 45 min), **Finish** and a close button (asks before discarding).
- Chips across the top jump between exercises (tick when done); "Add" adds one mid-workout.
- The current exercise shows: name, cue, a **Target** (progression hint, "Use weight"), last time / PR,
  then the set rows. Recovery only appears when you're training before you've recovered.
- Bottom: compact rest timer + **Back / Next exercise**; the last one becomes **Review & finish**.
- **Review**: per-exercise set counts, warns about incomplete sets, then Log workout.
- An app kill mid-workout resumes the same workout on the first unfinished exercise.

## Moved
- Rest timer length (2-5 min) is now in Settings -> Workout.
- A draft from before this update is adopted as a workout in progress, so nothing is lost.
