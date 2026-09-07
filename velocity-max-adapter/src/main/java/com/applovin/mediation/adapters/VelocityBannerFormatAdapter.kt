package com.applovin.mediation.adapters

import android.app.Activity
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters

/**
 * Handles the [MaxAdViewAdapter] contract for [VelocityAdsMediationAdapter].
 *
 * Delegates the banner load / destroy lifecycle to [VelocityBannerAdHandler], which owns
 * size resolution and the [io.velocityads.sdk.models.VelocityBannerAd] state.
 *
 * All methods are expected to be called on the main thread.
 */
internal class VelocityBannerFormatAdapter(
    private val ctx: FormatAdapterContext,
) {
    private var bannerAdHandler: VelocityBannerAdHandler? = null

    fun load(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) {
        val adUnitId = parameters.getThirdPartyAdPlacementId()
        if (adUnitId.isNullOrBlank()) {
            listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION)
            return
        }
        if (activity == null) {
            listener.onAdViewAdLoadFailed(MaxAdapterError.MISSING_ACTIVITY)
            return
        }

        ctx.forwardPrivacySettings()

        ctx.ensureInitialized(parameters) { initialized ->
            if (ctx.isDestroyed) return@ensureInitialized
            if (!initialized) {
                listener.onAdViewAdLoadFailed(MaxAdapterError.NOT_INITIALIZED)
                return@ensureInitialized
            }

            bannerAdHandler?.destroy()
            val handler = VelocityBannerAdHandler()
            bannerAdHandler = handler
            handler.load(parameters, adFormat, activity, listener)
        }
    }

    fun destroy() {
        bannerAdHandler?.destroy()
        bannerAdHandler = null
    }
}
