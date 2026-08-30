# Changelog

## 0.10.0.0
_2026-08-30_

* Initial release of the Velocity Ads AppLovin MAX custom-network adapter for Android.
* Wraps Velocity Ads Android SDK 0.10.0.
* Supports AppLovin MAX SDK 13.x.
* Requires Android API 24 or later.
* Supported ad formats:
  * **Interstitial** — full-screen interstitial ads (video, HTML/MRAID, static image).
  * **Rewarded** — full-screen rewarded ads with publisher-configurable reward currency and amount via `configureReward()`.
  * **Native** — native ads supplying headline, body, call-to-action, icon, and main image assets for custom publisher rendering; media image is downloaded using MAX's caching executor.
  * **Banner / MREC** — inline banner ads; standard fixed sizes and adaptive banner width are supported.
* The Velocity app key is read from the **App ID** field of the MAX dashboard ad-unit entry and delivered via `serverParameters.getString("app_id")`.
* Lazy SDK initialization: if the app key is absent at MAX network-level `initialize`, the adapter initializes the Velocity SDK on the first load that carries a valid app key. Concurrent init calls are coalesced so only one `initSDK` attempt is in flight at a time.
* GDPR user consent and CCPA Do Not Sell signals are forwarded from MAX to the Velocity SDK at both init time and on every ad load.
* Mediation environment reporting: the adapter identifies itself to the Velocity SDK (mediation name `max`, adapter version, AppLovin SDK version) so every Velocity ad request and analytics event carries the mediation context.
