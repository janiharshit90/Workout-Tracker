# Round 7: "Iron" visual redesign

Feedback: the guided flow was right, but the app still felt generic. This round is a **visual and input
redesign only**: same tracking, same data, **no database change**. Suggested tag: **v1.5.0**.

## Look
- Near-black with one acid-lime accent; orange-red is reserved for failure and warnings.
- Barlow Condensed (bundled, SIL Open Font License) for headings, big numbers and labels.
- Tight 4dp corners instead of pills and bubbles. Applies app-wide through the theme, so
  History / PRs / Settings pick it up too.

## Start screen
- Hero heading, a two-segment style switch (Heavy Duty one set / Several sets), big workout cards
  with a letter index, a lime "Suggested" card, and "Ready - 6 days rested" / "Recovering - 2 days left".

## During a workout
- The tab bar hides (focus mode).
- Big clock with a thin bar toward the 45-min target; turns red past it.
- Progress is one bar per exercise (lime done, white current, grey to do); tap to jump.
- **Target** block: "Add weight / Beat it / Build reps" with a big number and "Use weight".
  First time on an exercise it tells you what to find instead ("a weight you can take to failure in 6-10 reps").
- **Weight and reps now have -/+ steppers** under the fields (5 lb or 2.5 kg, 1 rep). A blank field jumps
  to the target first, then steps from there. Typing still works.
- Heavy Duty set: large fields and a full-width TO FAILURE button; forced reps stepper appears under it.
  Several-sets mode keeps compact rows with the flame icon.
