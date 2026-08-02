package com.applovin.mediation.adapters

import android.app.Activity
import android.util.Log
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxNativeAdAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.mediation.adapters.velocity.BuildConfig
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
    @Volatile private var interstitialAd: VelocityInterstitialAd? = null
    @Volatile private var rewardedAd: VelocityRewardedAd? = null
    @Volatile private var nativeAd: VelocityNativeAd? = null

    // ---- handler holders (needed to wire the show-time listener) ----
    private var interstitialAdHandler: VelocityInterstitialAdHandler? = null
    private var rewardedAdHandler: VelocityRewardedAdHandler? = null

    // =========================================================================
    // MediationAdapterBase
    // =========================================================================

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        onCompletionListener: MaxAdapter.OnCompletionListener
    ) {
        if (VelocityAds.isInitialized()) {
            onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
            return
        }

        val appKey = parameters.getServerParameters().getString("app_key")
        if (appKey.isNullOrBlank()) {
            onCompletionListener.onCompletion(
                MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
                "Velocity Ads: missing app_key in server parameters"
            )
            return
        }

        val context = activity ?: getApplicationContext()

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        val initRequest = VelocityAdsInitRequest.Builder(appKey).build()

        VelocityAds.initSDK(context, initRequest, object : VelocityAdsInitListener {
            override fun onInitSuccess() {
                onCompletionListener.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
            }

            override fun onInitFailure(error: VelocityAdsError) {
                onCompletionListener.onCompletion(
                    MaxAdapter.InitializationStatus.INITIALIZED_FAILURE,
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
        interstitialAdHandler = null

        rewardedAd?.destroy()
        rewardedAd = null
        rewardedAdHandler = null

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
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        interstitialAd?.destroy()
        interstitialAd = null

        val adRequest = VelocityInterstitialAdRequest.Builder(adUnitId).build()
        val ad = VelocityInterstitialAd(adRequest)
        interstitialAd = ad
        val handler = VelocityInterstitialAdHandler(
            listener,
            onDismissed = { if (interstitialAd === ad) interstitialAd = null },
        )
        interstitialAdHandler = handler
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
        if (activity == null) {
            Log.e(TAG, "Cannot show interstitial: Activity is null")
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        interstitialAdHandler?.attachShowListener(listener)
        ad.show(activity)
    }

    // =========================================================================
    // MaxRewardedAdapter
    // =========================================================================

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        rewardedAd?.destroy()
        rewardedAd = null

        val adRequest = VelocityRewardedAdRequest.Builder(adUnitId).build()
        val ad = VelocityRewardedAd(adRequest)
        rewardedAd = ad
        val handler = VelocityRewardedAdHandler(
            listener,
            onDismissed = { if (rewardedAd === ad) rewardedAd = null },
        )
        rewardedAdHandler = handler
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
        if (activity == null) {
            Log.e(TAG, "Cannot show rewarded ad: Activity is null")
            listener.onRewardedAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        rewardedAdHandler?.attachShowListener(listener)
        ad.show(activity)
    }

    // =========================================================================
    // MaxNativeAdAdapter
    // =========================================================================

    override fun loadNativeAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxNativeAdAdapterListener
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onNativeAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        parameters.hasUserConsent()?.let { VelocityAds.setConsent(it) }
        parameters.isDoNotSell()?.let { VelocityAds.setDoNotSell(it) }

        nativeAd?.destroy()
        nativeAd = null

        val adRequest = VelocityNativeAdRequest.Builder(adUnitId).build()
        val handler = VelocityNativeAdHandler(listener)
        val ad = VelocityNativeAd(adRequest)
        nativeAd = ad
        ad.load(handler)
    }
}
