# Two editions of TowerScope Live

TowerScope Live comes in two editions built from the same code. They look the same and share the same
screens. The difference is how much they keep about you, and how you get them.

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

## Side by side

| | Play edition | Open-source edition |
|---|---|---|
| Price | Free 7-day trial, then $1.99 once | Free |
| Where to get it | Google Play | [GitHub releases](https://github.com/garyleebairdjr-cmyk/towerscope-live/releases/latest) or build from source |
| Package name | `io.towerscope.live` | `io.towerscope.android` |
| App name on your phone | TowerScope Live | TowerScope Live (Open Source) |
| Location | Precise, only while the app is open | Precise, only while the app is open (unless you turn on background recording) |
| Background recording | Not available | Optional, off by default, with a notification while it runs |
| Long-term history | Not available | Optional, off by default, up to 90 days on the phone |
| Location kept with history | Not applicable | Your choice: exact, approximate (about 100 m) or none |
| Google backup of history | Not applicable | Optional, off by default |
| Export | Not available | Optional CSV export |
| Billing | Google Play, one-time unlock | None |
| Live signal charts, radar, tower lookup, speed test, diagnosis | Yes | Yes |

Both editions can be installed on the same phone at the same time, because they have different package
names. Settings and API keys aren't shared between them.

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

- Play edition: [PRIVACY.md](PRIVACY.md)
- Both editions: [Privacy and Permissions](https://github.com/garyleebairdjr-cmyk/towerscope-live/wiki/Privacy-and-Permissions) on the wiki
