# TowerScope Live on iPhone

This is my plan and checklist for getting TowerScope Live onto the App Store. The code is ready to build on
a Mac; the Apple account steps below still need doing.

## What the iPhone version can and can't do

I want to be upfront about this, because it's very different from Android.

Apple doesn't give apps a way to see which cell tower an iPhone is connected to, the cell ID, the
neighbouring cells, or signal strength (RSRP, RSRQ, SINR and the rest). The carrier details Apple used to
offer (`CTCarrier`) were deprecated in iOS 16 with no replacement. What an app can still read is the network
type, such as LTE or 5G. References:
[CTTelephonyNetworkInfo](https://developer.apple.com/documentation/coretelephony/cttelephonynetworkinfo) and
[serviceSubscriberCellularProviders (deprecated)](https://developer.apple.com/documentation/coretelephony/cttelephonynetworkinfo/servicesubscribercellularproviders).

So on iPhone, TowerScope Live offers:

- Your location while the app is open, to find the towers recorded near you in OpenCelliD and show how far
  away they are and which way to face (with the compass)
- Your network type (LTE, 5G and so on), when iOS reports it
- The full speed test (it asks before using mobile data)
- A clear note in the app that it can't see your serving tower or live signal, and that the Android app can

It does not offer, and doesn't pretend to:

- The tower you're actually connected to, cell ID, PCI or TAC
- Live signal strength or quality
- A list of cells the phone can hear
- Background location or long-term history (the same as the Google Play edition)

I won't use private APIs, Field Test Mode tricks, or anything else to get around this.

## How it compares

| | iPhone (App Store) | Android (Google Play) | Android (open source) |
|---|---|---|---|
| Price | Free 7-day trial, then $1.99 once | Free 7-day trial, then $1.99 once | Free |
| Identifies your serving tower | No (iOS doesn't allow it) | Yes | Yes |
| Live signal strength | No | Yes | Yes |
| Nearby towers from OpenCelliD | Yes | Yes | Yes |
| Distance and direction | To the nearest known tower | To your serving tower | To your serving tower |
| Speed test | Yes | Yes | Yes |
| Location | Precise, only while the app is open | Precise, only while the app is open | Precise, plus optional background recording |
| History and export | No | No | Optional |
| Bundle or package ID | `io.towerscope.live` | `io.towerscope.live` | `io.towerscope.android` |

More in [EDITIONS.md](EDITIONS.md).

## Building it

This Ubuntu machine can't build an iPhone app; that takes a Mac with Xcode, or the macOS job on GitHub
Actions (`.github/workflows/ios.yml`). On Linux I can only check that the shared Kotlin code compiles
(`./gradlew :shared:compileKotlinMetadata :shared:compileIosMainKotlinMetadata :shared:testAndroidHostTest`).

On a Mac:

```bash
brew install xcodegen
cd iosApp
echo 'DEVELOPMENT_TEAM = YOURTEAMID' > Signing.local.xcconfig   # gitignored
xcodegen                                      # generates TowerScope.xcodeproj
open TowerScope.xcodeproj                     # pick an iPhone, then Run
```

- **Xcode version.** Since April 28, 2026, App Store uploads must be built with Xcode 26 or later and the iOS 26
  SDK ([Apple](https://developer.apple.com/news/upcoming-requirements/?id=04282026a)). The app still runs on
  iOS 16 and newer; that's only the deployment target.
- **Java:** the Xcode build step runs Gradle, so a JDK must be visible to Xcode (for Homebrew's `openjdk@21`,
  link it into `/Library/Java/JavaVirtualMachines`). Plain-language steps for building it yourself are on the
  wiki's [Installing on iPhone](https://github.com/garyleebairdjr-cmyk/towerscope-live/wiki/Installing-on-iPhone) page.
- **Bundle ID:** `io.towerscope.live`. **Team ID:** `YOUR_APPLE_TEAM_ID` in git, the real one only in
  `Signing.local.xcconfig`.
- **Testing purchases without App Store Connect.** The Run scheme uses `iosApp/TowerScope/TowerScope.storekit`,
  a local StoreKit configuration with both products. In Xcode, Debug, StoreKit, Manage Transactions lets you
  refund, delete or expire test purchases. A real sandbox purchase needs the products created in App Store
  Connect first.
- **Encryption.** `Info.plist` sets `ITSAppUsesNonExemptEncryption` to false: the app only uses the HTTPS
  built into iOS.

## The account: Art's team, and I publish

I'm not enrolling in the Apple Developer Program myself. Art (Carlos Nevarez) already has an Apple Developer
account with two apps on it. He'll add me to his team, and I'll do the App ID, App Store Connect record,
signing, TestFlight and submission work using his Team ID. The code stays in my GitHub repo.

**The seller name** on the App Store will be whatever is on Art's enrollment: his legal name if it's an
Individual account, or his organization's legal name if it's an Organization. It won't be my name unless we
change the account later.

### Check this first: Individual or Organization?

Apple treats the two very differently
([accounts and roles](https://developer.apple.com/help/app-store-connect/manage-your-team/overview-of-accounts-and-roles)):

- **Organization:** Art can add me as a full team member, including Certificates, Identifiers & Profiles,
  which I need to create the App ID, certificates and profiles.
- **Individual:** Art can invite people into App Store Connect, but they aren't on the developer team and
  don't get Certificates, Identifiers & Profiles. If Art's account is Individual, I can't do the signing work
  myself until he converts it to an Organization (Apple says to contact them) or we find another arrangement.

### The role to ask for

- **Preferred: Admin.** On an Organization team, Admin includes Certificates, Identifiers & Profiles, can
  manage all apps, and can run TestFlight
  ([role permissions](https://developer.apple.com/help/app-store-connect/reference/account-management/role-permissions/)).
- **Also workable: App Manager plus the Certificates, Identifiers & Profiles permission.** The catch, which Art
  should know before granting it: Apple says people with that permission can see all app information on the
  team and can't be limited to one app, so I'd be able to see his two other apps too
  ([add and edit users](https://developer.apple.com/help/app-store-connect/manage-your-team/add-and-edit-users/)).
- I need two-factor authentication on the Apple Account I use to accept the invite.

### What only Art can do (Account Holder)

- Sign and renew the **Paid Apps agreement**, which has to be active before the $1.99 unlock can be sold
  ([agreements](https://developer.apple.com/help/app-store-connect/manage-agreements/sign-and-update-agreements/)).
- Banking, tax forms and payouts. **Apple pays the $1.99 proceeds to Art's account, not mine.**
- Renew the membership ($99 a year, [Apple Developer Program](https://developer.apple.com/programs/)).
- Turn on App Store Connect API access, if it isn't on, before anyone can create a Team API key for CI.

**A decision for Art and me, not for the code:** who keeps the App Store proceeds, and whether and how we
share them. Apple will pay Art's account. The app can't split payments, so we'll agree this between us.

## The trial and the unlock (StoreKit 2)

The iPhone offer matches Google Play: free to download, 7 days of everything, then a one-time $1.99 unlock.

Apple has no built-in free trial for a one-time purchase, but App Store Review Guideline 3.1.1 allows a free,
time-based trial for a non-subscription app through a free (Price Tier 0) non-consumable named "XX-day Trial"
([guidelines](https://developer.apple.com/app-store/review/guidelines/)). So there are two products:

| Product ID | Type | Price | Reference name |
|---|---|---|---|
| `towerscope_7_day_trial` | Non-consumable | Free (Tier 0) | 7-day Trial |
| `towerscope_unlock` | Non-consumable | $1.99 (US) | TowerScope Live Unlock |

How it works in the app (`iosApp/TowerScope/StoreKitBridge.swift`):

- On first launch, the app shows what the trial includes **before** it starts: it lasts 7 days, the app stops
  working when it ends until you unlock it, and the full version is a one-time purchase at the price from the
  App Store (shown in the local currency). It says it isn't a subscription and nothing renews.
- "Start my free 7-day trial" gets the free trial product. Its purchase date is the start of the trial, so it
  follows the person's Apple Account.
- If the trial product isn't available, the app falls back to a start date kept on the device (Keychain and
  UserDefaults). That can be reset on a new device, and the paywall says so. I don't claim it can't be reset.
- The app is unlocked whenever `Transaction.currentEntitlements` includes `towerscope_unlock`. It listens to
  `Transaction.updates` and finishes verified transactions. **Restore purchase** is always on the paywall.

The text the paywall shows before the trial:

> Everything in the app is included during the trial. It lasts 7 days from when you start it. When it ends,
> the app stops working until you unlock it with a one-time purchase of $1.99. This is not a subscription,
> and nothing renews or charges you automatically.

## Privacy

- `Info.plist` asks only for location **when in use**. There's no "always" purpose string and no background
  location mode.
- The purpose string says what's true: "While the app is open, TowerScope uses your location to find the
  cell towers recorded near you in OpenCelliD and show how far away they are and which way to face. iPhones
  don't let apps read which tower you're connected to."
- `iosApp/TowerScope/PrivacyInfo.xcprivacy` declares precise location, used for app functionality only, not
  linked to identity, no tracking. It's declared as precise because the OpenCelliD area search is centred on
  your position. TODO: compare its required-reason API entries with Xcode's privacy report on the first archive.
- **App Privacy answers in App Store Connect** should match ([App privacy details](https://developer.apple.com/app-store/app-privacy-details/)):
  Precise Location, App Functionality, not linked to you, not used for tracking. Purchases go through Apple.
  Nothing else is collected. See [PRIVACY.md](PRIVACY.md).

## Signing on CI (later, never in git)

- `iosApp/fastlane/` has placeholder `Appfile`, `Matchfile` and `Fastfile`. Certificates and profiles live in
  a **private** repo managed by [fastlane match](https://docs.fastlane.tools/actions/match/), never in this
  public one. The first `match` run happens once on a Mac after I'm on Art's team.
- Use a **Team** App Store Connect API key, not an Individual key: fastlane notes that Individual keys can't
  call the provisioning endpoints match needs ([fastlane](https://docs.fastlane.tools/app-store-connect-api/)).
- GitHub secrets on `garyleebairdjr-cmyk/towerscope-live`, once they exist:

| Secret | What it is |
|---|---|
| `ASC_KEY_ID` | App Store Connect API key ID |
| `ASC_ISSUER_ID` | App Store Connect issuer ID |
| `ASC_KEY_P8_BASE64` | The `.p8` key file, base64-encoded |
| `MATCH_PASSWORD` | Encryption password for the match repo |
| `MATCH_GIT_URL` | URL of the private certificates repo |
| `MATCH_GIT_BASIC_AUTHORIZATION` | base64 of `username:token` with read access to that repo |
| `APPLE_TEAM_ID` | Art's Team ID |

- Real keys never go in git, in a gist, or in a chat. The `.gitignore` blocks `.p8`, `.p12`, `.cer` and
  provisioning profiles.
- The iOS workflow's simulator job needs none of these. The TestFlight job only runs when started by hand,
  and skips with a notice if any secret is missing.

## TestFlight versus Google Play testing

They work differently, and Apple has no 12-tester, 14-day rule (that's a Google Play requirement for new
personal accounts). Details are in [TESTERS.md](TESTERS.md).

## Checklist (Gary, and Art where noted)

1. Confirm whether Art's account is an **Organization**. If it's Individual, Art needs to convert it (or we
   need another plan) before I can get Certificates, Identifiers & Profiles.
2. Art sends me a team invite. I accept with my own Apple Account, with two-factor authentication on
   ([invite team members](https://developer.apple.com/help/account/access/invite-team-members/)).
3. I ask Art for **Admin**, or **App Manager** plus Certificates, Identifiers & Profiles. I remind him that
   this lets me see all apps on the team, including his two.
4. I put Art's Team ID in `iosApp/Signing.local.xcconfig` (git keeps `YOUR_APPLE_TEAM_ID`).
5. I create the App ID `io.towerscope.live` (or whatever we finally agree) on Art's team, and the App Store
   Connect app record. The seller is Art's enrollment name.
6. Art, as Account Holder, signs the Paid Apps agreement and finishes banking and tax, if they aren't already
   in place for his two apps.
7. Art and I agree what happens to the $1.99 proceeds. Apple pays Art's account; the app can't split it.
8. With Certificates, Identifiers & Profiles access, I create the certificates and profiles with fastlane
   match and a **private** certs repo.
9. Art turns on App Store Connect API access if needed. I create a **Team** API key, download the `.p8` once,
   and add the secrets above to GitHub. Never pasted into a chat
   ([creating API keys](https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api)).
10. I create the two In-App Purchase products once the Paid Apps agreement is active, then test with sandbox
    and TestFlight on a real iPhone.
11. Internal TestFlight first, then an external group (its first build goes through TestFlight App Review),
    then App Review for the public listing.
12. I fill in the App Privacy answers, check the location purpose string, and submit when the build is honest
    and complete.
