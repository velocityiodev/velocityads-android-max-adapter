package com.applovin.mediation.adapters

import android.util.Log
import android.view.View
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import io.velocityads.sdk.models.VelocityNativeAd

/**
 * [MaxNativeAd] subclass that bridges interaction registration to the Velocity native ad.
 */
internal class VelocityMaxNativeAd(
    builder: Builder,
    private val velocityNativeAd: VelocityNativeAd,
) : MaxNativeAd(builder) {
    companion object {
        private const val TAG = "VelocityMaxNativeAd"
    }

    override fun prepareViewForInteraction(maxNativeAdView: MaxNativeAdView) {
        val clickableViews = mutableListOf<View>()

        maxNativeAdView.titleTextView?.let { clickableViews.add(it) }
        maxNativeAdView.bodyTextView?.let { clickableViews.add(it) }
        maxNativeAdView.callToActionButton?.let { clickableViews.add(it) }
        maxNativeAdView.iconImageView?.let { clickableViews.add(it) }
        maxNativeAdView.advertiserTextView?.let { clickableViews.add(it) }
        // MAX renders the main image / media inside the media content view group — register
        // it so taps on the creative area are billable clicks.
        maxNativeAdView.mediaContentViewGroup?.let { clickableViews.add(it) }

        if (clickableViews.isEmpty()) {
            Log.w(TAG, "No sub-views found in MaxNativeAdView — falling back to root view for click tracking")
            clickableViews.add(maxNativeAdView)
        }

        velocityNativeAd.registerViewForInteraction(maxNativeAdView, clickableViews)
    }
}
