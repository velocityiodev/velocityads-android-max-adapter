package com.applovin.mediation.adapters

import android.app.Activity
import android.util.Log
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import io.velocityads.sdk.models.VelocityInterstitialAd
import io.velocityads.sdk.models.VelocityInterstitialAdRequest

/**
 * Handles the [MaxInterstitialAdapter] contract for [VelocityAdsMediationAdapter].
 *
 * Owns the [VelocityInterstitialAd] and [VelocityInterstitialAdHandler] for the current load
 * cycle. All methods are expected to be called on the main thread — MAX and Velocity both
 * guarantee main-thread delivery for adapter entry points and listener callbacks.
 */
internal class VelocityInterstitialFormatAdapter(
    private val ctx: FormatAdapterContext,
) {
    companion object {
        private const val TAG = "VelocityAdsAdapter"
    }

    private var ad: VelocityInterstitialAd? = null
    private var handler: VelocityInterstitialAdHandler? = null

    fun load(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        ctx.forwardPrivacySettings()

        ctx.ensureInitialized(parameters) { initialized ->
            if (ctx.isDestroyed) return@ensureInitialized
            if (!initialized) {
                listener.onInterstitialAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            ad?.destroy()
            ad = null

            val adRequest = VelocityInterstitialAdRequest.Builder(adUnitId).build()
            val newAd = VelocityInterstitialAd(adRequest)
            ad = newAd
            val newHandler =
                VelocityInterstitialAdHandler(
                    listener,
                    onDismissed = {
                        // Clear the ad and its handler together so a dismissed ad does not
                        // strand a stale handler reference on this adapter. Guarded on
                        // identity so a newer load cycle's state is never clobbered.
                        if (ad === newAd) {
                            ad = null
                            handler = null
                        }
                    },
                )
            handler = newHandler
            newAd.load(newHandler)
        }
    }

    fun show(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) {
        val currentAd = ad
        if (currentAd == null || !currentAd.isReady) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }
        if (activity == null) {
            Log.e(TAG, "Cannot show interstitial: Activity is null")
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        val currentHandler = handler
        if (currentHandler == null) {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError.INVALID_LOAD_STATE)
            return
        }
        currentHandler.attachShowListener(listener)
        currentAd.show(activity)
    }

    fun destroy() {
        ad?.destroy()
        ad = null
        handler = null
    }
}
