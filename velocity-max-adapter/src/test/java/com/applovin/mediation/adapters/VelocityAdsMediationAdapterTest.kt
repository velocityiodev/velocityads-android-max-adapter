package com.applovin.mediation.adapters

import android.app.Activity
import android.os.Bundle
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.sdk.AppLovinSdk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [VelocityAdsMediationAdapter].
 *
 * Note on VelocityAds mocking: [VelocityAds] is a Kotlin `object` using interface
 * delegation, so its methods are not static invocations — Mockito's `mockStatic`
 * cannot intercept them. Tests that depend on
 * [io.velocityads.sdk.VelocityAds.isInitialized] returning `true` are therefore covered
 * at the integration-test level; the unit tests here focus on the adapter's guard logic,
 * wiring, and error-delivery behaviour — all of which are testable without SDK
 * initialisation, because the SDK is always non-initialised in the test process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityAdsMediationAdapterTest {
    private lateinit var sdk: AppLovinSdk
    private lateinit var adapter: VelocityAdsMediationAdapter

    @Before
    fun setUp() {
        // RETURNS_DEEP_STUBS prevents NPEs in MediationAdapterBase.<init>, which calls
        // internal obfuscated methods on the AppLovinSdk instance.
        sdk = mock(AppLovinSdk::class.java, Mockito.RETURNS_DEEP_STUBS)
        adapter = VelocityAdsMediationAdapter(sdk)
    }

    @After
    fun tearDown() {
        // VelocityAdsMediationAdapter companion-object fields (storedAppKey, initCoalescer) are
        // shared across all adapter instances and across tests in the same JVM. Reset them so
        // each test starts from a clean state.
        resetCompanionState()
    }

    /**
     * Resets the static fields of [VelocityAdsMediationAdapter] that are shared across
     * instances and persist between tests: [storedAppKey] and the [InitCoalescer].
     * Uses reflection because both are `private` — exposing a `resetForTesting()` hook
     * on the production class would widen its API surface.
     */
    private fun resetCompanionState() {
        try {
            val adapterClass = VelocityAdsMediationAdapter::class.java

            val storedAppKeyField = adapterClass.getDeclaredField("storedAppKey")
            storedAppKeyField.isAccessible = true
            storedAppKeyField.set(null, null)

            sharedCoalescer()?.complete(false)
        } catch (_: Exception) {
            // Best-effort; failing to reset is not fatal but may cause inter-test interference.
        }
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
     * Returns load parameters. With the default null [appKey] and a never-initialised SDK,
     * [VelocityAdsMediationAdapter.ensureInitialized] takes the fast-fail path
     * (no app key ever seen → `onReady(false)`) without touching the real Velocity SDK.
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
     *
     * The coalescing behaviour for in-flight init attempts is exercised exhaustively by
     * [InitCoalescerTest]. End-to-end verification of two concurrent SUCCESS deliveries
     * requires mocking [VelocityAds] (integration test territory — see class KDoc).
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
    // When VelocityAds is not initialised (the real state in a unit-test JVM) and no
    // app key is available (initialize() was never called, so storedAppKey is null, and
    // the load parameters carry no app_id), ensureInitialized() calls onReady(false)
    // synchronously — before reaching the coalescer or the real SDK. This tests the full
    // load delegate wiring without any SDK mocking.

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
    // from winning the claim, so the real Velocity SDK is never touched.

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
