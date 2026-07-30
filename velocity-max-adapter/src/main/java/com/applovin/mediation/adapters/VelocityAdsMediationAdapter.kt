package com.applovin.mediation.adapters

import android.app.Activity
import android.content.Context
import android.util.Log
import com.applovin.mediation.MaxAdapterError
import com.applovin.mediation.adapter.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.MaxAdapterResponseParameters
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxNativeAdAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.sdk.AppLovinSdk
import io.velocityads.sdk.VelocityAds
import io.velocityads.sdk.listeners.VelocityAdsInitListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsInitRequest
import io.velocityads.sdk.models.VelocityInterstitialAd
import io.velocityads.sdk.models.VelocityInterstitialAdRequest
import io.velocityads.sdk.models.VelocityNativeAd
import io.velocityads.sdk.models.VelocityNativeAdRequest
import io.velocityads.sdk.models.VelocityRewardedAd
import io.velocityads.sdk.models.VelocityRewardedAdRequest

/**
 * AppLovin MAX custom-network adapter for the Velocity Ads SDK.
 *
 * Custom network class name: `com.applovin.mediation.adapters.VelocityAdsMediationAdapter`
 */
class VelocityAdsMediationAdapter(sdk: AppLovinSdk) : MediationAdapterBase(sdk),
    MaxInterstitialAdapter,
    MaxRewardedAdapter,
    MaxNativeAdAdapter {

    companion object {
        private const val TAG = "VelocityAdsAdapter"
    }

    // ---- ad object holders ----
    private var interstitialAd: VelocityInterstitialAd? = null
    private var rewardedAd: VelocityRewardedAd? = null
    private var nativeAd: VelocityNativeAd? = null

    // =========================================================================
    // MediationAdapterBase
    // =========================================================================

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        onCompletionListener: OnCompletionListener
    ) {
        if (VelocityAds.isInitialized()) {
            onCompletionListener.onCompletion(InitializationStatus.INITIALIZED_SUCCESS, null)
            return
        }

        val appKey = parameters.serverParameters.getString("app_key")
        if (appKey.isNullOrBlank()) {
            onCompletionListener.onCompletion(
                InitializationStatus.INITIALIZED_FAILURE,
                "Velocity Ads: missing app_key in server parameters"
            )
            return
        }

        val context: Context = activity ?: appLovinSdk.applicationContext

        parameters.hasUserConsent?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell?.let { VelocityAds.setDoNotSell(it) }

        val initRequest = VelocityAdsInitRequest.Builder(appKey).build()

        VelocityAds.initSDK(context, initRequest, object : VelocityAdsInitListener {
            override fun onInitSuccess() {
                onCompletionListener.onCompletion(InitializationStatus.INITIALIZED_SUCCESS, null)
            }

            override fun onInitFailure(error: VelocityAdsError) {
                onCompletionListener.onCompletion(
                    InitializationStatus.INITIALIZED_FAILURE,
                    "Velocity Ads init failed [${error.code}]: ${error.message}"
                )
            }
        })
    }

    override fun getSdkVersion(): String = VelocityAds.getSdkVersion()

    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION

    override fun onDestroy() {
        interstitialAd?.destroy()
        interstitialAd = null

        rewardedAd?.destroy()
        rewardedAd = null

        nativeAd?.destroy()
        nativeAd = null
    }

    // =========================================================================
    // MaxInterstitialAdapter
    // =========================================================================

    override fun loadInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener
    ) {
        val adUnitId = parameters.thirdPartyAdPlacementId
        if (adUnitId.isNullOrBlank()) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell?.let { VelocityAds.setDoNotSell(it) }

        val adRequest = VelocityInterstitialAdRequest.Builder(adUnitId).build()
        val handler = VelocityInterstitialAdHandler(listener, onDismissed = { interstitialAd = null })
        val ad = VelocityInterstitialAd(adRequest)
        interstitialAd = ad
        ad.load(handler)
    }

    override fun showInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener
    ) {
        val ad = interstitialAd
        if (ad == null || !ad.isReady) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }

        ad.show(resolveContext(activity))
    }

    // =========================================================================
    // MaxRewardedAdapter
    // =========================================================================

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener
    ) {
        val adUnitId = parameters.thirdPartyAdPlacementId
        if (adUnitId.isNullOrBlank()) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell?.let { VelocityAds.setDoNotSell(it) }

        val adRequest = VelocityRewardedAdRequest.Builder(adUnitId).build()
        val handler = VelocityRewardedAdHandler(listener, onDismissed = { rewardedAd = null })
        val ad = VelocityRewardedAd(adRequest)
        rewardedAd = ad
        ad.load(handler)
    }

    override fun showRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener
    ) {
        val ad = rewardedAd
        if (ad == null || !ad.isReady) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }

        ad.show(resolveContext(activity))
    }

    // =========================================================================
    // MaxNativeAdAdapter
    // =========================================================================

    override fun loadNativeAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxNativeAdAdapterListener
    ) {
        val adUnitId = parameters.thirdPartyAdPlacementId
        if (adUnitId.isNullOrBlank()) {
            listener.onNativeAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell?.let { VelocityAds.setDoNotSell(it) }

        val adRequest = VelocityNativeAdRequest.Builder(adUnitId).build()
        val handler = VelocityNativeAdHandler(listener)
        val ad = VelocityNativeAd(adRequest)
        nativeAd = ad
        ad.load(handler)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns an Activity context for showing fullscreen ads. Falls back to the application
     * context with a warning — this is not recommended but prevents a crash if the activity
     * reference is null at show time.
     */
    private fun resolveContext(activity: Activity?): Context {
        if (activity == null) {
            Log.w(TAG, "Activity is null; falling back to application context. " +
                    "Fullscreen ads may not display correctly.")
            return appLovinSdk.applicationContext
        }
        return activity
    }
}
