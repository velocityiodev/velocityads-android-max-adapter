package com.applovin.mediation.adapters

import android.net.Uri
import android.util.Log
import android.view.View
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.mediation.nativeAds.MaxNativeAd.MaxNativeAdImage
import io.velocityads.sdk.listeners.VelocityNativeAdListener
import io.velocityads.sdk.models.VelocityNativeAd

/**
 * Downloads the native ad's main image off the calling thread and delivers a media view
 * ready to hand to MAX (`null` on failure). The completion must be invoked on the main thread.
 */
internal fun interface NativeMediaViewLoader {
    fun load(
        url: String,
        completion: (View?) -> Unit,
    )
}

/**
 * Translates [VelocityNativeAdListener] callbacks to [MaxNativeAdAdapterListener] calls,
 * and assembles the [VelocityMaxNativeAd] delivered to MAX.
 *
 * @param listener The MAX listener to forward ad load and lifecycle events to.
 * @param mediaViewLoader Optional loader used to download the native ad's main image and wrap
 *   it in a [android.view.View] for MAX's media content slot. When `null`, the ad is delivered
 *   without a media view. The loader must invoke its completion on the main thread.
 */
internal class VelocityNativeAdHandler(
    private val listener: MaxNativeAdAdapterListener,
    private val mediaViewLoader: NativeMediaViewLoader? = null,
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
        val mainImageUrl =
            data.squareImageUrl?.takeIf { it.isNotBlank() }
                ?: data.largeImageUrl?.takeIf { it.isNotBlank() }

        val builder =
            MaxNativeAd
                .Builder()
                .setAdFormat(MaxAdFormat.NATIVE)
                .setTitle(data.title)
                .setBody(data.description)
                .setCallToAction(data.callToAction)
                .setAdvertiser(data.advertiserName)
                .setIcon(iconImage)
                .setMainImage(mainImageUrl?.let { MaxNativeAdImage(Uri.parse(it)) })

        // MaxNativeAdView only renders getMediaView() into the media content view group —
        // it never falls back to mainImage — so the adapter must download the image itself
        // and hand MAX a ready view (the same pattern AppLovin's URL-based network
        // adapters use). Failure is non-fatal: the ad is delivered without media.
        val loader = mediaViewLoader
        if (loader != null && mainImageUrl != null) {
            loader.load(mainImageUrl) { mediaView ->
                if (mediaView != null) {
                    builder.setMediaView(mediaView)
                } else {
                    Log.w(TAG, "Failed to load native ad media image — delivering ad without media view")
                }
                listener.onNativeAdLoaded(VelocityMaxNativeAd(builder, nativeAd), null)
            }
        } else {
            listener.onNativeAdLoaded(VelocityMaxNativeAd(builder, nativeAd), null)
        }
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
