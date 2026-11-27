# Help test TowerScope Live on Google Play

Thanks for thinking about joining the closed test. Here's what it involves, and why it would help me a lot.

## Why join

- **You get it first.** You'll have TowerScope Live from Google Play before it has a public listing.
- **You help shape it.** Tell me what's confusing, what's useful, and what you'd like next. With a small
  group, I really do read every note.
- **Credit, if you'd like it.** I'm happy to thank testers by name in the app and in the README. It's
  entirely up to you, and the default is no.
- **A free unlock.** I plan to give testers the full app for free, with Google Play promo codes or tester
  accounts. I'll send instructions once Play Console is set up: [PROMO CODE INSTRUCTIONS]. Until then,
  please don't spend real money on it.
- **You help me launch carefully.** This is my first app on Google Play. A clean, careful launch here
  teaches me how to ship the official apps I'll work on next, including the careful, honest tools I
  want to build in the Roman AI style.

## What you need

- An Android phone running Android 10 or newer, with a SIM card
- A Gmail or Google account signed in on that phone

## How to sign up

1. Send me the Gmail address you use on the phone, or join the Google Group: [GOOGLE GROUP ADDRESS AND INVITE].
2. I'll add you as a tester and send you the Play opt-in link: `PLAY_OPT_IN_LINK`.
3. Open that link on your phone, signed in to the same Google account, and accept the invitation to test.
4. Install **TowerScope Live** from Google Play using the link. Please don't install an APK from anywhere
   else for this test.
5. Keep it installed, and stay opted in, for **14 days**. Google asks new developers like me to run a closed
   test with at least 12 testers for 14 days in a row before an app can go public. If you leave early, I might have to restart that
   14-day clock, so please tell me if you can't stay. No hard feelings.
6. Open the app a few times over those two weeks. Try the tour, run a speed test, and, if you'd like,
   add a free OpenCelliD key so the app can find tower positions.
7. Send feedback whichever way is easiest:
   - the private feedback form for testers on the app's Google Play page
   - a [GitHub issue](https://github.com/garyleebairdjr-cmyk/towerscope-live/issues)
   - or write to me directly: [CONTACT EMAIL]

## What to try

You don't need to do all of this. Anything you notice helps.

- [ ] It installs from Google Play without trouble.
- [ ] The first screen explains why the app wants location, before Android asks you.
- [ ] Android words the optional phone permission as "make and manage phone calls". That's Android's
      label for it; TowerScope only reads the network type and 5G details, never calls. Allowing or
      denying it are both fine, and I'd like to hear if the wording put you off.
- [ ] The Overview shows your tower, with a distance and a direction.
- [ ] The Signal tab shows live readings and a chart that grows while the app is open.
- [ ] The Towers tab lists cells your phone can hear.
- [ ] A speed test runs, and asks first if you're on mobile data.
- [ ] Settings shows how many trial days are left.
- [ ] Unlock and Restore purchase work, but only if I've sent you a test purchase method.
- [ ] Nothing crashes with Wi-Fi and mobile data both off.
- [ ] You can find how to send me feedback.

If something looks wrong, a screenshot and a sentence about what you expected is perfect.

## Questions

**What does the Play edition collect?**
Nothing about you leaves your phone except tower lookups to OpenCelliD (only if you add a key) and speed
tests you start. It uses your location only while it's open, keeps no history of where you've been, and
has no ads or analytics. The details are in [PRIVACY.md](PRIVACY.md).

**How is this different from the open-source version on GitHub?**
The open-source edition has extras for power users: optional long-term history, export, and recording
with the app closed. The Play edition leaves those out on purpose so it stays simple and private. See
[EDITIONS.md](EDITIONS.md). You can have both installed at once.

**Will this cost me money?**
Not as a tester. For everyone else, the app will be free for 7 days, then a one-time $1.99 to keep using
it. There's no subscription.

**How do I leave after the 14 days?**
Open the opt-in link again and choose to leave the test, then uninstall the app if you want to. If you
haven't unlocked it, uninstalling may reset the trial.

**Something's broken. Should I keep going?**
Yes, please, and tell me about it. Finding broken things is exactly what this test is for.

## On iPhone: TestFlight

The iPhone version will be tested through Apple's TestFlight. It's the same idea, with a few differences
from Google Play:

- **No 14-day rule.** Apple doesn't require a set number of testers or days. Stay as long as you like.
- **What you need:** an iPhone and an Apple Account. You install through Apple's free **TestFlight** app,
  not from a file.
- **How you join:** I'll either email you an invite or send a public link: [TESTFLIGHT PUBLIC LINK]. Open it
  on your iPhone, install TestFlight if asked, then tap **Accept** and **Install**.
- **Two kinds of testers.** Up to 100 people on the developer team can test right away (internal testers,
  each on up to 30 devices). Everyone else is an external tester, up to 10,000 per app. The first build for
  external testers goes through a short TestFlight review by Apple, so there may be a day or two of waiting
  before your invite works.
- **Who's likely first:** Art, who runs the Apple developer account the app is published under, and Kaleb.
  If you'd like to join them, tell me.

**Please know before you start:** iPhones don't let any app see which tower the phone is connected to, or
read signal strength. On iPhone, TowerScope shows the towers recorded near you, the distance and direction to
the nearest one, your network type, and a full speed test. That's expected, not a bug. See [IOS.md](IOS.md).

### What to try on iPhone

- [ ] Before anything else, the app explains the 7-day trial, what happens when it ends, and the price.
- [ ] Starting the trial works, and Settings shows the days left.
- [ ] The location explanation appears before iOS asks, and iOS only offers "While Using the App".
- [ ] "Not now" still lets you run a speed test.
- [ ] With an OpenCelliD key, the Overview shows the nearest known tower with distance and direction.
- [ ] The Towers tab opens on the database list.
- [ ] Unlock and Restore purchase work in TestFlight. TestFlight purchases are free; you won't be charged.

Feedback works the same way: TestFlight's own feedback (take a screenshot in the app and share it to
TestFlight), a [GitHub issue](https://github.com/garyleebairdjr-cmyk/towerscope-live/issues), or
[CONTACT EMAIL].

Thank you. It means a lot.

Gary
