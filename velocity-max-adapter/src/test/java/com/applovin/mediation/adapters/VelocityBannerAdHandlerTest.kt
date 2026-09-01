package com.applovin.mediation.adapters

import android.app.Activity
import android.os.Bundle
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import io.velocityads.sdk.ads.banner.VelocityBannerAdView
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityBannerAd
import io.velocityads.sdk.models.VelocityBannerAdSize
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityBannerAdHandlerTest {
    private lateinit var listener: MaxAdViewAdapterListener
    private lateinit var activity: Activity
    private lateinit var handler: VelocityBannerAdHandler

    @Before
    fun setUp() {
        listener = mock(MaxAdViewAdapterListener::class.java)
        activity = Robolectric.buildActivity(Activity::class.java).get()
        handler = VelocityBannerAdHandler()
    }

    private fun parameters(
        adUnitId: String? = "banner-ad-unit",
        adaptive: Boolean = false,
        adaptiveWidthDp: Int? = null,
    ): MaxAdapterResponseParameters {
        val params = mock(MaxAdapterResponseParameters::class.java)
        `when`(params.thirdPartyAdPlacementId).thenReturn(adUnitId)
        val serverParameters = Bundle().apply { putBoolean("adaptive_banner", adaptive) }
        `when`(params.serverParameters).thenReturn(serverParameters)
        val localExtras: Map<String, Any> =
            if (adaptiveWidthDp != null) mapOf("adaptive_banner_width" to adaptiveWidthDp) else emptyMap()
        `when`(params.localExtraParameters).thenReturn(localExtras)
        return params
    }

    // ========== load() guards ==========

    @Test
    fun `load with null activity fails with MISSING_ACTIVITY`() {
        // When
        handler.load(parameters(), MaxAdFormat.BANNER, null, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.MISSING_ACTIVITY)
        verify(listener, never()).onAdViewAdLoaded(org.mockito.Mockito.any())
    }

    @Test
    fun `load with blank ad unit fails with INVALID_CONFIGURATION`() {
        // When
        handler.load(parameters(adUnitId = "   "), MaxAdFormat.BANNER, activity, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
        verify(listener, never()).onAdViewAdLoaded(org.mockito.Mockito.any())
    }

    @Test
    fun `load with null ad unit fails with INVALID_CONFIGURATION`() {
        // When
        handler.load(parameters(adUnitId = null), MaxAdFormat.BANNER, activity, listener)

        // Then
        verify(listener).onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
    }

    // ========== resolveAdSize ==========

    @Test
    fun `resolveAdSize maps MREC to MREC`() {
        val size = handler.resolveAdSize(parameters(), MaxAdFormat.MREC, activity)
        assertEquals(VelocityBannerAdSize.MREC, size)
    }

    @Test
    fun `resolveAdSize maps LEADER to LEADERBOARD`() {
        val size = handler.resolveAdSize(parameters(), MaxAdFormat.LEADER, activity)
        assertEquals(VelocityBannerAdSize.LEADERBOARD, size)
    }

    @Test
    fun `resolveAdSize defaults to BANNER when not adaptive`() {
        val size = handler.resolveAdSize(parameters(adaptive = false), MaxAdFormat.BANNER, activity)
        assertEquals(VelocityBannerAdSize.BANNER, size)
    }

    @Test
    fun `resolveAdSize ignores adaptive width local-extra when adaptive_banner param is false`() {
        // Given adaptive not enabled but a width local-extra present
        val size = handler.resolveAdSize(parameters(adaptive = false, adaptiveWidthDp = 200), MaxAdFormat.BANNER, activity)

        // Then it stays a standard banner
        assertEquals(VelocityBannerAdSize.BANNER, size)
    }

    @Test
    fun `resolveAdSize uses explicit adaptive width when adaptive enabled`() {
        // Given — a width comfortably above the SDK's adaptive minimum so it is passed
        // through verbatim (and is distinguishable from the screen-width fallback).
        val explicitWidthDp = 728
        val size = handler.resolveAdSize(parameters(adaptive = true, adaptiveWidthDp = explicitWidthDp), MaxAdFormat.BANNER, activity)

        // Then — resolves to the SDK's adaptive size for the explicit width, not the screen width.
        assertEquals(VelocityBannerAdSize.getAdaptiveBannerAdSize(activity, explicitWidthDp), size)
        assertEquals(explicitWidthDp, size.widthDp)
        assertNotEquals(
            VelocityBannerAdSize.getAdaptiveBannerAdSize(activity, activity.resources.configuration.screenWidthDp),
            size,
        )
    }

    @Test
    fun `resolveAdSize falls back to screen width when adaptive enabled without explicit width`() {
        // Given
        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val size = handler.resolveAdSize(parameters(adaptive = true, adaptiveWidthDp = null), MaxAdFormat.BANNER, activity)

        // Then — matches the SDK's adaptive size computed from the current screen width.
        assertEquals(VelocityBannerAdSize.getAdaptiveBannerAdSize(activity, screenWidthDp), size)
    }

    @Test
    fun `resolveAdSize keeps MREC fixed even when adaptive enabled`() {
        val size = handler.resolveAdSize(parameters(adaptive = true, adaptiveWidthDp = 500), MaxAdFormat.MREC, activity)
        assertEquals(VelocityBannerAdSize.MREC, size)
    }

    // ========== listener forwarding ==========

    @Test
    fun `onAdLoaded forwards to onAdViewAdLoaded with the banner view`() {
        // Given
        val view = VelocityBannerAdView(activity)
        val ad = mock(VelocityBannerAd::class.java)
        val velocityListener = handler.createListener(view, listener)

        // When
        velocityListener.onAdLoaded(ad)

        // Then
        verify(listener).onAdViewAdLoaded(view)
    }

    @Test
    fun `onAdFailedToLoad forwards mapped error to onAdViewAdLoadFailed`() {
        // Given
        val view = VelocityBannerAdView(activity)
        val ad = mock(VelocityBannerAd::class.java)
        val velocityListener = handler.createListener(view, listener)

        // When
        velocityListener.onAdFailedToLoad(ad, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        // Then
        val errorCaptor = ArgumentCaptor.forClass(MaxAdapterError::class.java)
        verify(listener).onAdViewAdLoadFailed(errorCaptor.capture())
        assertEquals(MaxAdapterError.NO_FILL.code, errorCaptor.value.code)
        assertEquals(VelocityAdsErrorCode.NO_FILL, errorCaptor.value.mediatedNetworkErrorCode)
        assertEquals("no fill", errorCaptor.value.mediatedNetworkErrorMessage)
    }

    @Test
    fun `onAdImpression forwards to onAdViewAdDisplayed`() {
        // Given
        val view = VelocityBannerAdView(activity)
        val ad = mock(VelocityBannerAd::class.java)
        val velocityListener = handler.createListener(view, listener)

        // When
        velocityListener.onAdImpression(ad)

        // Then
        verify(listener).onAdViewAdDisplayed()
    }

    @Test
    fun `onAdClicked forwards to onAdViewAdClicked`() {
        // Given
        val view = VelocityBannerAdView(activity)
        val ad = mock(VelocityBannerAd::class.java)
        val velocityListener = handler.createListener(view, listener)

        // When
        velocityListener.onAdClicked(ad)

        // Then
        verify(listener).onAdViewAdClicked()
    }

    @Test
    fun `onAdFailedToShow forwards mapped error to onAdViewAdDisplayFailed`() {
        // Given
        val view = VelocityBannerAdView(activity)
        val ad = mock(VelocityBannerAd::class.java)
        val velocityListener = handler.createListener(view, listener)

        // When
        velocityListener.onAdFailedToShow(ad, VelocityAdsError(VelocityAdsErrorCode.INTERNAL_ERROR, "boom"))

        // Then
        val errorCaptor = ArgumentCaptor.forClass(MaxAdapterError::class.java)
        verify(listener).onAdViewAdDisplayFailed(errorCaptor.capture())
        assertEquals(MaxAdapterError.INTERNAL_ERROR.code, errorCaptor.value.code)
        assertEquals(VelocityAdsErrorCode.INTERNAL_ERROR, errorCaptor.value.mediatedNetworkErrorCode)
        assertEquals("boom", errorCaptor.value.mediatedNetworkErrorMessage)
    }

    @Test
    fun `destroy without a prior load is a safe no-op`() {
        // When / Then — no interactions with the listener
        handler.destroy()
        verifyNoInteractions(listener)
    }
}
