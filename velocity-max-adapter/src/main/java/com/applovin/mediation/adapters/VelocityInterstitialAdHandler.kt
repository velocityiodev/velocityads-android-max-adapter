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
    // Main-thread-confined: MAX and Velocity both deliver all callbacks on the main thread,
    // so no cross-thread visibility guarantee is needed here.
    private var listener: MaxInterstitialAdapterListener,
    private val onDismissed: () -> Unit = {},
) : VelocityInterstitialAdListener {
    /**
     * Wires the show-time listener so that display callbacks (shown, clicked, hidden) are
     * delivered to the listener that MAX provides at show time, which may differ from the
     * load-time listener.
     */
    fun attachShowListener(showListener: MaxInterstitialAdapterListener) {
        listener = showListener
    }

    override fun onAdLoaded(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdLoaded()
    }

    override fun onAdFailedToLoad(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onInterstitialAdLoadFailed(maxError)
    }

    override fun onAdShown(ad: VelocityFullscreenAd) {
        listener.onInterstitialAdDisplayed()
    }

    override fun onAdImpression(ad: VelocityFullscreenAd) {
        // Velocity impression confirmed — no additional MAX signal needed here.
    }

    override fun onAdFailedToShow(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
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
