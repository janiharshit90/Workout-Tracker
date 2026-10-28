# Launching Rarelift: Harshit's checklist

Everything code-side is done. These are the steps only you can do (they need your Google account,
ID and phone). Rough timeline: a day of setup, then **14 days of closed testing** that Google requires
for new personal accounts, then production.

## 1. Ship v2.0.0 to your current users (GitHub)
1. Merge the **Rename to Rarelift** pull request.
2. GitHub → **Actions → Release → Run workflow** → version `2.0.0`.
   The release notes in `docs/release-notes/v2.0.0.md` go out with it automatically.
3. Do the move yourself first (back up → install Rarelift → restore → uninstall old), then tell your
   friends. Their old app will also show them the steps when it sees the update.

## 2. Create your Google Play developer account (~₹2,100 / US$25, one time)
1. Go to <https://play.google.com/console/signup> with the Google account you want to own the app.
2. Choose **Personal** account. Have your government ID ready; Google verifies it, which can take
   a few days. Use a contact email you're happy to have public on the listing.
3. This account also covers Android's **developer verification**, which is what stops "unverified
   app" warnings for people who install outside the Play Store, and becomes mandatory worldwide in 2027.
   In the developer verification section, register the app ID **`com.rarelift.app`**.
   If it asks which signing keys you use outside Play, that's the keystore whose secrets are in
   GitHub (`KEYSTORE_BASE64` etc.).

## 3. Set up payouts
Play Console → **Setup → Payments profile**: add your bank account and tax details (PAN in India).
Google keeps **15%** of the first US$1M each year; you get the rest monthly.

## 4. Create the app
Play Console → **Create app**: name *Rarelift*, app (not game), **free**, accept the declarations.
- **Store listing**: copy from [`docs/play-store/listing.md`](play-store/listing.md); graphics in
  [`docs/play-store/graphics/`](play-store/graphics/). Take 4–6 screenshots on your phone.
- **App content**: answers in [`docs/play-store/app-content.md`](play-store/app-content.md).
- **Monetize → In-app products**: create **`rarelift_pro`** (exact ID) and set the price.
  Play only lets you create products after you've uploaded a build with billing, so do step 5 first
  if the button is greyed out.

## 5. Upload the build
Every release now also produces a **Play App Bundle**:
GitHub → Actions → the Release run → **Artifacts** → `Rarelift-play-<version>` → download → unzip →
`Rarelift-play-<version>.aab`.
- Play Console → **Testing → Closed testing → Create track** → upload the `.aab`.
- Let Google manage the app signing key (Play App Signing). The key from GitHub becomes your
  *upload key*. Keep `release.keystore` and its passwords backed up somewhere safe. Losing it means
  you can't ship updates to the GitHub edition.

## 6. Closed test: 12 testers, 14 days (required for new personal accounts)
- Add at least **12 testers** (their Google account emails) to the closed track. Your current
  friends using the app are perfect. They join via the opt-in link and install from Play.
- They must stay opted in for **14 days in a row**. Ask them to actually use it, since Google looks at
  engagement.
- Test the purchase: Play Console → **Setup → License testing** → add your email. License testers
  can buy Pro without being charged.

## 7. Go live
After 14 days: **Dashboard → Apply for production access**, answer the questions about your test,
then roll out the same build (or a newer one) to **Production**. Review usually takes a few days.

## After launch: making it earn
- Reply to every review in the first weeks; early ratings matter most.
- Post where HIT / Heavy Duty lifters hang out (Reddit r/HIT, r/bodybuilding threads, YouTube
  comments on HIT channels) with the share image from a real workout. That's the cheapest marketing
  there is.
- Watch **Statistics → Conversion** to see how many visitors install, and whether the store listing
  needs better screenshots.
- The GitHub edition stays free with everything unlocked (good for friends and goodwill). If it ever
  undercuts Play sales noticeably, it can be switched to the same Pro unlock or retired.
