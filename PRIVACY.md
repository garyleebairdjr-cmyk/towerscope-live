# TowerScope Live privacy policy (Google Play edition)

**Draft.** Effective date: [EFFECTIVE DATE]

This policy covers **TowerScope Live** as distributed on Google Play (package `io.towerscope.live`). The
open-source edition you can build or download from GitHub works differently; see [EDITIONS.md](EDITIONS.md).

## Who I am

I'm Gary Lee Baird Jr., and I make TowerScope Live on my own. If you have a question about this policy or
about your data, write to me at [CONTACT EMAIL].

## The short version

- TowerScope Live shows the cell tower your phone is using, how strong the signal is, and how fast your
  connection is.
- It uses your location only while the app is open on your screen. It never runs in the background.
- It doesn't keep a history of where you've been, and it has no way to export one.
- It doesn't need an account, shows no ads, and has no analytics or tracking code.
- I don't sell personal data. I don't receive your readings at all: they stay on your phone.

## Permissions and why the app asks for them

The app explains each permission on its own screen first, and Android only asks after you tap **Continue**.

| Permission | What it's used for |
|---|---|
| Location (precise) | Android only tells an app which cell tower the phone is connected to when the app has precise location. TowerScope also uses your position to work out the distance and direction to the tower. Used only while the app is open. |
| Phone (`READ_PHONE_STATE`) | Optional. Android's dialog calls this "make and manage phone calls", but TowerScope only reads the network type (for example LTE or 5G), whether 5G is active, and carrier-aggregation bandwidths. It is never used to read your phone number, calls, contacts or call log, and never to identify you. The app works without it, with fewer details. |
| Internet and network state | Tower lookups (if you add an OpenCelliD key), speed tests you start, and telling Wi-Fi from mobile data. |
| Google Play billing | The one-time unlock described below. |

The Play edition does **not** request background location, does not run a background service, and does
not keep long-term location history.

## What stays on your phone

- Your settings, including the OpenCelliD API key if you add one. The key is excluded from Android's
  cloud backup.
- Live readings and speed-test results for the current session, such as the signal chart on the Signal
  tab. These are kept in memory and are gone when the app closes.
- Any "best spot" readings you choose to save: the name you give the spot, its average signal, and the
  time. Saved spots don't include a location.
- The date you first opened the app, for the free trial, and a note that you've unlocked the app.

All of this is removed when you uninstall the app or clear its storage in Android settings.

## Services the app talks to

**OpenCelliD (optional).** If you add your own free OpenCelliD API key, the app sends the identifiers of
the cells your phone can hear (country and network codes, area code and cell ID) to opencellid.org to get
the towers' positions. To list nearby towers, it also sends a square about 2 km across, centred on your
position, which reveals roughly where you are. Nothing is
sent to OpenCelliD unless you add a key. Tower data comes from [OpenCelliD](https://opencellid.org) and is
licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). OpenCelliD's own privacy
policy applies to those requests.

**Speed test (only when you start one).** The test downloads and uploads test data from
`speed.cloudflare.com`. Like any website, Cloudflare sees your IP address when you do this. If you're on
mobile data, the app asks before it starts, because a test can use a fair amount of data.

**Google Play.** Google Play handles installing and updating the app, and the one-time purchase.

## Free trial and the one-time unlock

- The app is free for 7 days from the first time you open it. After that, a one-time purchase of $1.99
  (or the price Google Play shows in your currency) unlocks it for good. There's no subscription.
- Google Play processes the payment. I never see your full card or payment details.
- The trial start date is stored only on your phone. Uninstalling the app may reset the trial.
- If you've bought the unlock, it's tied to your Google account, and the app restores it from Google Play
  when you reinstall it or use a new phone signed in to the same account.

## Children

TowerScope Live is a technical tool for people who want to understand their mobile signal. It isn't
aimed at children, and I don't knowingly collect information from anyone, children included.

## Changes to this policy

If the app starts handling data differently, I'll update this policy and the effective date above before
that version goes out.

## Contact

[CONTACT EMAIL]
