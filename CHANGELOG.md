# Changelog

Newest first. Each release is a git tag (`v1.10.0`), built and published by the Release workflow,
and picked up by the app's built-in updater. Detailed notes for older rounds are in
[`docs/rounds/`](docs/rounds/).

## 1.11.0 (next)

**Database v6 → v7, with a written migration.** History, templates and settings carry over. Back up
from Settings first anyway; it's a good habit before any update that touches the database.

Heavy Duty
- **Beyond failure.** Once a set is marked to failure, add forced reps, negatives, rest-pause reps or a
  static hold. They're logged on their own and never count toward reps, PRs or targets.
- **Pre-exhaust supersets.** Link an exercise to the next one in a template. During the workout it
  says "No rest, straight into…" instead of offering the rest timer.
- **Ready-made routines.** Add Mentzer's Ideal Routine or a Consolidation routine in one tap (Start
  screen → Routines, or Manage templates). It reuses exercises you already have.
- Principles screen explains both ideas.

Insights & sharing
- **Stats tab** (replaces PRs): workouts and PRs over the last 30 days, average rest between
  workouts, average session length, a 12-week consistency chart, each exercise's strength trend over
  8 weeks, a body-weight log with chart, and the PR list.
- **Share a workout as an image** from the post-workout summary or any session in History.
- Sessions now remember which workout they were, so the calendar and week strip stop guessing.
  Older sessions still fall back to matching by exercises.
- History shows each session's workout name; CSV export gains Negatives, Rest-pause and Static hold.

Under the hood
- Targets Android 15 (API 35); the app now draws edge to edge and pads for the system bars and
  keyboard itself. Android Gradle Plugin 8.7.3, Gradle 8.9.
- Repo tidy: a real README, this changelog, round notes moved to `docs/rounds/`, and the old
  PowerShell project generators removed (running them would have overwritten current code).
- New unit tests for techniques, supersets, routines, stats and session summaries.

## 1.10.0 (2026-10-26)
- **Stall detection**: flags an exercise after two sessions without beating your best, with Heavy Duty
  advice (more rest, or back off ~10% and rebuild).
- **Log a past workout** from History, on any earlier day, with no timer.
- **This week** strip on Today and a **calendar** view in History.

## 1.8.0 (2026-10-25)
- Rest timer keeps running with the app closed or the phone locked: lock-screen countdown with
  +30s / Stop, and a "Rest is over" alert.

## 1.7.0 (2026-10-25)
- Post-workout summary, notes per exercise, warm-up sets, weight jump per exercise, Send feedback and
  crash-log sharing. Database v6.

## 1.6.0 (2026-10-24)
- Preview a workout before starting it; the clock only starts when you tap Start.

## 1.5.0 (2026-10-23)
- "Iron" redesign: near-black with an acid-lime accent, Barlow Condensed, big -/+ steppers.

## 1.4.0 (2026-10-22)
- Guided workout flow: pick a workout, then one exercise at a time, then review and log.

## 1.3.0 (2026-10-20)
- Progression targets (double progression), session clock, editing failure/forced reps in History.
  Database v5.

## 1.2.0 (2026-10-17)
- Heavy Duty features: recovery badge, to-failure and forced-reps logging, Principles screen.
  Database v4.

## 1.1.x (2026-09-22)
- Reliable self-updates from GitHub Releases, signed builds, backup & restore, CSV export, PRs computed
  live from history, and many logic fixes.
