# Changelog

## [Unreleased]

### Fixed

* Reduced log noise when the app initializes the Velocity SDK itself.

## [0.10.0.0] - 2026-09-07

### Added

* Initial release of the Velocity Ads AppLovin MAX custom-network adapter for Android.
* Wraps Velocity Ads Android SDK 0.10.0.
* Supports AppLovin MAX SDK 13.x.
* Requires Android API 24 or later.
* Supported ad formats: Interstitial, Rewarded, and Banner / MREC.
* Reads the Velocity app key from the **App ID** field of the MAX dashboard ad-unit entry.
* Forwards GDPR user consent and CCPA Do Not Sell signals from AppLovin to the Velocity SDK.
