package com.applovin.mediation.adapters

import android.app.Activity
import android.util.Log
import com.applovin.mediation.MaxReward
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import io.velocityads.sdk.models.VelocityRewardedAd
import io.velocityads.sdk.models.VelocityRewardedAdRequest

/**
 * Handles the [MaxRewardedAdapter] contract for [VelocityAdsMediationAdapter].
 *
 * Owns the [VelocityRewardedAd] and [VelocityRewardedAdHandler] for the current load cycle.
 * The [rewardSupplier] lambda is injected so the adapter can forward the publisher's
 * dashboard-configured reward (amount / currency) via
 * [com.applovin.mediation.adapters.MediationAdapterBase.getReward].
 *
 * All methods are expected to be called on the main thread.
 */
internal class VelocityRewardedFormatAdapter(
    private val ctx: FormatAdapterContext,
    private val rewardSupplier: () -> MaxReward,
    private val configureReward: (MaxAdapterResponseParameters) -> Unit,
) {
    companion object {
        private const val TAG = "VelocityAdsAdapter"
    }

    private var ad: VelocityRewardedAd? = null
    private var handler: VelocityRewardedAdHandler? = null

    fun load(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }

        ctx.forwardPrivacySettings()

        // Capture the publisher's dashboard-configured reward (amount/currency and the
        // always-reward override) so onUserRewarded can deliver it via getReward().
        configureReward(parameters)

        ctx.ensureInitialized(parameters) { initialized ->
            if (ctx.isDestroyed) return@ensureInitialized
            if (!initialized) {
                listener.onRewardedAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            ad?.destroy()
            ad = null

            val adRequest = VelocityRewardedAdRequest.Builder(adUnitId).build()
            val newAd = VelocityRewardedAd(adRequest)
            ad = newAd
            val newHandler =
                VelocityRewardedAdHandler(
                    listener,
                    rewardSupplier = rewardSupplier,
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
        listener: MaxRewardedAdapterListener,
    ) {
        val currentAd = ad
        if (currentAd == null || !currentAd.isReady) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY)
            return
        }
        if (activity == null) {
            Log.e(TAG, "Cannot show rewarded ad: Activity is null")
            listener.onRewardedAdDisplayFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }
        val currentHandler = handler
        if (currentHandler == null) {
            listener.onRewardedAdDisplayFailed(MaxAdapterError.INVALID_LOAD_STATE)
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
