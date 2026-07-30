package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import io.velocityads.sdk.listeners.VelocityInterstitialAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityFullscreenAd

/**
 * Translates [VelocityInterstitialAdListener] callbacks to [MaxInterstitialAdapterListener] calls.
 *
 * @param onDismissed Optional hook invoked after [onAdDismissed] has forwarded to MAX.
 *                    Used by the adapter to release its ad reference once the ad is fully gone.
 */
internal class VelocityInterstitialAdHandler(
    private val listener: MaxInterstitialAdapterListener,
    private val onDismissed: () -> Unit = {},
) : VelocityInterstitialAdListener {

    override fun onAdLoaded(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdLoaded()
    }

    override fun onAdFailedToLoad(ad: VelocityFullscreenAd, error: VelocityAdsError) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onInterstitialAdLoadFailed(maxError)
    }

    override fun onAdShown(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdDisplayed()
    }

    override fun onAdImpression(ad: VelocityFullscreenAd) {
        // MAX records impression via onInterstitialAdDisplayed; no separate call needed.
    }

    override fun onAdFailedToShow(ad: VelocityFullscreenAd, error: VelocityAdsError) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onInterstitialAdDisplayFailed(maxError)
    }

    override fun onAdClicked(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdClicked()
    }

    override fun onAdDismissed(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdHidden()
        onDismissed()
    }
}
