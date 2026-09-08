package com.applovin.mediation.adapters

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val MEDIATION_NAME = "max"

        /**
         * Shared across adapter instances (MAX may create one per ad unit) because
         * Velocity init is process-global. Main-thread-confined — see [InitCoalescer].
         */
        private val initCoalescer = InitCoalescer<Boolean>()

        /**
         * The app key captured from the first successful App ID sighting, used as a
         * fallback for load-time re-init attempts whose response parameters lack `app_id`.
         * First-wins: Velocity init is process-global, so later mismatched App IDs are
         * logged and ignored for storage.
         */
        @Volatile private var storedAppKey: String? = null

        private val appKeyMismatchLogged = AtomicBoolean(false)

        private val mediationInfoForwarded = AtomicBoolean(false)

        private val defaultInitSdkRunner: (Context?, VelocityAdsInitRequest, VelocityAdsInitListener) -> Unit =
            { context, request, listener ->
                VelocityAds.initSDK(requireNotNull(context) { "application context unavailable" }, request, listener)
            }

        /**
         * Test seam: performs the Velocity SDK initialization call. Production wiring is
         * [VelocityAds.initSDK]; tests substitute a fake so the coalesced init flow can be
         * driven deterministically without network I/O. A missing application context is
         * surfaced as a throw so [startClaimedInit] fails the attempt cleanly.
         */
        internal var initSdkRunner: (Context?, VelocityAdsInitRequest, VelocityAdsInitListener) -> Unit = defaultInitSdkRunner

        /** Test seam: reports whether the Velocity SDK is initialized. Production wiring is [VelocityAds.isInitialized]. */
        internal var isSdkInitialized: () -> Boolean = VelocityAds::isInitialized

        /**
         * Test-only: drains and unclaims the shared coalescer and clears all remembered state
         * and seams so nothing leaks between test cases.
         */
        internal fun resetForTesting() {
            if (initCoalescer.isClaimed) initCoalescer.complete(false)
            storedAppKey = null
            appKeyMismatchLogged.set(false)
            initSdkRunner = defaultInitSdkRunner
            isSdkInitialized = VelocityAds::isInitialized
        }

        private fun rememberAppKey(appKey: String) {
            val previous = storedAppKey
            if (previous == null) {
                storedAppKey = appKey
                return
            }
            if (previous != appKey && appKeyMismatchLogged.compareAndSet(false, true)) {
                Log.w(
                    TAG,
                    "Velocity Ads: multiple App ID values detected. " +
                        "Use one Velocity app key per application process.",
                )
            }
        }

        /** Reports the mediation environment to the Velocity SDK. Idempotent; safe from any entry point. */
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
        forwardMediationInfo()
        // Forward privacy signals before the fast-path return so consent is always
        // up-to-date even when the SDK was pre-initialised by the host app.
        forwardPrivacySettings()

        if (isSdkInitialized()) {
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

        rememberAppKey(appKey)

        // Route through the shared initCoalescer so concurrent initialize() calls (one per
        // adapter instance that MAX may create) and concurrent ensureInitialized() calls from
        // the load path all share a single in-flight initSDK attempt and its outcome.
        runOnMainNow {
            if (isSdkInitialized()) {
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
        if (isSdkInitialized()) {
            onReady(true)
            return
        }

        val loadAppKey = extractAppKey(parameters)
        if (!loadAppKey.isNullOrBlank()) {
            rememberAppKey(loadAppKey)
        }
        val appKey = loadAppKey ?: storedAppKey
        if (appKey.isNullOrBlank()) {
            // No app key ever seen — nothing to re-init with; fail as before.
            onReady(false)
            return
        }

        runOnMainNow {
            if (isSdkInitialized()) {
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
     * Performs the actual Velocity SDK init call on behalf of the caller that won the
     * coalescer claim, broadcasting the outcome to every parked handler when the SDK responds.
     *
     * If the SDK reports `SDK_INITIALIZATION_IN_PROGRESS` — the host app called `initSDK`
     * moments before the adapter did — the claim stays held and [InFlightInitPoller] waits for
     * that init to settle, so concurrent callers keep parking on the coalescer instead of
     * failing. A host init that fails inside the poll window surfaces as a timeout; the next
     * load re-attempts init, which the Velocity SDK permits from its FAILED state.
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
                        InFlightInitPoller.awaitInitialization(isInitialized = isSdkInitialized) { initialized ->
                            if (!initialized) {
                                Log.w(TAG, "Velocity Ads: timed out waiting for in-flight SDK initialization")
                            }
                            initCoalescer.complete(initialized)
                        }
                        return
                    }
                    Log.w(TAG, "Velocity Ads initialization failed [${error.code}]: ${error.message}")
                    initCoalescer.complete(false)
                }
            }
        try {
            initSdkRunner(getApplicationContext(), initRequest, initListener)
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
