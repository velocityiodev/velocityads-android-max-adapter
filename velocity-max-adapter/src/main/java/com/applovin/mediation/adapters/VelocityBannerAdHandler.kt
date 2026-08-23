package com.applovin.mediation.adapters

import android.app.Activity
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
    }

    private var bannerAd: VelocityBannerAd? = null
    private var bannerAdView: VelocityBannerAdView? = null

    fun load(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) {
        val context = activity ?: run {
            listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }
        val adUnitId = parameters.thirdPartyAdPlacementId
        if (adUnitId.isNullOrBlank()) {
            listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        // Adaptive width param takes precedence over adFormat.
        val adaptiveWidthDp = (parameters.localExtraParameters["adaptive_banner_width"] as? Number)?.toInt()
        val size = when {
            adaptiveWidthDp != null -> VelocityBannerAdSize.getAdaptiveBannerAdSize(context, adaptiveWidthDp)
            adFormat == MaxAdFormat.MREC -> VelocityBannerAdSize.MREC
            adFormat == MaxAdFormat.LEADER -> VelocityBannerAdSize.LEADERBOARD
            else -> VelocityBannerAdSize.BANNER
        }

        val sizeLabel = if (adaptiveWidthDp != null) "adaptive(requestedWidth=${adaptiveWidthDp}dp)" else adFormat.label
        Log.d(TAG, "Loading banner: adUnitId='$adUnitId' format=$sizeLabel resolvedSize=${size.width}x${size.height}dp")

        val view = VelocityBannerAdView(context)
        bannerAdView = view

        val request = VelocityBannerAdRequest.Builder(adUnitId, size).build()
        val ad = VelocityBannerAd(request)
        bannerAd = ad

        ad.load(
            view,
            object : VelocityBannerAdListener {
                override fun onAdLoaded(ad: VelocityBannerAd) = listener.onAdViewAdLoaded(view)

                override fun onAdFailedToLoad(ad: VelocityBannerAd, error: VelocityAdsError) =
                    listener.onAdViewAdLoadFailed(VelocityAdsErrorMapper.toMaxAdapterError(error))

                override fun onAdImpression(ad: VelocityBannerAd) = listener.onAdViewAdDisplayed()

                override fun onAdClicked(ad: VelocityBannerAd) = listener.onAdViewAdClicked()

                override fun onAdFailedToShow(ad: VelocityBannerAd, error: VelocityAdsError) =
                    listener.onAdViewAdDisplayFailed(VelocityAdsErrorMapper.toMaxAdapterError(error))
            },
        )
    }

    fun destroy() {
        bannerAd?.destroy()
        bannerAd = null
        bannerAdView = null
    }
}
