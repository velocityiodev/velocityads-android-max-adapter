package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityFullscreenAd
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import kotlin.test.assertTrue

class VelocityInterstitialAdHandlerTest {
    private lateinit var loadListener: MaxInterstitialAdapterListener
    private lateinit var ad: VelocityFullscreenAd

    @Before
    fun setUp() {
        loadListener = mock(MaxInterstitialAdapterListener::class.java)
        ad = mock(VelocityFullscreenAd::class.java)
    }

    // ========== Listener forwarding ==========

    @Test
    fun `onAdLoaded forwards to onInterstitialAdLoaded`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdLoaded(ad)

        // Then
        verify(loadListener).onInterstitialAdLoaded()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdFailedToLoad forwards mapped error to onInterstitialAdLoadFailed`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdFailedToLoad(ad, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        // Then
        verify(loadListener).onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL)
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdShown forwards to onInterstitialAdDisplayed`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdShown(ad)

        // Then
        verify(loadListener).onInterstitialAdDisplayed()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdImpression does not forward any MAX callback`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdImpression(ad)

        // Then
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdFailedToShow forwards mapped error to onInterstitialAdDisplayFailed`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdFailedToShow(ad, VelocityAdsError(VelocityAdsErrorCode.AD_SPENT, "spent"))

        // Then
        verify(loadListener).onInterstitialAdDisplayFailed(MaxAdapterError.INVALID_LOAD_STATE)
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdClicked forwards to onInterstitialAdClicked`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)

        // When
        handler.onAdClicked(ad)

        // Then
        verify(loadListener).onInterstitialAdClicked()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdDismissed forwards to onInterstitialAdHidden and invokes onDismissed exactly once`() {
        // Given
        var dismissedCount = 0
        val handler = VelocityInterstitialAdHandler(loadListener, onDismissed = { dismissedCount++ })

        // When
        handler.onAdDismissed(ad)

        // Then
        verify(loadListener).onInterstitialAdHidden()
        assertTrue(dismissedCount == 1, "onDismissed should be invoked exactly once")
    }

    // ========== Show-listener swap ==========

    @Test
    fun `attachShowListener routes display callbacks to show listener instead of load listener`() {
        // Given
        val handler = VelocityInterstitialAdHandler(loadListener)
        val showListener = mock(MaxInterstitialAdapterListener::class.java)

        // When
        handler.attachShowListener(showListener)
        handler.onAdShown(ad)
        handler.onAdClicked(ad)
        handler.onAdDismissed(ad)

        // Then
        verify(showListener).onInterstitialAdDisplayed()
        verify(showListener).onInterstitialAdClicked()
        verify(showListener).onInterstitialAdHidden()
        verify(loadListener, never()).onInterstitialAdDisplayed()
        verify(loadListener, never()).onInterstitialAdClicked()
        verify(loadListener, never()).onInterstitialAdHidden()
    }
}
