# Round 13: stall detection + log a past workout

**No database change.** Suggested tag: **v1.10.0** (assuming v1.9.0 is out).

## Stall detection
An exercise is flagged when its last **2 sessions in a row** failed to beat your best (more weight, or more reps
at the same weight; warm-ups ignored; going backwards counts). Shown on the exercise page and in the preview.
The advice is Heavy Duty's, not "do more":
- training it more often than your recovery target -> **try N+ days of rest** before the next one;
- already resting enough -> **back off ~10%** (shown as an actual weight, rounded to that exercise's jump) and
  rebuild toward the top of your rep range.

## Log a past workout
History -> calendar-edit icon (top right). Pick the day (not in the future), pick the workout (or Custom), then
it runs through the normal preview and guided flow with **no timer**; it's saved on that day (current time of
day, so two backfilled sessions never collide). Handy for your old Excel history. It shows up in History, the
calendar, the week strip and PRs. Backfilled sessions skip the "NEW PR / vs last time" labels on the summary
(those only make sense for a workout happening now). Duration is stored as unknown.
You can't start one while a workout is in progress.

## Tests
New unit tests for stall detection (progress / flat / regression / warm-ups / rest-vs-deload advice / deload
rounding) and the date-picker day conversion.
