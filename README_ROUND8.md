# Round 8: preview before you start

Feedback: tapping a workout card started it (and its clock) immediately, so you couldn't look at it first
and browsing time inflated the logged duration. **No database change.** Suggested tag: **v1.6.0**.

## Flow now
1. **Start screen**: tap a workout card (or Custom workout). This only *opens* it.
2. **Preview**: the exercises, each with its Target (or "first time" hint), last session and a
   "recovering" flag. Add or remove exercises here. The **Heavy Duty / Several sets** switch lives here
   (it belongs to a specific workout). Back returns to the cards with nothing started.
3. **START WORKOUT**: this is the moment the workout begins and the clock starts.

The preview is kept in memory only: it survives rotation, but if the app is killed you land back on the cards.
A workout becomes real (and is saved for resume) only when you tap Start. The tab bar hides during the
preview as well as during the workout.
