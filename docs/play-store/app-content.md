# Play Console "App content" answers: Rarelift (Play edition)

These are the forms under **Policy and programs → App content**. The answers below match what the
Play edition actually does. If the app changes (analytics, accounts, cloud sync, ads), revisit them.

## Privacy policy
`https://github.com/janiharshit90/Workout-Tracker/blob/main/PRIVACY.md`
(The repo must stay public for this link to work. A simple website works too.)

## Ads
**No**, the app does not contain ads.

## App access
**All functionality is available without special access** (no login). For review, the Pro features
are behind an in-app purchase; Google's reviewers can use license testing.

## Content rating
Complete the questionnaire as a **utility / productivity / fitness** app: no violence, no sexual
content, no gambling, no user-generated content shared with others, no location sharing.
Expected result: rated for everyone (e.g. IARC 3+).

## Target audience
**18 and over** is simplest. (Choosing ages under 13 brings Families policy requirements.)

## Data safety
Google's definition: data is "collected" only if it's sent **off the device**. Rarelift keeps all
workout and body-weight data on the device, so:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (none collected) |
| Do you provide a way for users to request that their data is deleted? | Data is on-device; uninstalling deletes it |

Notes:
- Purchases are processed by Google Play Billing. The app only reads whether Pro is owned.
- Backups, CSV, share images and feedback leave the device **only when the user sends them**, through
  apps the user picks. Google treats user-initiated sharing like this as not "collected".
- If you ever add crash reporting (e.g. Firebase Crashlytics), analytics or ads, this changes to
  **Yes** and you must declare those data types.

## Health apps declaration
Play asks apps in Health & Fitness to fill in a health declaration. Choose the
**fitness / workout tracking** feature, with no medical, diagnostic or health-research features,
and no Health Connect access.

## Government apps / Financial features / News
Not applicable.
