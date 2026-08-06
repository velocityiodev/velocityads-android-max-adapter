package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.MaxAdapterError
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode

internal object VelocityAdsErrorMapper {
    /**
     * Maps a [VelocityAdsError] to the closest [MaxAdapterError] constant.
     */
    fun toMaxAdapterError(error: VelocityAdsError): MaxAdapterError =
        when (error.code) {
            VelocityAdsErrorCode.INVALID_URL,
            VelocityAdsErrorCode.JSON_PARSE_ERROR,
            VelocityAdsErrorCode.INVALID_RESPONSE,
            VelocityAdsErrorCode.EMPTY_RESPONSE_BODY,
            VelocityAdsErrorCode.INVALID_AD_RESPONSE,
            -> MaxAdapterError.BAD_REQUEST

            VelocityAdsErrorCode.NETWORK_ERROR -> MaxAdapterError.NO_CONNECTION

            VelocityAdsErrorCode.SERVER_ERROR_FIELD,
            VelocityAdsErrorCode.HTTP_FAILURE,
            -> MaxAdapterError.SERVER_ERROR

            VelocityAdsErrorCode.INVALID_APP_KEY,
            VelocityAdsErrorCode.INVALID_AD_UNIT_ID,
            -> MaxAdapterError.INVALID_CONFIGURATION

            VelocityAdsErrorCode.SDK_NOT_INITIALIZED -> MaxAdapterError.NOT_INITIALIZED

            VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS -> MaxAdapterError.NOT_INITIALIZED

            VelocityAdsErrorCode.LOAD_ALREADY_IN_PROGRESS,
            VelocityAdsErrorCode.AD_ALREADY_LOADED,
            VelocityAdsErrorCode.AD_SPENT,
            -> MaxAdapterError.INVALID_LOAD_STATE

            VelocityAdsErrorCode.LOAD_SERVICE_UNAVAILABLE -> MaxAdapterError.NOT_INITIALIZED

            VelocityAdsErrorCode.NO_FILL -> MaxAdapterError.NO_FILL

            // Velocity's internal waterfall exhausted all adapters or threw — not a
            // demand signal. Map to INTERNAL_ERROR so MAX doesn't penalise eCPM ranking
            // as if Velocity had no inventory. Matches the iOS adapter mapping.
            VelocityAdsErrorCode.WATERFALL_LOAD_FAILED -> MaxAdapterError.INTERNAL_ERROR

            VelocityAdsErrorCode.AD_DESTROYED -> MaxAdapterError.INVALID_LOAD_STATE

            VelocityAdsErrorCode.INTERNAL_ERROR -> MaxAdapterError.INTERNAL_ERROR

            else -> MaxAdapterError.UNSPECIFIED
        }
}
