# Changelog

## [Unreleased]

### Changed

* Unreachable ad media (Velocity error `2013`) is now reported to MAX as `NO_FILL` so the waterfall moves on.

## [0.10.0.0] - 2026-09-07

### Added

* Initial release of the Velocity Ads AppLovin MAX custom-network adapter for Android.
* Wraps Velocity Ads Android SDK 0.10.0.
* Supports AppLovin MAX SDK 13.x.
* Requires Android API 24 or later.
* Supported ad formats: Interstitial, Rewarded, and Banner / MREC.
* Reads the Velocity app key from the **App ID** field of the MAX dashboard ad-unit entry.
* Forwards GDPR user consent and CCPA Do Not Sell signals from AppLovin to the Velocity SDK.
