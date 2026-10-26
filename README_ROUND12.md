# Round 12: week strip + calendar

**No database change.** Suggested tag: **v1.9.0** (v2.1.0 if you've already switched to Ironset).

- **Today** now opens with **THIS WEEK**: Mon-Sun, each day showing what you did (the workout's letter, A/B/...),
  REST for days with nothing logged, and READY on upcoming days from your recovery setting (Settings -> Heavy
  Duty -> recovery days) counted from your last workout. Nothing is scheduled: Heavy Duty is paced by recovery,
  so the strip follows you. Today is outlined.
- **History -> CALENDAR**: a month grid (filled = trained), arrows to go back through months, tap a day to see
  the workout (or "Rest day"). The LIST view is unchanged.
- Workouts don't store which template they were, so the letter is matched by exercises (>= 60% overlap with a
  template); anything else shows a dot / "Custom workout".
- Unit tests added for week/month maths and template matching.
