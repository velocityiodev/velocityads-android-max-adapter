package com.applovin.mediation.adapters

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
    override fun prepareViewForInteraction(maxNativeAdView: MaxNativeAdView) {
        val clickableViews = mutableListOf<View>()

        maxNativeAdView.titleTextView?.let { clickableViews.add(it) }
        maxNativeAdView.bodyTextView?.let { clickableViews.add(it) }
        maxNativeAdView.callToActionButton?.let { clickableViews.add(it) }
        maxNativeAdView.iconImageView?.let { clickableViews.add(it) }
        maxNativeAdView.advertiserTextView?.let { clickableViews.add(it) }
        maxNativeAdView.optionsContentView?.let { clickableViews.add(it) }

        velocityNativeAd.registerViewForInteraction(maxNativeAdView, clickableViews)
    }
}
