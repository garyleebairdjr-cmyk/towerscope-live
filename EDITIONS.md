# Editions of TowerScope Live

TowerScope Live comes in two Android editions built from the same code, plus an iPhone version being
prepared for the App Store. They look the same and share the same screens. On Android, the difference is how
much they keep about you and how you get them. On iPhone, Apple limits what any app can read, so it does less.

## The Play edition: simple and private

Most people want three things from an app like this: to see the tower they're on, to understand their
signal, and to run a speed test. They don't need, and often don't want, a precise trail of where they've
been. The Play edition is built for them.

- Uses your location only while the app is open on your screen.
- No background tracking, no long-term location history, nothing to export.
- Free for 7 days, then a one-time $1.99 unlock. No subscription, no ads, no account.
- Installs and updates through Google Play like any other app.

## The open-source edition: for power users and researchers

Some people want more: a record of signal and speed over weeks, exact GPS on each reading, a spreadsheet
they can analyse, or readings collected while the phone sits in a pocket. The open-source edition does
all of that, and every extra is something you switch on yourself.

- Precise GPS distance and direction, as in the Play edition.
- Optional on-phone history (off until you turn it on), with exact, approximate or no location on each reading.
- Optional export of that history as a CSV spreadsheet, through Android's share sheet.
- Optional recording with the app closed. It shows a notification the whole time, with a Stop button.
- Free. You build it yourself or install the APK from GitHub. It is not the Play listing.

## The iPhone edition: what Apple allows

Apple doesn't let any iPhone app see which cell tower the phone is using, or read signal strength. So the
iPhone version can't identify your serving tower. It shows the towers recorded near you in OpenCelliD, with
distance and direction to the nearest one, your network type (LTE, 5G and so on), and the full speed test.
It follows the Play edition's privacy rules and price. The details are in [IOS.md](IOS.md).

## Side by side

| | Android, Play edition | Android, open source | iPhone, App Store |
|---|---|---|---|
| Price | Free 7-day trial, then $1.99 once | Free | Free 7-day trial, then $1.99 once |
| Where to get it | Google Play | [GitHub releases](https://github.com/garyleebairdjr-cmyk/towerscope-live/releases/latest) or build from source | App Store (being prepared) |
| Package or bundle ID | `io.towerscope.live` | `io.towerscope.android` | `io.towerscope.live` |
| App name on your phone | TowerScope Live | TowerScope Live (Open Source) | TowerScope Live |
| Identifies the tower you're connected to | Yes | Yes | No, iOS doesn't allow it |
| Live signal strength and charts | Yes | Yes | No, iOS doesn't allow it |
| Nearby towers from OpenCelliD | Yes | Yes | Yes |
| Distance and direction | To your serving tower | To your serving tower | To the nearest known tower |
| Speed test | Yes | Yes | Yes |
| Location | Precise, only while the app is open | Precise, only while the app is open (unless you turn on background recording) | Precise, only while the app is open |
| Background recording | Not available | Optional, off by default, with a notification while it runs | Not available |
| Long-term history | Not available | Optional, off by default, up to 90 days on the phone | Not available |
| Location kept with history | Not applicable | Your choice: exact, approximate (about 100 m) or none | Not applicable |
| Google backup of history | Not applicable | Optional, off by default | Not applicable |
| Export | Not available | Optional CSV export | Not available |
| Billing | Google Play, one-time unlock | None | App Store, one-time unlock |
| Radar, diagnosis, better-signal guide | Yes | Yes | Radar and guide; diagnosis is limited without signal readings |

Both Android editions can be installed on the same phone at the same time, because they have different
package names. Settings and API keys aren't shared between them.

## Why precise location in both

Android only tells an app which cell tower the phone is connected to when the app has precise location
permission. Without it, TowerScope couldn't identify your tower, look it up, or point you at it. Both
editions use it only while the app is open. The open-source edition can keep using it while the app is
closed, but only when you turn on background recording, which shows a notification for as long as it runs.

## The phone permission

Both editions ask for the phone permission (`READ_PHONE_STATE`), and it's optional in both. Android needs it
for the network type, whether 5G is active, and carrier-aggregation bandwidths. Android's dialog calls it "make and manage phone calls", but
TowerScope never uses it to read your number, calls or contacts, and it works without it, with fewer details.

## Privacy

- Play edition and iPhone: [PRIVACY.md](PRIVACY.md)
- Both editions: [Privacy and Permissions](https://github.com/garyleebairdjr-cmyk/towerscope-live/wiki/Privacy-and-Permissions) on the wiki
