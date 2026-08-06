# Velocity Ads – AppLovin MAX Adapter (Android)

This library is the official AppLovin MAX **custom-network adapter** for the Velocity Ads Android SDK. It lets MAX mediate Velocity Ads demand alongside all other networks in your waterfall with zero boilerplate in your app.

---

## Supported ad formats

| Format | Supported |
|---|---|
| Interstitial | ✅ |
| Rewarded | ✅ |
| Native | ✅ |
| Banner / MREC | ❌ |

---

## Requirements

| Requirement | Minimum version |
|---|---|
| Android | API 24 (Android 7.0) |
| AppLovin MAX SDK | 13.x |
| Velocity Ads SDK | 0.10.0 |
| Kotlin | 1.9+ |

---

## Installation

### 1. Add the adapter dependency

Once published to Maven Central, add the adapter to your app's `build.gradle`:

```groovy
dependencies {
    // AppLovin MAX SDK (already present in most apps)
    implementation 'com.applovin:applovin-sdk:13.+'

    // Velocity Ads SDK
    implementation 'io.velocity:ads-sdk:0.10.0'

    // Velocity Ads MAX Adapter
    implementation 'io.velocity:ads-sdk-max-adapter:0.10.0.0'
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

### 2. Local / development builds

If you are developing against a local Velocity SDK build, add `mavenLocal()` **first** in your repositories block so Gradle picks it up before checking remote repositories.

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
3. Set the **App Key** in **Server Parameters** (JSON):
   ```json
   { "app_key": "YOUR_VELOCITY_APP_KEY" }
   ```
4. Set the **Placement ID** to your Velocity ad unit ID. This is passed to the adapter as `thirdPartyAdPlacementId`.

---

## Privacy & Consent

The adapter automatically forwards MAX's consent signals to the Velocity SDK before every ad request:

| MAX signal | Velocity Ads API |
|---|---|
| `parameters.hasUserConsent` | `VelocityAds.setConsent(Boolean)` – `true` = consent granted (GDPR) |
| `parameters.isDoNotSell` | `VelocityAds.setDoNotSell(Boolean)` – `true` = opt-out (CCPA) |

No additional integration is required in your app. If a signal is `null` (not set), it is not forwarded and the Velocity SDK retains its previous value.

---

## SDK Initialization

The adapter initializes the Velocity SDK automatically the first time MAX calls `initialize()`. You do **not** need to call `VelocityAds.initSDK()` yourself. The `app_key` is read from the server parameters you configured in the MAX dashboard.

If the SDK is already initialized (e.g. you initialize it directly in your app), the adapter detects this and reports `INITIALIZED_SUCCESS` immediately.

---

## Version history

| Adapter version | Velocity SDK version | Notes |
|---|---|---|
| 0.10.0.0 | 0.10.0 | Initial release |

---

## License

Apache License 2.0 – see [LICENSE](LICENSE).
