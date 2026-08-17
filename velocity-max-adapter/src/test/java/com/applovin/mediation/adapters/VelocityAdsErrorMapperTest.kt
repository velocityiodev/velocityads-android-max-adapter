package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.MaxAdapterError
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import org.junit.Test
import kotlin.test.assertSame

/**
 * Covers every constant in [VelocityAdsErrorCode] plus the unknown-code fallback.
 */
class VelocityAdsErrorMapperTest {
    private fun map(code: Int): MaxAdapterError = VelocityAdsErrorMapper.toMaxAdapterError(VelocityAdsError(code, "test message"))

    // ========== Server / network errors (1xxx) ==========

    @Test
    fun `INVALID_URL maps to BAD_REQUEST`() {
        assertSame(MaxAdapterError.BAD_REQUEST, map(VelocityAdsErrorCode.INVALID_URL))
    }

    @Test
    fun `NETWORK_ERROR maps to NO_CONNECTION`() {
        assertSame(MaxAdapterError.NO_CONNECTION, map(VelocityAdsErrorCode.NETWORK_ERROR))
    }

    @Test
    fun `JSON_PARSE_ERROR maps to BAD_REQUEST`() {
        assertSame(MaxAdapterError.BAD_REQUEST, map(VelocityAdsErrorCode.JSON_PARSE_ERROR))
    }

    @Test
    fun `INVALID_RESPONSE maps to BAD_REQUEST`() {
        assertSame(MaxAdapterError.BAD_REQUEST, map(VelocityAdsErrorCode.INVALID_RESPONSE))
    }

    @Test
    fun `EMPTY_RESPONSE_BODY maps to BAD_REQUEST`() {
        assertSame(MaxAdapterError.BAD_REQUEST, map(VelocityAdsErrorCode.EMPTY_RESPONSE_BODY))
    }

    @Test
    fun `SERVER_ERROR_FIELD maps to SERVER_ERROR`() {
        assertSame(MaxAdapterError.SERVER_ERROR, map(VelocityAdsErrorCode.SERVER_ERROR_FIELD))
    }

    @Test
    fun `HTTP_FAILURE maps to SERVER_ERROR`() {
        assertSame(MaxAdapterError.SERVER_ERROR, map(VelocityAdsErrorCode.HTTP_FAILURE))
    }

    // ========== SDK state errors (2xxx) ==========

    @Test
    fun `INVALID_APP_KEY maps to INVALID_CONFIGURATION`() {
        assertSame(MaxAdapterError.INVALID_CONFIGURATION, map(VelocityAdsErrorCode.INVALID_APP_KEY))
    }

    @Test
    fun `SDK_NOT_INITIALIZED maps to NOT_INITIALIZED`() {
        assertSame(MaxAdapterError.NOT_INITIALIZED, map(VelocityAdsErrorCode.SDK_NOT_INITIALIZED))
    }

    @Test
    fun `SDK_INITIALIZATION_IN_PROGRESS maps to NOT_INITIALIZED`() {
        assertSame(MaxAdapterError.NOT_INITIALIZED, map(VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS))
    }

    @Test
    fun `LOAD_ALREADY_IN_PROGRESS maps to INVALID_LOAD_STATE`() {
        assertSame(MaxAdapterError.INVALID_LOAD_STATE, map(VelocityAdsErrorCode.LOAD_ALREADY_IN_PROGRESS))
    }

    @Test
    fun `LOAD_SERVICE_UNAVAILABLE maps to NOT_INITIALIZED`() {
        assertSame(MaxAdapterError.NOT_INITIALIZED, map(VelocityAdsErrorCode.LOAD_SERVICE_UNAVAILABLE))
    }

    @Test
    fun `INVALID_AD_RESPONSE maps to BAD_REQUEST`() {
        assertSame(MaxAdapterError.BAD_REQUEST, map(VelocityAdsErrorCode.INVALID_AD_RESPONSE))
    }

    @Test
    fun `NO_FILL maps to NO_FILL`() {
        assertSame(MaxAdapterError.NO_FILL, map(VelocityAdsErrorCode.NO_FILL))
    }

    @Test
    fun `INTERNAL_ERROR maps to INTERNAL_ERROR`() {
        assertSame(MaxAdapterError.INTERNAL_ERROR, map(VelocityAdsErrorCode.INTERNAL_ERROR))
    }

    @Test
    fun `AD_ALREADY_LOADED maps to INVALID_LOAD_STATE`() {
        assertSame(MaxAdapterError.INVALID_LOAD_STATE, map(VelocityAdsErrorCode.AD_ALREADY_LOADED))
    }

    @Test
    fun `WATERFALL_LOAD_FAILED maps to INTERNAL_ERROR`() {
        assertSame(MaxAdapterError.INTERNAL_ERROR, map(VelocityAdsErrorCode.WATERFALL_LOAD_FAILED))
    }

    @Test
    fun `AD_DESTROYED maps to INVALID_LOAD_STATE`() {
        assertSame(MaxAdapterError.INVALID_LOAD_STATE, map(VelocityAdsErrorCode.AD_DESTROYED))
    }

    @Test
    fun `INVALID_AD_UNIT_ID maps to INVALID_CONFIGURATION`() {
        assertSame(MaxAdapterError.INVALID_CONFIGURATION, map(VelocityAdsErrorCode.INVALID_AD_UNIT_ID))
    }

    @Test
    fun `AD_SPENT maps to INVALID_LOAD_STATE`() {
        assertSame(MaxAdapterError.INVALID_LOAD_STATE, map(VelocityAdsErrorCode.AD_SPENT))
    }

    // ========== Fallback ==========

    @Test
    fun `unknown code maps to UNSPECIFIED`() {
        assertSame(MaxAdapterError.UNSPECIFIED, map(-1))
        assertSame(MaxAdapterError.UNSPECIFIED, map(9999))
    }
}
