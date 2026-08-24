package com.applovin.mediation.adapters

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import io.velocityads.sdk.models.VelocityNativeAd

/**
 * [MaxNativeAd] subclass that bridges interaction registration to the Velocity native ad.
 *
 * Both MAX render paths are covered: [prepareForInteraction] handles manual
 * (publisher-rendered) native ads — MAX has no fallback on that path, so failing to
 * override it silently drops all click/impression tracking — and
 * [prepareViewForInteraction] handles the template / [MaxNativeAdView] path.
 */
internal class VelocityMaxNativeAd(
    builder: Builder,
    private val velocityNativeAd: VelocityNativeAd,
) : MaxNativeAd(builder) {
    companion object {
        private const val TAG = "VelocityMaxNativeAd"
    }

    override fun prepareForInteraction(
        clickableViews: List<View>,
        container: ViewGroup,
    ): Boolean {
        val views =
            clickableViews.ifEmpty {
                Log.w(TAG, "No clickable views provided — falling back to container for click tracking")
                listOf(container)
            }
        registerForInteraction(container, views)
        return true
    }

    // Deprecated in MAX 13 in favor of prepareForInteraction (which this class overrides and
    // handles), but kept as the documented fallback for template renders on older MAX SDKs
    // and the hybrid native activity path.
    @Suppress("OVERRIDE_DEPRECATION")
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

        registerForInteraction(maxNativeAdView, clickableViews)
    }

    private fun registerForInteraction(
        adView: View,
        clickableViews: List<View>,
    ) {
        velocityNativeAd.registerViewForInteraction(adView, clickableViews)
    }
}
