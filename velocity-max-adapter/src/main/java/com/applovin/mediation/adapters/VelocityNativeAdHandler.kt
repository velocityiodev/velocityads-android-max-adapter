package com.applovin.mediation.adapters

import android.net.Uri
import android.util.Log
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.mediation.nativeAds.MaxNativeAd.MaxNativeAdImage
import io.velocityads.sdk.listeners.VelocityNativeAdListener
import io.velocityads.sdk.models.VelocityNativeAd

/**
 * Translates [VelocityNativeAdListener] callbacks to [MaxNativeAdAdapterListener] calls,
 * and assembles the [VelocityMaxNativeAd] delivered to MAX.
 */
internal class VelocityNativeAdHandler(
    private val listener: MaxNativeAdAdapterListener,
) : VelocityNativeAdListener {
    companion object {
        private const val TAG = "VelocityNativeAdHandler"
    }

    override fun onAdLoaded(nativeAd: VelocityNativeAd) {
        // The SDK contract guarantees `data` is populated before `onAdLoaded` fires.
        // Accessing it earlier throws, so catch the violation and surface a logged
        // error instead of crashing the host app.
        val data =
            try {
                nativeAd.data
            } catch (e: UninitializedPropertyAccessException) {
                Log.e(TAG, "onAdLoaded fired before data was set — SDK contract violation", e)
                listener.onNativeAdLoadFailed(MaxAdapterError.INTERNAL_ERROR)
                return
            }

        val iconImage =
            data.advertiserIconUrl
                .takeIf { it.isNotBlank() }
                ?.let { MaxNativeAdImage(Uri.parse(it)) }

        // Prefer square crop; fall back to landscape hero.
        val mainImage =
            (data.squareImageUrl?.takeIf { it.isNotBlank() } ?: data.largeImageUrl?.takeIf { it.isNotBlank() })
                ?.let { MaxNativeAdImage(Uri.parse(it)) }

        val maxNativeAd =
            VelocityMaxNativeAd(
                builder =
                    MaxNativeAd
                        .Builder()
                        .setAdFormat(MaxAdFormat.NATIVE)
                        .setTitle(data.title)
                        .setBody(data.description)
                        .setCallToAction(data.callToAction)
                        .setAdvertiser(data.advertiserName)
                        .setIcon(iconImage)
                        .setMainImage(mainImage),
                velocityNativeAd = nativeAd,
            )

        listener.onNativeAdLoaded(maxNativeAd, null)
    }

    override fun onAdFailedToLoad(
        nativeAd: VelocityNativeAd,
        error: io.velocityads.sdk.models.VelocityAdsError,
    ) {
        val maxError = VelocityAdsErrorMapper.toMaxAdapterError(error)
        listener.onNativeAdLoadFailed(maxError)
    }

    override fun onAdImpression(nativeAd: VelocityNativeAd) {
        listener.onNativeAdDisplayed(null)
    }

    override fun onAdClicked(nativeAd: VelocityNativeAd) {
        listener.onNativeAdClicked(null)
    }
}
