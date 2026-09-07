package com.applovin.mediation.adapters

import android.app.Activity
import android.content.Context
import android.util.Log
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import io.velocityads.sdk.ads.banner.VelocityBannerAdView
import io.velocityads.sdk.listeners.VelocityBannerAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityBannerAd
import io.velocityads.sdk.models.VelocityBannerAdRequest
import io.velocityads.sdk.models.VelocityBannerAdSize

/**
 * Translates [VelocityBannerAdListener] callbacks to [MaxAdViewAdapterListener] calls and
 * manages the [VelocityBannerAd] / [VelocityBannerAdView] lifecycle.
 */
internal class VelocityBannerAdHandler {
    companion object {
        private const val TAG = "VelocityAdsAdapter"

        /** Local-extra key MAX sets with the container width (dp) for adaptive banners. */
        private const val ADAPTIVE_WIDTH_KEY = "adaptive_banner_width"

        /** Server-parameter key MAX sets to `true` when the ad unit is an adaptive banner. */
        private const val ADAPTIVE_BANNER_KEY = "adaptive_banner"
    }

    // @Volatile ensures that a destroy() call from any thread sees the most recent value written
    // by load() on the main thread — VelocityBannerAd.destroy() is safe to call from any thread.
    @Volatile private var bannerAd: VelocityBannerAd? = null

    /**
     * Loads a banner ad for [parameters] into a new [VelocityBannerAdView].
     *
     * [VelocityBannerFormatAdapter] validates [activity] and the ad unit ID before calling
     * [com.applovin.mediation.adapters.FormatAdapterContext.ensureInitialized], so these
     * guards are defense-in-depth for any caller that bypasses the format adapter.
     */
    fun load(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) {
        val context =
            activity ?: run {
                listener.onAdViewAdLoadFailed(MaxAdapterError.MISSING_ACTIVITY)
                return
            }
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        val size = resolveAdSize(parameters, adFormat, context)
        Log.d(TAG, "Loading banner: adUnitId='$adUnitId' format=${adFormat.label} resolvedSize=${size.widthDp}x${size.heightDp}dp")

        val view = VelocityBannerAdView(context)

        val request = VelocityBannerAdRequest.Builder(adUnitId, size).build()
        val ad = VelocityBannerAd(request)
        bannerAd = ad

        ad.load(view, createListener(view, listener))
    }

    /**
     * Resolves the Velocity banner size for a MAX ad-view request.
     *
     * Adaptive sizing is gated on the `adaptive_banner` server parameter (AppLovin's
     * contract), and applies only to the anchored banner slot — MREC and leaderboard keep
     * their fixed IAB sizes. When adaptive is enabled without an explicit
     * `adaptive_banner_width` local-extra, the current screen width is used so the banner
     * fills the available width rather than falling back to a fixed 320 dp.
     */
    internal fun resolveAdSize(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        context: Context,
    ): VelocityBannerAdSize {
        val adaptive = parameters.serverParameters.getBoolean(ADAPTIVE_BANNER_KEY, false)
        return when {
            adFormat == MaxAdFormat.MREC -> {
                VelocityBannerAdSize.MREC
            }

            adFormat == MaxAdFormat.LEADER -> {
                VelocityBannerAdSize.LEADERBOARD
            }

            adaptive -> {
                val widthDp =
                    (parameters.localExtraParameters[ADAPTIVE_WIDTH_KEY] as? Number)?.toInt()
                        ?: context.resources.configuration.screenWidthDp
                VelocityBannerAdSize.getAdaptiveBannerAdSize(context, widthDp)
            }

            else -> {
                VelocityBannerAdSize.BANNER
            }
        }
    }

    /**
     * Builds the Velocity listener that forwards banner lifecycle events to [listener].
     * [view] is captured so the successful-load callback can hand MAX the exact view that
     * now hosts the creative.
     */
    internal fun createListener(
        view: VelocityBannerAdView,
        listener: MaxAdViewAdapterListener,
    ): VelocityBannerAdListener =
        object : VelocityBannerAdListener {
            override fun onAdLoaded(ad: VelocityBannerAd) = listener.onAdViewAdLoaded(view)

            override fun onAdFailedToLoad(
                ad: VelocityBannerAd,
                error: VelocityAdsError,
            ) = listener.onAdViewAdLoadFailed(VelocityAdsErrorMapper.toMaxAdapterError(error))

            // Intentional: MAX's ad-view contract has no separate impression hook —
            // onAdViewAdDisplayed IS the display/impression signal for banners. We emit it on
            // Velocity's MRC impression (≥ 50% visible for ≥ 1s) rather than on load so MAX's
            // impression is viewability-gated and matches Velocity's own accounting.
            override fun onAdImpression(ad: VelocityBannerAd) = listener.onAdViewAdDisplayed()

            override fun onAdClicked(ad: VelocityBannerAd) = listener.onAdViewAdClicked()

            override fun onAdFailedToShow(
                ad: VelocityBannerAd,
                error: VelocityAdsError,
            ) = listener.onAdViewAdDisplayFailed(VelocityAdsErrorMapper.toMaxAdapterError(error))
        }

    fun destroy() {
        bannerAd?.destroy()
        bannerAd = null
    }
}
