package com.applovin.mediation.adapters

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdViewAdapter
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.mediation.adapters.velocity.BuildConfig
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import io.velocityads.sdk.VelocityAds
import io.velocityads.sdk.VelocityAdsMediationBridge
import io.velocityads.sdk.listeners.VelocityAdsInitListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityAdsInitRequest

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
    MaxAdViewAdapter,
    FormatAdapterContext {
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
         * load-time re-init attempts whose response parameters lack `app_id`.
         */
        @Volatile private var storedAppKey: String? = null

        /**
         * Mediation name reported to the Velocity SDK via [VelocityAdsMediationBridge].
         * Owned by this adapter — the SDK accepts any lowercase canonical string.
         */
        private const val MEDIATION_NAME = "max"

        /**
         * One-shot guard for [forwardMediationInfo] — the values (mediation name,
         * adapter version, AppLovin SDK version) never change mid-session.
         */
        private val mediationInfoForwarded = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Reports the mediation environment (MAX) to the Velocity SDK so it is attached
         * to every ad request and analytics event. Safe to call from any adapter entry
         * point; only the first call has an effect.
         */
        internal fun forwardMediationInfo() {
            if (!mediationInfoForwarded.compareAndSet(false, true)) return
            VelocityAdsMediationBridge.setMediationInfo(
                MEDIATION_NAME,
                BuildConfig.ADAPTER_VERSION,
                AppLovinSdk.VERSION,
            )
        }
    }

    /**
     * Set by [onDestroy]. Format adapters check this before creating ad objects so orphaned
     * operations from a completed [ensureInitialized] cannot spawn ads that will never be
     * destroyed.
     */
    @Volatile override var isDestroyed = false
        private set

    // ---- format adapters ----

    private val interstitialFormatAdapter: VelocityInterstitialFormatAdapter
    private val rewardedFormatAdapter: VelocityRewardedFormatAdapter
    private val bannerFormatAdapter: VelocityBannerFormatAdapter

    init {
        interstitialFormatAdapter = VelocityInterstitialFormatAdapter(this)
        rewardedFormatAdapter =
            VelocityRewardedFormatAdapter(
                ctx = this,
                rewardSupplier = ::getReward,
                configureReward = ::configureReward,
            )
        bannerFormatAdapter = VelocityBannerFormatAdapter(this)
    }

    // =========================================================================
    // MediationAdapterBase
    // =========================================================================

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        onCompletionListener: MaxAdapter.OnCompletionListener,
    ) {
        // Identify the mediation environment before SDK init so the very first
        // request and event carry it.
        forwardMediationInfo()
        // Forward privacy signals before the fast-path return so consent is always
        // up-to-date even when the SDK was pre-initialised by the host app.
        forwardPrivacySettings()

        if (VelocityAds.isInitialized()) {
            onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
            return
        }

        val appKey = extractAppKey(parameters)
        if (appKey.isNullOrBlank()) {
            // The App ID field is optional in the MAX dashboard's Custom Network settings,
            // so app_id may not be present at network-level initialization time. Report
            // INITIALIZED_UNKNOWN — the adapter is ready but the app key arrives via the
            // per-placement App ID field at load time; ensureInitialized() performs the
            // real SDK init lazily on the first load.
            onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_UNKNOWN, null)
            return
        }

        storedAppKey = appKey

        // Route through the shared initCoalescer so concurrent initialize() calls (one per
        // adapter instance that MAX may create) and concurrent ensureInitialized() calls from
        // the load path all share a single in-flight initSDK attempt and its outcome.
        runOnMainNow {
            if (VelocityAds.isInitialized()) {
                onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
                return@runOnMainNow
            }
            val won =
                initCoalescer.claim { initialized ->
                    if (initialized) {
                        onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
                    } else {
                        onCompletionListener.onCompletion(
                            MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
                            "Velocity Ads: initialization failed or timed out",
                        )
                    }
                }
            if (won) {
                startClaimedInit(appKey)
            }
        }
    }

    /**
     * Extracts the Velocity app key from MAX parameters.
     *
     * MAX delivers the value set in the **App ID** field of the Custom Network dashboard
     * entry via [MaxAdapterParameters.getServerParameters] under the key `"app_id"`.
     */
    internal fun extractAppKey(parameters: MaxAdapterParameters): String? =
        parameters.serverParameters?.getString("app_id")?.takeUnless { it.isBlank() }

    /**
     * Polls on the main thread until the host-owned in-flight initialization resolves, then
     * either succeeds fast or re-attempts [VelocityAds.initSDK] itself — the Velocity SDK
     * explicitly permits re-init from its FAILED state, so a failed host init is retried
     * immediately instead of waiting out the full poll window. [onResult] is invoked exactly
     * once: every path either terminates with a result or reschedules itself, and the
     * deadline is only checked between attempts (never while an owned re-init is in flight).
     */
    private fun awaitInFlightInitialization(
        appKey: String,
        onResult: (Boolean) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val deadlineUptimeMs = SystemClock.uptimeMillis() + INIT_POLL_TIMEOUT_MS
        val attempt =
            object : Runnable {
                override fun run() {
                    if (VelocityAds.isInitialized()) {
                        onResult(true)
                        return
                    }
                    if (SystemClock.uptimeMillis() >= deadlineUptimeMs) {
                        onResult(false)
                        return
                    }
                    val reattempt = this
                    val retryListener =
                        object : VelocityAdsInitListener {
                            override fun onInitSuccess() {
                                onResult(true)
                            }

                            override fun onInitFailure(error: VelocityAdsError) {
                                if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                                    // Host init still in flight — check again shortly.
                                    handler.postDelayed(reattempt, INIT_POLL_INTERVAL_MS)
                                } else {
                                    Log.w(TAG, "Velocity Ads re-init after host init failed [${error.code}]: ${error.message}")
                                    onResult(false)
                                }
                            }
                        }
                    val initRequest = VelocityAdsInitRequest.Builder(appKey).build()
                    try {
                        VelocityAds.initSDK(getApplicationContext(), initRequest, retryListener)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Velocity Ads initSDK threw unexpectedly during re-init", t)
                        onResult(false)
                    }
                }
            }
        handler.post(attempt)
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
    override fun ensureInitialized(
        parameters: MaxAdapterResponseParameters,
        onReady: (Boolean) -> Unit,
    ) {
        if (VelocityAds.isInitialized()) {
            onReady(true)
            return
        }

        val appKey = extractAppKey(parameters) ?: storedAppKey
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
        val initListener =
            object : VelocityAdsInitListener {
                override fun onInitSuccess() {
                    initCoalescer.complete(true)
                }

                override fun onInitFailure(error: VelocityAdsError) {
                    if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                        // Another caller (e.g. the host app) owns the in-flight init —
                        // wait for its outcome instead of failing the parked loads.
                        awaitInFlightInitialization(appKey) { initialized ->
                            initCoalescer.complete(initialized)
                        }
                        return
                    }
                    Log.w(TAG, "Velocity Ads re-init before load failed [${error.code}]: ${error.message}")
                    initCoalescer.complete(false)
                }
            }
        try {
            VelocityAds.initSDK(getApplicationContext(), initRequest, initListener)
        } catch (t: Throwable) {
            // The Velocity SDK's public API contract is no-throw, but a synchronous throw
            // here would otherwise strand the claimed coalescer forever (parking every
            // future load) and propagate a crash into MAX. Fail the attempt cleanly instead.
            Log.e(TAG, "Velocity Ads initSDK threw unexpectedly", t)
            initCoalescer.complete(false)
        }
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
        isDestroyed = true
        interstitialFormatAdapter.destroy()
        rewardedFormatAdapter.destroy()
        bannerFormatAdapter.destroy()
    }

    // =========================================================================
    // MaxInterstitialAdapter
    // =========================================================================

    override fun loadInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) = interstitialFormatAdapter.load(parameters, activity, listener)

    override fun showInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) = interstitialFormatAdapter.show(parameters, activity, listener)

    // =========================================================================
    // MaxRewardedAdapter
    // =========================================================================

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) = rewardedFormatAdapter.load(parameters, activity, listener)

    override fun showRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) = rewardedFormatAdapter.show(parameters, activity, listener)

    // =========================================================================
    // MaxAdViewAdapter
    // =========================================================================

    override fun loadAdViewAd(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) = bannerFormatAdapter.load(parameters, adFormat, activity, listener)

    // =========================================================================
    // Privacy helpers
    // =========================================================================

    /**
     * Forwards the current AppLovin privacy state to the Velocity SDK.
     *
     * Reads directly from [AppLovinPrivacySettings] — the authoritative Android source —
     * rather than from MAX adapter parameters. [MaxAdapterParameters.hasUserConsent] and
     * [MaxAdapterParameters.isDoNotSell] are only reliably non-null when the publisher has
     * already called `AppLovinPrivacySettings.setHasUserConsent/setDoNotSell` *and* the MAX
     * SDK has had time to propagate those values into the parameter object, which is not
     * guaranteed on every adapter entry point. Reading from [AppLovinPrivacySettings]
     * directly is always accurate and requires no parameter threading.
     *
     * Called at [initialize] (before SDK boots) and on every ad load, so mid-session
     * CMP changes propagate on the next request.
     */
    override fun forwardPrivacySettings() {
        try {
            val ctx = applicationContext ?: return
            if (AppLovinPrivacySettings.isUserConsentSet(ctx)) {
                VelocityAds.setConsent(AppLovinPrivacySettings.hasUserConsent(ctx))
            }
            if (AppLovinPrivacySettings.isDoNotSellSet(ctx)) {
                VelocityAds.setDoNotSell(AppLovinPrivacySettings.isDoNotSell(ctx))
            }
        } catch (_: Exception) {
            // Defensive: privacy forwarding must never crash or block ad loading.
        }
    }
}
