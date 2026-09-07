package com.applovin.mediation.adapters

import com.applovin.mediation.MaxReward
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityFullscreenAd
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class VelocityRewardedAdHandlerTest {
    private lateinit var loadListener: MaxRewardedAdapterListener
    private lateinit var ad: VelocityFullscreenAd

    @Before
    fun setUp() {
        loadListener = mock(MaxRewardedAdapterListener::class.java)
        ad = mock(VelocityFullscreenAd::class.java)
    }

    // ========== Listener forwarding ==========

    @Test
    fun `onAdLoaded forwards to onRewardedAdLoaded`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdLoaded(ad)

        // Then
        verify(loadListener).onRewardedAdLoaded()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdFailedToLoad forwards mapped error to onRewardedAdLoadFailed`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdFailedToLoad(ad, VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))

        // Then
        val errorCaptor = ArgumentCaptor.forClass(MaxAdapterError::class.java)
        verify(loadListener).onRewardedAdLoadFailed(errorCaptor.capture())
        assertEquals(MaxAdapterError.NO_CONNECTION.code, errorCaptor.value.code)
        assertEquals(VelocityAdsErrorCode.NETWORK_ERROR, errorCaptor.value.mediatedNetworkErrorCode)
        assertEquals("offline", errorCaptor.value.mediatedNetworkErrorMessage)
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdShown forwards to onRewardedAdDisplayed`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdShown(ad)

        // Then
        verify(loadListener).onRewardedAdDisplayed()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdImpression does not forward any MAX callback`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdImpression(ad)

        // Then
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdFailedToShow forwards mapped error to onRewardedAdDisplayFailed`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdFailedToShow(ad, VelocityAdsError(VelocityAdsErrorCode.INTERNAL_ERROR, "boom"))

        // Then
        val errorCaptor = ArgumentCaptor.forClass(MaxAdapterError::class.java)
        verify(loadListener).onRewardedAdDisplayFailed(errorCaptor.capture())
        assertEquals(MaxAdapterError.INTERNAL_ERROR.code, errorCaptor.value.code)
        assertEquals(VelocityAdsErrorCode.INTERNAL_ERROR, errorCaptor.value.mediatedNetworkErrorCode)
        assertEquals("boom", errorCaptor.value.mediatedNetworkErrorMessage)
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onAdClicked forwards to onRewardedAdClicked`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)

        // When
        handler.onAdClicked(ad)

        // Then
        verify(loadListener).onRewardedAdClicked()
        verifyNoMoreInteractions(loadListener)
    }

    @Test
    fun `onUserRewarded delivers MAX default reward label and amount when no supplier injected`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)
        val rewardCaptor = ArgumentCaptor.forClass(MaxReward::class.java)

        // When
        handler.onUserRewarded(ad)

        // Then
        verify(loadListener).onUserRewarded(rewardCaptor.capture())
        assertEquals(MaxReward.DEFAULT_LABEL, rewardCaptor.value.label)
        assertEquals(MaxReward.DEFAULT_AMOUNT, rewardCaptor.value.amount)
    }

    @Test
    fun `onUserRewarded delivers the injected reward supplier's reward`() {
        // Given — a dashboard-configured reward, as the adapter's getReward() would supply
        val configuredReward =
            object : MaxReward {
                override fun getLabel(): String = "coins"

                override fun getAmount(): Int = 50
            }
        val handler = VelocityRewardedAdHandler(loadListener, rewardSupplier = { configuredReward })
        val rewardCaptor = ArgumentCaptor.forClass(MaxReward::class.java)

        // When
        handler.onUserRewarded(ad)

        // Then
        verify(loadListener).onUserRewarded(rewardCaptor.capture())
        assertEquals("coins", rewardCaptor.value.label)
        assertEquals(50, rewardCaptor.value.amount)
    }

    @Test
    fun `onAdDismissed forwards to onRewardedAdHidden and invokes onDismissed exactly once`() {
        // Given
        var dismissedCount = 0
        val handler = VelocityRewardedAdHandler(loadListener, onDismissed = { dismissedCount++ })

        // When
        handler.onAdDismissed(ad)

        // Then
        verify(loadListener).onRewardedAdHidden()
        assertTrue(dismissedCount == 1, "onDismissed should be invoked exactly once")
    }

    // ========== Show-listener swap ==========

    @Test
    fun `attachShowListener routes display callbacks to show listener instead of load listener`() {
        // Given
        val handler = VelocityRewardedAdHandler(loadListener)
        val showListener = mock(MaxRewardedAdapterListener::class.java)

        // When
        handler.attachShowListener(showListener)
        handler.onAdShown(ad)
        handler.onAdDismissed(ad)

        // Then
        verify(showListener).onRewardedAdDisplayed()
        verify(showListener).onRewardedAdHidden()
        verify(loadListener, never()).onRewardedAdDisplayed()
        verify(loadListener, never()).onRewardedAdHidden()
    }
}
