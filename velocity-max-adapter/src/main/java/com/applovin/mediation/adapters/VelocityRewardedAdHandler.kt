package com.applovin.mediation.adapters

import com.applovin.mediation.MaxReward
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import io.velocityads.sdk.listeners.VelocityRewardedAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityFullscreenAd

/**
 * Translates [VelocityRewardedAdListener] callbacks to [MaxRewardedAdapterListener] calls.
 *
 * @param onDismissed Optional hook invoked after [onAdDismissed] has forwarded to MAX.
 *                    Used by the adapter to release its ad reference once the ad is fully gone.
 */
internal class VelocityRewardedAdHandler(
    private val listener: MaxRewardedAdapterListener,
    private val onDismissed: () -> Unit = {},
) : VelocityRewardedAdListener {

    override fun onAdLoaded(ad: VelocityFullscreenAd) {
        listener.onRewardedAdLoaded()
    }

    override fun onAdFailedToLoad(ad: VelocityFullscreenAd, error: VelocityAdsError) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onRewardedAdLoadFailed(maxError)
    }

    override fun onAdShown(ad: VelocityFullscreenAd) {
        listener.onRewardedAdDisplayed()
    }

    override fun onAdImpression(ad: VelocityFullscreenAd) {
        // MAX records impression via onRewardedAdDisplayed; no separate call needed.
    }

    override fun onAdFailedToShow(ad: VelocityFullscreenAd, error: VelocityAdsError) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onRewardedAdDisplayFailed(maxError)
    }

    override fun onAdClicked(ad: VelocityFullscreenAd) {
        listener.onRewardedAdClicked()
    }

    /**
     * Fires before [onAdDismissed]. Velocity does not provide currency/amount, so we use
     * MAX defaults.
     */
    override fun onUserRewarded(ad: VelocityFullscreenAd) {
        listener.onUserRewarded(MaxReward.create(MaxReward.DEFAULT_AMOUNT, ""))
    }

    override fun onAdDismissed(ad: VelocityFullscreenAd) {
        listener.onRewardedAdHidden()
        onDismissed()
    }
}
