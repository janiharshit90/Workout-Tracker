# Round 10: rest timer that works with the phone locked

**No database change.** Suggested tag: **v1.8.0** (installs as a normal in-app update).

## What changed
- The rest timer now keeps running when the app is closed or the phone is locked.
- While resting, a **lock-screen notification** shows a big live countdown, the exercise you just did, what's
  next (the next target), and **+30s / Stop** buttons. The countdown is drawn by Android itself, so it keeps
  ticking with the app closed.
- When rest ends, a **"Rest is over"** notification buzzes and chimes, even locked. Tapping it opens the app.
- On Android 13+ the first time you tap Start the app asks for notification permission. If you say no, the timer
  still works in the app and falls back to a buzz + chime.
- Timer state is wall-clock based and stored, so killing the app mid-rest doesn't lose it. Logging or
  discarding the workout clears any running timer.

## How it works
`timer/RestTimer.kt` owns the timer; an alarm (`setAlarmClock`, no special permission, not delayed by Doze)
fires `RestTimerReceiver` at the end time. The ViewModel only starts/stops it and shows the in-app countdown.
Because it uses an alarm, Android may show a small alarm icon in the status bar while resting.

## Not verified here
I can't run Android code in this environment, so the lock-screen look and the alert timing need a real-phone
check: start a rest, lock the phone, wait for the buzz; try +30s and Stop from the lock screen.
