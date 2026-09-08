package com.applovin.mediation.adapters

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.sdk.AppLovinSdk
import io.velocityads.sdk.listeners.VelocityAdsInitListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [VelocityAdsMediationAdapter].
 *
 * The Velocity SDK is never initialised in the test process. The init call and the
 * initialised-state query are replaced through the adapter's test seams
 * ([VelocityAdsMediationAdapter.initSdkRunner] / [VelocityAdsMediationAdapter.isSdkInitialized])
 * so the coalesced init flow can be driven deterministically without network I/O.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityAdsMediationAdapterTest {
    private lateinit var sdk: AppLovinSdk
    private lateinit var adapter: VelocityAdsMediationAdapter

    /** The listeners handed to the fake init runner, in call order. */
    private val initListeners = mutableListOf<VelocityAdsInitListener>()
    private var sdkInitialized = false

    @Before
    fun setUp() {
        // RETURNS_DEEP_STUBS prevents NPEs in MediationAdapterBase.<init>, which calls
        // internal obfuscated methods on the AppLovinSdk instance.
        sdk = mock(AppLovinSdk::class.java, Mockito.RETURNS_DEEP_STUBS)
        adapter = VelocityAdsMediationAdapter(sdk)
        VelocityAdsMediationAdapter.initSdkRunner = { _, _, listener -> initListeners += listener }
        VelocityAdsMediationAdapter.isSdkInitialized = { sdkInitialized }
    }

    @After
    fun tearDown() {
        VelocityAdsMediationAdapter.resetForTesting()
    }

    /** Returns the shared companion [InitCoalescer] via reflection, or null on failure. */
    private fun sharedCoalescer(): InitCoalescer<Boolean>? =
        try {
            val coalescerField = VelocityAdsMediationAdapter::class.java.getDeclaredField("initCoalescer")
            coalescerField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            coalescerField.get(null) as? InitCoalescer<Boolean>
        } catch (_: Exception) {
            null
        }

    private fun advanceMainLooper(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun inProgressError() =
        VelocityAdsError(VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS, "SDK initialization is already in progress.")

    // ========== Helpers ==========

    private fun mockInitParams(appKey: String? = "test-app-key"): MaxAdapterInitializationParameters {
        val params = mock(MaxAdapterInitializationParameters::class.java)
        val serverBundle = Bundle()
        if (appKey != null) serverBundle.putString("app_id", appKey)
        `when`(params.getServerParameters()).thenReturn(serverBundle)
        `when`(params.getCustomParameters()).thenReturn(Bundle())
        `when`(params.hasUserConsent()).thenReturn(null)
        `when`(params.isDoNotSell()).thenReturn(null)
        return params
    }

    /**
     * Returns load parameters. With the default null [appKey] and no remembered key,
     * [VelocityAdsMediationAdapter.ensureInitialized] takes the fast-fail path
     * (no app key ever seen → `onReady(false)`).
     */
    private fun mockLoadParams(
        adUnitId: String? = "test-ad-unit",
        appKey: String? = null,
    ): MaxAdapterResponseParameters {
        val params = mock(MaxAdapterResponseParameters::class.java)
        `when`(params.getThirdPartyAdPlacementId()).thenReturn(adUnitId)
        val serverBundle = Bundle()
        if (appKey != null) serverBundle.putString("app_id", appKey)
        `when`(params.getServerParameters()).thenReturn(serverBundle)
        `when`(params.getCustomParameters()).thenReturn(Bundle())
        `when`(params.hasUserConsent()).thenReturn(null)
        `when`(params.isDoNotSell()).thenReturn(null)
        return params
    }

    // ========== initialize() ==========

    @Test
    fun `initialize with null appKey delivers INITIALIZED_UNKNOWN`() {
        // Given
        val params = mockInitParams(appKey = null)
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        // When
        adapter.initialize(params, null, onCompletion)

        // Then — no app_id at network level is normal; MAX still routes loads through this network
        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_UNKNOWN, null)
    }

    @Test
    fun `initialize with blank appKey delivers INITIALIZED_UNKNOWN`() {
        // Given
        val params = mockInitParams(appKey = "   ")
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        // When
        adapter.initialize(params, null, onCompletion)

        // Then
        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_UNKNOWN, null)
    }

    /**
     * When two adapter instances call [VelocityAdsMediationAdapter.initialize] with no
     * `app_id`, both must report INITIALIZED_UNKNOWN immediately and independently without
     * crashing or interfering with each other. The real SDK init is deferred to the first
     * load, where the waterfall entry's app_id is available via ensureInitialized().
     */
    @Test
    fun `concurrent initialize calls with no appKey both deliver INITIALIZED_UNKNOWN gracefully`() {
        // Given — two adapter instances, neither with an app key
        val adapter2 = VelocityAdsMediationAdapter(sdk)
        val params = mockInitParams(appKey = null)
        val onCompletion1 = mock(MaxAdapter.OnCompletionListener::class.java)
        val onCompletion2 = mock(MaxAdapter.OnCompletionListener::class.java)

        // When
        adapter.initialize(params, null, onCompletion1)
        adapter2.initialize(params, null, onCompletion2)

        // Then — both report unknown cleanly without interfering with each other
        verify(onCompletion1).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_UNKNOWN, null)
        verify(onCompletion2).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_UNKNOWN, null)
    }

    @Test
    fun `destroy before any initialize is a safe no-op`() {
        // When / Then — must not throw
        adapter.onDestroy()
    }

    // ========== initialize() — claimed init through the SDK ==========

    @Test
    fun `initialize with an already initialized SDK reports success without calling initSDK`() {
        sdkInitialized = true
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        adapter.initialize(mockInitParams(), null, onCompletion)

        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
        assertTrue(initListeners.isEmpty())
    }

    @Test
    fun `initialize with an appKey starts exactly one SDK init and reports its success`() {
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        adapter.initialize(mockInitParams(), null, onCompletion)
        assertEquals(1, initListeners.size)
        verifyNoInteractions(onCompletion)

        initListeners.single().onInitSuccess()

        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
    }

    @Test
    fun `initialize reports failure when the SDK init fails`() {
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        adapter.initialize(mockInitParams(), null, onCompletion)
        initListeners.single().onInitFailure(VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))

        verify(onCompletion).onCompletion(eq(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE), anyString())
    }

    @Test
    fun `simultaneous initialize calls coalesce onto one SDK init and share the outcome`() {
        val first = mock(MaxAdapter.OnCompletionListener::class.java)
        val second = mock(MaxAdapter.OnCompletionListener::class.java)

        adapter.initialize(mockInitParams(), null, first)
        VelocityAdsMediationAdapter(sdk).initialize(mockInitParams(), null, second)
        assertEquals(1, initListeners.size)

        initListeners.single().onInitSuccess()

        verify(first).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
        verify(second).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
    }

    @Test
    fun `a failed init releases the claim so the next attempt calls initSDK again`() {
        adapter.initialize(mockInitParams(), null, mock(MaxAdapter.OnCompletionListener::class.java))
        initListeners.single().onInitFailure(VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))

        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)
        adapter.initialize(mockInitParams(), null, onCompletion)

        assertEquals(2, initListeners.size)
        initListeners.last().onInitSuccess()
        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
    }

    @Test
    fun `initialize survives a throwing initSDK and reports failure`() {
        VelocityAdsMediationAdapter.initSdkRunner = { _, _, _ -> throw IllegalStateException("boom") }
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)

        adapter.initialize(mockInitParams(), null, onCompletion)

        verify(onCompletion).onCompletion(eq(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE), anyString())
        assertFalse(checkNotNull(sharedCoalescer()).isClaimed)
    }

    // ========== initialize() — host-owned init in flight ==========

    @Test
    fun `an in-progress rejection polls isInitialized instead of calling initSDK again`() {
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)
        adapter.initialize(mockInitParams(), null, onCompletion)

        initListeners.single().onInitFailure(inProgressError())
        advanceMainLooper(InFlightInitPoller.DEFAULT_POLL_INTERVAL_MS * 3)
        verifyNoInteractions(onCompletion)

        sdkInitialized = true
        advanceMainLooper(InFlightInitPoller.DEFAULT_POLL_INTERVAL_MS)

        verify(onCompletion).onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
        assertEquals(1, initListeners.size)
    }

    @Test
    fun `an in-progress rejection whose host init never completes fails after the poll window`() {
        val onCompletion = mock(MaxAdapter.OnCompletionListener::class.java)
        adapter.initialize(mockInitParams(), null, onCompletion)

        initListeners.single().onInitFailure(inProgressError())
        advanceMainLooper(InFlightInitPoller.DEFAULT_TIMEOUT_MS - InFlightInitPoller.DEFAULT_POLL_INTERVAL_MS)
        verifyNoInteractions(onCompletion)

        advanceMainLooper(InFlightInitPoller.DEFAULT_POLL_INTERVAL_MS)

        verify(onCompletion).onCompletion(eq(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE), anyString())
        assertFalse(checkNotNull(sharedCoalescer()).isClaimed)
    }

    @Test
    fun `loads arriving during the poll window park on the claim and share its outcome`() {
        adapter.initialize(mockInitParams(), null, mock(MaxAdapter.OnCompletionListener::class.java))
        initListeners.single().onInitFailure(inProgressError())

        val ready = mutableListOf<Boolean>()
        adapter.ensureInitialized(mockLoadParams(appKey = "test-app-key")) { ready += it }
        assertTrue(ready.isEmpty())
        assertEquals(1, initListeners.size)

        sdkInitialized = true
        advanceMainLooper(InFlightInitPoller.DEFAULT_POLL_INTERVAL_MS)

        assertEquals(listOf(true), ready)
    }

    // ========== ensureInitialized ==========

    @Test
    fun `ensureInitialized with an initialized SDK reports ready synchronously`() {
        sdkInitialized = true
        val ready = mutableListOf<Boolean>()

        adapter.ensureInitialized(mockLoadParams()) { ready += it }

        assertEquals(listOf(true), ready)
        assertTrue(initListeners.isEmpty())
    }

    @Test
    fun `ensureInitialized with a load-time appKey starts the SDK init and reports its outcome`() {
        val ready = mutableListOf<Boolean>()

        adapter.ensureInitialized(mockLoadParams(appKey = "test-app-key")) { ready += it }
        assertTrue(ready.isEmpty())

        initListeners.single().onInitSuccess()

        assertEquals(listOf(true), ready)
    }

    @Test
    fun `ensureInitialized falls back to the appKey remembered from initialize`() {
        adapter.initialize(mockInitParams(appKey = "remembered-key"), null, mock(MaxAdapter.OnCompletionListener::class.java))
        initListeners.single().onInitFailure(VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))
        val ready = mutableListOf<Boolean>()

        adapter.ensureInitialized(mockLoadParams(appKey = null)) { ready += it }

        assertEquals(2, initListeners.size)
        initListeners.last().onInitSuccess()
        assertEquals(listOf(true), ready)
    }

    @Test
    fun `a mismatched load-time appKey is ignored in favour of the first key seen`() {
        val capturedAppKeys = mutableListOf<String>()
        VelocityAdsMediationAdapter.initSdkRunner = { _, request, listener ->
            capturedAppKeys += request.appKey
            initListeners += listener
        }
        adapter.initialize(mockInitParams(appKey = "first-key"), null, mock(MaxAdapter.OnCompletionListener::class.java))
        initListeners.single().onInitFailure(VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))

        adapter.ensureInitialized(mockLoadParams(appKey = "other-key")) { }

        assertEquals(listOf("first-key", "first-key"), capturedAppKeys)
    }

    // ========== Load — adUnitId guard ==========

    @Test
    fun `loadInterstitialAd with null adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        adapter.loadInterstitialAd(mockLoadParams(adUnitId = null), null, listener)

        // Then
        verify(listener).onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `loadInterstitialAd with blank adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        adapter.loadInterstitialAd(mockLoadParams(adUnitId = "  "), null, listener)

        // Then
        verify(listener).onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `loadRewardedAd with null adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxRewardedAdapterListener::class.java)

        // When
        adapter.loadRewardedAd(mockLoadParams(adUnitId = null), null, listener)

        // Then
        verify(listener).onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `loadRewardedAd with blank adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxRewardedAdapterListener::class.java)

        // When
        adapter.loadRewardedAd(mockLoadParams(adUnitId = ""), null, listener)

        // Then
        verify(listener).onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `loadAdViewAd with null adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxAdViewAdapterListener::class.java)

        // When
        adapter.loadAdViewAd(mockLoadParams(adUnitId = null), MaxAdFormat.BANNER, null, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `loadAdViewAd with blank adUnitId delivers INVALID_CONFIGURATION`() {
        // Given
        val listener = mock(MaxAdViewAdapterListener::class.java)

        // When
        adapter.loadAdViewAd(mockLoadParams(adUnitId = "   "), MaxAdFormat.BANNER, null, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    // ========== Load — not-initialized path ==========
    //
    // When the SDK is not initialised and no app key is available (initialize() was never
    // called, so storedAppKey is null, and the load parameters carry no app_id),
    // ensureInitialized() calls onReady(false) synchronously — before reaching the
    // coalescer or the SDK. This tests the full load delegate wiring.

    @Test
    fun `loadInterstitialAd when SDK not initialized and no appKey delivers NOT_INITIALIZED`() {
        // Given
        val listener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        adapter.loadInterstitialAd(mockLoadParams(adUnitId = "valid-unit"), null, listener)

        // Then
        verify(listener).onInterstitialAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
    }

    @Test
    fun `loadRewardedAd when SDK not initialized and no appKey delivers NOT_INITIALIZED`() {
        // Given
        val listener = mock(MaxRewardedAdapterListener::class.java)

        // When
        adapter.loadRewardedAd(mockLoadParams(adUnitId = "valid-unit"), null, listener)

        // Then
        verify(listener).onRewardedAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
    }

    @Test
    fun `loadAdViewAd when SDK not initialized and no appKey delivers NOT_INITIALIZED`() {
        // Given — a valid activity so the Activity guard is satisfied; missing app_id so
        // ensureInitialized fails without an appKey to initSDK with.
        val listener = mock(MaxAdViewAdapterListener::class.java)
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        // When
        adapter.loadAdViewAd(mockLoadParams(adUnitId = "valid-unit"), MaxAdFormat.BANNER, activity, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
    }

    // ========== Show guards ==========

    @Test
    fun `showInterstitialAd when no ad is loaded delivers AD_NOT_READY`() {
        // Given
        val listener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        adapter.showInterstitialAd(mockLoadParams(), null, listener)

        // Then
        verify(listener).onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
    }

    @Test
    fun `showRewardedAd when no ad is loaded delivers AD_NOT_READY`() {
        // Given
        val listener = mock(MaxRewardedAdapterListener::class.java)

        // When
        adapter.showRewardedAd(mockLoadParams(), null, listener)

        // Then
        verify(listener).onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
    }

    // ========== destroy() — prevents subsequent shows ==========

    @Test
    fun `destroy clears ad state so subsequent showInterstitialAd delivers AD_NOT_READY`() {
        // Given
        adapter.onDestroy()
        val listener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        adapter.showInterstitialAd(mockLoadParams(), null, listener)

        // Then
        verify(listener).onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
    }

    @Test
    fun `destroy clears ad state so subsequent showRewardedAd delivers AD_NOT_READY`() {
        // Given
        adapter.onDestroy()
        val listener = mock(MaxRewardedAdapterListener::class.java)

        // When
        adapter.showRewardedAd(mockLoadParams(), null, listener)

        // Then
        verify(listener).onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
    }

    @Test
    fun `destroy before any load is a safe no-op`() {
        // When / Then — must not throw
        adapter.onDestroy()
    }

    // ========== extractAppKey ==========

    private fun paramsWithBundles(serverParams: Bundle?): MaxAdapterResponseParameters {
        val params = mock(MaxAdapterResponseParameters::class.java)
        `when`(params.getCustomParameters()).thenReturn(Bundle())
        `when`(params.getServerParameters()).thenReturn(serverParams)
        return params
    }

    @Test
    fun `extractAppKey reads app_id from server parameters`() {
        // Given
        val server = Bundle().apply { putString("app_id", "from-app-id") }

        // When / Then
        assertEquals("from-app-id", adapter.extractAppKey(paramsWithBundles(server)))
    }

    @Test
    fun `extractAppKey returns null when app_id is blank`() {
        // Given
        val server = Bundle().apply { putString("app_id", "   ") }

        // When / Then
        assertNull(adapter.extractAppKey(paramsWithBundles(server)))
    }

    @Test
    fun `extractAppKey returns null when no source has a value`() {
        assertNull(adapter.extractAppKey(paramsWithBundles(Bundle())))
    }

    @Test
    fun `extractAppKey tolerates null parameter bundles`() {
        assertNull(adapter.extractAppKey(paramsWithBundles(null)))
    }

    // ========== destroy-guarded parked load continuations ==========
    //
    // A load whose continuation is parked in the shared InitCoalescer (init in flight)
    // must become a no-op if the adapter is destroyed before init completes: no ad object
    // creation and no listener callback. Pre-claiming the coalescer keeps the parked load
    // from winning the claim, so only the parked continuation is exercised.

    @Test
    fun `parked load continuation after destroy is a no-op`() {
        // Given — an in-flight init owns the coalescer, and a load parks behind it
        val coalescer = sharedCoalescer()
        checkNotNull(coalescer) { "Could not access shared InitCoalescer via reflection" }
        coalescer.claim { }
        val listener = mock(MaxInterstitialAdapterListener::class.java)
        adapter.loadInterstitialAd(mockLoadParams(appKey = "test-app-key"), null, listener)

        // When — the adapter is destroyed, then the init resolves successfully
        adapter.onDestroy()
        coalescer.complete(true)

        // Then — the parked continuation bails out: no callbacks, no orphaned ad
        verifyNoInteractions(listener)
    }

    @Test
    fun `parked load continuation before destroy still delivers NOT_INITIALIZED on failure`() {
        // Given
        val coalescer = sharedCoalescer()
        checkNotNull(coalescer) { "Could not access shared InitCoalescer via reflection" }
        coalescer.claim { }
        val listener = mock(MaxInterstitialAdapterListener::class.java)
        adapter.loadInterstitialAd(mockLoadParams(appKey = "test-app-key"), null, listener)

        // When — the init fails while the adapter is still alive
        coalescer.complete(false)

        // Then
        verify(listener).onInterstitialAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
    }
}
