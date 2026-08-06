package com.applovin.mediation.adapters

import android.net.Uri
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
    override fun onAdLoaded(nativeAd: VelocityNativeAd) {
        // `data` delegates to an internal `lateinit var`; the SDK contract guarantees
        // it is populated before `onAdLoaded` fires, but guard defensively to prevent
        // a crash if an SDK bug violates that guarantee.
        val data = try {
            nativeAd.data
        } catch (_: UninitializedPropertyAccessException) {
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
