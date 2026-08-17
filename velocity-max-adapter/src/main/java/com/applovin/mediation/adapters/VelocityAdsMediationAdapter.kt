package com.applovin.mediation.adapters

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxNativeAdAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.mediation.adapters.velocity.BuildConfig
import com.applovin.sdk.AppLovinSdk
import io.velocityads.sdk.VelocityAds
import io.velocityads.sdk.listeners.VelocityAdsInitListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityAdsInitRequest
import io.velocityads.sdk.models.VelocityInterstitialAd
import io.velocityads.sdk.models.VelocityInterstitialAdRequest
import io.velocityads.sdk.models.VelocityNativeAd
import io.velocityads.sdk.models.VelocityNativeAdRequest
import io.velocityads.sdk.models.VelocityRewardedAd
import io.velocityads.sdk.models.VelocityRewardedAdRequest

/**
 * AppLovin MAX custom-network adapter for the Velocity Ads SDK.
 *
 * Custom network class name: `com.applovin.mediation.adapters.VelocityAdsMediationAdapter`
 */
class VelocityAdsMediationAdapter(
    sdk: AppLovinSdk,
) : MediationAdapterBase(sdk),
    MaxInterstitialAdapter,
    MaxRewardedAdapter,
    MaxNativeAdAdapter {
    companion object {
        private const val TAG = "VelocityAdsAdapter"
        private const val INIT_POLL_INTERVAL_MS = 200L
        private const val INIT_POLL_TIMEOUT_MS = 5_000L

        /**
         * Shared across adapter instances (MAX may create one per ad unit) because
         * Velocity init is process-global. Main-thread-confined — see [InitCoalescer].
         */
        private val initCoalescer = InitCoalescer<Boolean>()

        /**
         * The app key captured from the first [initialize] call, used as a fallback for
         * load-time re-init attempts whose response parameters lack `app_key`.
         */
        @Volatile private var storedAppKey: String? = null
    }

    // ---- ad object holders ----
    @Volatile private var interstitialAd: VelocityInterstitialAd? = null

    @Volatile private var rewardedAd: VelocityRewardedAd? = null

    @Volatile private var nativeAd: VelocityNativeAd? = null

    // ---- handler holders (needed to wire the show-time listener) ----
    @Volatile private var interstitialAdHandler: VelocityInterstitialAdHandler? = null

    @Volatile private var rewardedAdHandler: VelocityRewardedAdHandler? = null

    @Volatile private var nativeAdHandler: VelocityNativeAdHandler? = null

    // =========================================================================
    // MediationAdapterBase
    // =========================================================================

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        onCompletionListener: MaxAdapter.OnCompletionListener,
    ) {
        // Forward privacy signals before the fast-path return so consent is always
        // up-to-date even when the SDK was pre-initialised by the host app.
        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        if (VelocityAds.isInitialized()) {
            onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
            return
        }

        val appKey = parameters.getServerParameters().getString("app_key")
        if (appKey.isNullOrBlank()) {
            onCompletionListener.onCompletion(
                MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
                "Velocity Ads: missing app_key in server parameters",
            )
            return
        }

        storedAppKey = appKey

        val context = activity ?: getApplicationContext()

        val initRequest = VelocityAdsInitRequest.Builder(appKey).build()

        VelocityAds.initSDK(
            context,
            initRequest,
            object : VelocityAdsInitListener {
                override fun onInitSuccess() {
                    onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
                }

                override fun onInitFailure(error: VelocityAdsError) {
                    if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                        // Another caller (e.g. the host app) already kicked off Velocity init.
                        // Not a permanent failure — wait for the in-flight init to finish and
                        // report the real outcome.
                        awaitInFlightInitialization { initialized ->
                            if (initialized) {
                                onCompletionListener.onCompletion(
                                    MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS,
                                    null,
                                )
                            } else {
                                onCompletionListener.onCompletion(
                                    MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
                                    "Velocity Ads: timed out waiting for in-flight SDK initialization",
                                )
                            }
                        }
                        return
                    }
                    onCompletionListener.onCompletion(
                        MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
                        "Velocity Ads init failed [${error.code}]: ${error.message}",
                    )
                }
            },
        )
    }

    /**
     * Polls [VelocityAds.isInitialized] on the main thread until the in-flight initialization
     * completes or [INIT_POLL_TIMEOUT_MS] elapses. [onResult] is invoked exactly once: every
     * poll iteration either terminates with a result or reschedules itself.
     */
    private fun awaitInFlightInitialization(onResult: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val deadlineUptimeMs = SystemClock.uptimeMillis() + INIT_POLL_TIMEOUT_MS
        val poll =
            object : Runnable {
                override fun run() {
                    when {
                        VelocityAds.isInitialized() -> {
                            onResult(true)
                        }

                        SystemClock.uptimeMillis() >= deadlineUptimeMs -> {
                            onResult(false)
                        }

                        else -> {
                            handler.postDelayed(this, INIT_POLL_INTERVAL_MS)
                        }
                    }
                }
            }
        handler.post(poll)
    }

    /**
     * Ensures the Velocity SDK is initialized before a load proceeds.
     *
     * If the SDK is already up, [onReady] fires with `true` synchronously. Otherwise a
     * re-init is attempted (or coalesced onto an in-flight attempt) using the same
     * parameters as [initialize]. This covers the case where the original MAX-driven
     * init failed transiently (e.g. no connectivity at app launch) but a load arrives
     * later when the SDK could now initialize successfully — the Velocity SDK
     * explicitly permits re-init from its FAILED state.
     */
    private fun ensureInitialized(
        parameters: MaxAdapterResponseParameters,
        onReady: (Boolean) -> Unit,
    ) {
        if (VelocityAds.isInitialized()) {
            onReady(true)
            return
        }

        val appKey =
            parameters.getServerParameters().getString("app_key")?.takeUnless { it.isBlank() }
                ?: storedAppKey
        if (appKey.isNullOrBlank()) {
            // No app key ever seen — nothing to re-init with; fail as before.
            onReady(false)
            return
        }

        runOnMainNow {
            if (VelocityAds.isInitialized()) {
                onReady(true)
                return@runOnMainNow
            }
            val won = initCoalescer.claim(onReady)
            if (won) {
                startClaimedInit(appKey)
            }
        }
    }

    /**
     * Performs the actual [VelocityAds.initSDK] call on behalf of the caller that won
     * the coalescer claim, broadcasting the outcome to every parked handler when the
     * SDK responds.
     */
    private fun startClaimedInit(appKey: String) {
        val initRequest = VelocityAdsInitRequest.Builder(appKey).build()
        VelocityAds.initSDK(
            getApplicationContext(),
            initRequest,
            object : VelocityAdsInitListener {
                override fun onInitSuccess() {
                    initCoalescer.complete(true)
                }

                override fun onInitFailure(error: VelocityAdsError) {
                    if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                        // Another caller (e.g. the host app) owns the in-flight init —
                        // wait for its outcome instead of failing the parked loads.
                        awaitInFlightInitialization { initialized ->
                            initCoalescer.complete(initialized)
                        }
                        return
                    }
                    Log.w(TAG, "Velocity Ads re-init before load failed [${error.code}]: ${error.message}")
                    initCoalescer.complete(false)
                }
            },
        )
    }

    /**
     * Executes [block] on the main thread — inline when already there, otherwise
     * posted. AppLovin MAX documents that all adapter entry points are invoked on the
     * main thread, so the inline path is the norm; the post fallback keeps the
     * main-thread-confined [initCoalescer] safe if a caller strays off-main.
     */
    private fun runOnMainNow(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    override fun getSdkVersion(): String = VelocityAds.getSdkVersion()

    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION

    override fun onDestroy() {
        interstitialAd?.destroy()
        interstitialAd = null
        interstitialAdHandler = null

        rewardedAd?.destroy()
        rewardedAd = null
        rewardedAdHandler = null

        nativeAd?.destroy()
        nativeAd = null
        nativeAdHandler = null
    }

    // =========================================================================
    // MaxInterstitialAdapter
    // =========================================================================

    override fun loadInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        ensureInitialized(parameters) { initialized ->
            if (!initialized) {
                listener.onInterstitialAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            interstitialAd?.destroy()
            interstitialAd = null

            val adRequest = VelocityInterstitialAdRequest.Builder(adUnitId).build()
            val ad = VelocityInterstitialAd(adRequest)
            interstitialAd = ad
            val handler =
                VelocityInterstitialAdHandler(
                    listener,
                    onDismissed = { if (interstitialAd === ad) interstitialAd = null },
                )
            interstitialAdHandler = handler
            ad.load(handler)
        }
    }

    override fun showInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) {
        val ad = interstitialAd
        if (ad == null || !ad.isReady) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }
        if (activity == null) {
            Log.e(TAG, "Cannot show interstitial: Activity is null")
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        val handler = interstitialAdHandler
        if (handler == null) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.INVALID_LOAD_STATE)
            return
        }
        handler.attachShowListener(listener)
        ad.show(activity)
    }

    // =========================================================================
    // MaxRewardedAdapter
    // =========================================================================

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        ensureInitialized(parameters) { initialized ->
            if (!initialized) {
                listener.onRewardedAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            rewardedAd?.destroy()
            rewardedAd = null

            val adRequest = VelocityRewardedAdRequest.Builder(adUnitId).build()
            val ad = VelocityRewardedAd(adRequest)
            rewardedAd = ad
            val handler =
                VelocityRewardedAdHandler(
                    listener,
                    onDismissed = { if (rewardedAd === ad) rewardedAd = null },
                )
            rewardedAdHandler = handler
            ad.load(handler)
        }
    }

    override fun showRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        val ad = rewardedAd
        if (ad == null || !ad.isReady) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }
        if (activity == null) {
            Log.e(TAG, "Cannot show rewarded ad: Activity is null")
            listener.onRewardedAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        val handler = rewardedAdHandler
        if (handler == null) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.INVALID_LOAD_STATE)
            return
        }
        handler.attachShowListener(listener)
        ad.show(activity)
    }

    // =========================================================================
    // MaxNativeAdAdapter
    // =========================================================================

    override fun loadNativeAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxNativeAdAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onNativeAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        ensureInitialized(parameters) { initialized ->
            if (!initialized) {
                listener.onNativeAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            nativeAd?.destroy()
            nativeAd = null

            val adRequest = VelocityNativeAdRequest.Builder(adUnitId).build()
            val handler = VelocityNativeAdHandler(listener)
            nativeAdHandler = handler
            val ad = VelocityNativeAd(adRequest)
            nativeAd = ad
            ad.load(handler)
        }
    }
}
