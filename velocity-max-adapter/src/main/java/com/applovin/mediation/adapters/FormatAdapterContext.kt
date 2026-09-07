package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters

/**
 * Shared dependencies that each format adapter needs from the main adapter.
 *
 * Implemented by [VelocityAdsMediationAdapter] and injected into format adapters so each
 * format's load/show logic lives in its own file without referencing the main adapter class.
 */
internal interface FormatAdapterContext {
    /**
     * `true` once [VelocityAdsMediationAdapter.onDestroy] has been called. Format adapters
     * check this before creating or manipulating ad objects so orphaned operations are
     * discarded instead of spawning ads that nothing will ever destroy.
     */
    val isDestroyed: Boolean

    /**
     * Ensures the Velocity SDK is initialized and delivers the result on the main thread.
     * See [VelocityAdsMediationAdapter.ensureInitialized] for full semantics.
     */
    fun ensureInitialized(
        parameters: MaxAdapterResponseParameters,
        onReady: (Boolean) -> Unit,
    )

    /**
     * Forwards current AppLovin privacy settings to the Velocity SDK.
     * See [VelocityAdsMediationAdapter.forwardPrivacySettings] for full semantics.
     */
    fun forwardPrivacySettings()
}
