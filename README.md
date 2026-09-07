# Velocity Ads – AppLovin MAX Adapter (Android)

This library is the official AppLovin MAX **custom-network adapter** for the Velocity Ads Android SDK. It lets MAX mediate Velocity Ads demand alongside all other networks in your waterfall with zero boilerplate in your app.

---

## Supported ad formats

| Format | Supported |
|---|---|
| Interstitial | ✅ |
| Rewarded | ✅ |
| Banner / MREC / Leaderboard | ✅ |

---

## Requirements

| Requirement | Minimum version |
|---|---|
| Android | API 24 (Android 7.0) |
| AppLovin MAX SDK | 13.x |
| Velocity Ads SDK | 0.10.0 |
| Kotlin | 2.0+ |

---

## Installation

Add the adapter to your app's `build.gradle`:

```groovy
dependencies {
    // AppLovin MAX SDK (already present in most apps) — 13.0.1 is the version this
    // adapter is built and tested against; any 13.x release is compatible.
    implementation 'com.applovin:applovin-sdk:13.0.1'

    // Velocity Ads SDK
    implementation 'io.velocity:ads-sdk:0.10.0'

    // Velocity Ads MAX Adapter
    implementation 'io.velocity:max-mediation:0.10.0.0'
}
```

Make sure the AppLovin Maven repository is in your repository list:

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url "https://artifacts.applovin.com/android" }
    }
}
```

---

## MAX Dashboard Setup

### Step 1 – Create a Custom Network

1. In the MAX dashboard, go to **Mediation → Manage → Networks**.
2. Click **Click here to add a Custom Network**.
3. Set the following:
   - **Network Type**: `SDK`
   - **Name**: `Velocity Ads`
   - **Android Adapter Class Name**: `com.applovin.mediation.adapters.VelocityAdsMediationAdapter`
   - **iOS Adapter Class Name**: *(leave empty or set the iOS adapter class if applicable)*

### Step 2 – Create Ad Units

For each placement you want Velocity Ads to fill:

1. Go to **Mediation → Manage → Ad Units** and open (or create) the ad unit.
2. In the **Custom Networks & Deals** section, add **Velocity Ads**.
3. Set the **Placement ID** to your Velocity ad unit ID for that placement.
4. In the **App ID** field, enter your Velocity Ads app key. MAX delivers this value
   to the adapter as `serverParameters["app_id"]`. Use **one Velocity app key per
   application process** across all placements.

---

## Privacy & Consent

The adapter automatically forwards the device's AppLovin privacy state to the Velocity SDK before every ad request. It reads directly from `AppLovinPrivacySettings` — the authoritative Android source that the publisher (or their CMP) sets — rather than from per-request MAX adapter parameters:

| AppLovin privacy source | Velocity Ads API |
|---|---|
| `AppLovinPrivacySettings.hasUserConsent(context)` (forwarded only when `isUserConsentSet` is `true`) | `VelocityAds.setConsent(Boolean)` – `true` = consent granted (GDPR) |
| `AppLovinPrivacySettings.isDoNotSell(context)` (forwarded only when `isDoNotSellSet` is `true`) | `VelocityAds.setDoNotSell(Boolean)` – `true` = opt-out (CCPA) |

No additional integration is required in your app beyond the standard AppLovin privacy setup. A signal that has not been set is not forwarded, and the Velocity SDK retains its previous value. Privacy is forwarded at initialization (before the SDK boots) and on every ad load, so mid-session consent changes propagate on the next request.

---

## Mediation environment reporting

At initialization the adapter reports the mediation environment to the Velocity SDK via `VelocityAdsMediationBridge.setMediationInfo(name, adapterVersion, sdkVersion)`:

| Field | Value |
|---|---|
| Mediation name | `"max"` |
| Adapter version | This adapter's version (e.g. `0.10.0.0`) |
| Mediation SDK version | The AppLovin SDK version (`AppLovinSdk.VERSION`) |

The Velocity SDK attaches these values to every ad request and every analytics event, so traffic can be sliced by mediation platform, adapter version, and AppLovin SDK version. Forwarding happens once per process — the values never change mid-session.

---

## SDK Initialization

The adapter initializes the Velocity SDK automatically the first time MAX calls `initialize()`. You do **not** need to call `VelocityAds.initSDK()` yourself. The app key is read from the **App ID** field you configured in the MAX Custom Network dashboard entry.

Use **one Velocity app key per application process**. The Velocity SDK initializes once for the process; configuring different App ID values across MAX placements in the same app is unsupported.

If the SDK is already initialized (e.g. you initialize it directly in your app), the adapter detects this and reports `INITIALIZED_SUCCESS` immediately.

---

## Version history

| Adapter version | Velocity SDK version | Notes |
|---|---|---|
| 0.10.0.0 | 0.10.0 | Initial release |

---

## Release process

> **SDK-first requirement**: `io.velocity:ads-sdk:<version>` must be published to Maven Central before this adapter can be released. CI will fail until the matching SDK version ships.

### Prerequisites

Set the following secrets at the **`velocityiodev` org level** (shared automatically with all adapter repos; values are identical to those used by the SDK repos):

| Secret | Purpose |
|---|---|
| `SIGNING_KEY_ID` | Short GPG key ID (last 8 hex characters of the fingerprint) for Maven artifact signing — identical to the SDK repo |
| `SIGNING_KEY` | Base64-encoded binary GPG secret key (`gpg --export-secret-keys <ID> \| base64`, newlines stripped) — identical to the SDK repo |
| `SIGNING_PASSWORD` | Passphrase for `SIGNING_KEY` — identical to the SDK repo |
| `CENTRAL_PORTAL_TOKEN_USER` | Maven Central Portal user token (username half) |
| `CENTRAL_PORTAL_TOKEN_PASSWORD` | Maven Central Portal user token (password half) |
| `GPG_PRIVATE_KEY` | GPG private key for git tag signing (armored) |
| `GPG_PASSPHRASE` | Passphrase for `GPG_PRIVATE_KEY` |
| `GPG_TAGGER_NAME` | Display name for signed git tags |
| `GPG_TAGGER_EMAIL` | Email for signed git tags |
| `GPG_SIGNING_KEY_ID` | Full-length GPG key fingerprint for tag signing |

### Steps

1. On a release branch (`release/<version>`, e.g. `release/0.10.0.0`):
   - Bump `VERSION_NAME` in `gradle.properties`.
   - Add a `## <version>` entry to `CHANGELOG.md`.
2. Push the branch and open a draft PR for review.
3. **After the SDK version is on Maven Central**, go to **Actions → Release – Publish to Maven Central** and click **Run workflow**:
   - **Branch**: your release branch (`release/<version>` — enforced by the workflow).
   - **Version**: the 4-segment version, e.g. `0.10.0.0`.
   - **Dry run**: `true` for a first check (stages to Maven Central, skips tag/release); `false` for the real release.
4. If the dry run passes, drop the staging repository from the [Maven Central Portal](https://central.sonatype.com/) and re-run with **Dry run = false**.
   The **Publish to Maven Central** job requires approval from the GitHub Environment **`production-release`** before `closeAndRelease` (configure required reviewers under **Settings → Environments** before the first release). Tagging runs only after that publish succeeds.
5. Merge the release PR after the workflow succeeds.

---

## License

Apache License 2.0 – see [LICENSE](LICENSE).
