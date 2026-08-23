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
    // Main-thread-confined: MAX and Velocity both deliver all callbacks on the main thread,
    // so no cross-thread visibility guarantee is needed here.
    private var listener: MaxRewardedAdapterListener,
    private val onDismissed: () -> Unit = {},
) : VelocityRewardedAdListener {
    /**
     * Wires the show-time listener so that display callbacks (shown, rewarded, hidden) are
     * delivered to the listener that MAX provides at show time, which may differ from the
     * load-time listener.
     */
    fun attachShowListener(showListener: MaxRewardedAdapterListener) {
        listener = showListener
    }

    override fun onAdLoaded(ad: VelocityFullscreenAd) {
        listener.onRewardedAdLoaded()
    }

    override fun onAdFailedToLoad(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onRewardedAdLoadFailed(maxError)
    }

    override fun onAdShown(ad: VelocityFullscreenAd) {
        listener.onRewardedAdDisplayed()
    }

    override fun onAdImpression(ad: VelocityFullscreenAd) {
        // Velocity impression confirmed — no additional MAX signal needed here.
    }

    override fun onAdFailedToShow(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
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
        listener.onUserRewarded(
            object : MaxReward {
                override fun getLabel(): String = MaxReward.DEFAULT_LABEL

                override fun getAmount(): Int = MaxReward.DEFAULT_AMOUNT
            },
        )
    }

    override fun onAdDismissed(ad: VelocityFullscreenAd) {
        listener.onRewardedAdHidden()
        onDismissed()
    }
}
