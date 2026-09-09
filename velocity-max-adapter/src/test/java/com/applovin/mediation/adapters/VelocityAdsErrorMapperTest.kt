package com.applovin.mediation.adapters

import com.applovin.mediation.adapter.MaxAdapterError
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Covers every constant in [VelocityAdsErrorCode] plus the unknown-code fallback.
 * Each mapping must preserve the Velocity code and message as the third-party
 * SDK error so they stay visible in MAX logs.
 */
class VelocityAdsErrorMapperTest {
    private companion object {
        const val TEST_MESSAGE = "test message"
    }

    private fun assertMapping(
        velocityCode: Int,
        expected: MaxAdapterError,
    ) {
        val mapped = VelocityAdsErrorMapper.toMaxAdapterError(VelocityAdsError(velocityCode, TEST_MESSAGE))
        assertEquals(expected.code, mapped.code, "MAX error code must match the expected constant")
        assertEquals(expected.message, mapped.message, "MAX error message must match the expected constant")
        assertEquals(velocityCode, mapped.mediatedNetworkErrorCode, "Velocity code must be preserved as the third-party code")
        assertEquals(TEST_MESSAGE, mapped.mediatedNetworkErrorMessage, "Velocity message must be preserved as the third-party message")
    }

    // ========== Server / network errors (1xxx) ==========

    @Test
    fun `INVALID_URL maps to BAD_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.INVALID_URL, MaxAdapterError.BAD_REQUEST)
    }

    @Test
    fun `NETWORK_ERROR maps to NO_CONNECTION`() {
        assertMapping(VelocityAdsErrorCode.NETWORK_ERROR, MaxAdapterError.NO_CONNECTION)
    }

    @Test
    fun `JSON_PARSE_ERROR maps to BAD_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.JSON_PARSE_ERROR, MaxAdapterError.BAD_REQUEST)
    }

    @Test
    fun `INVALID_RESPONSE maps to BAD_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.INVALID_RESPONSE, MaxAdapterError.BAD_REQUEST)
    }

    @Test
    fun `EMPTY_RESPONSE_BODY maps to BAD_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.EMPTY_RESPONSE_BODY, MaxAdapterError.BAD_REQUEST)
    }

    @Test
    fun `SERVER_ERROR_FIELD maps to SERVER_ERROR`() {
        assertMapping(VelocityAdsErrorCode.SERVER_ERROR_FIELD, MaxAdapterError.SERVER_ERROR)
    }

    @Test
    fun `HTTP_FAILURE maps to SERVER_ERROR`() {
        assertMapping(VelocityAdsErrorCode.HTTP_FAILURE, MaxAdapterError.SERVER_ERROR)
    }

    // ========== SDK state errors (2xxx) ==========

    @Test
    fun `INVALID_APP_KEY maps to INVALID_CONFIGURATION`() {
        assertMapping(VelocityAdsErrorCode.INVALID_APP_KEY, MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `SDK_NOT_INITIALIZED maps to NOT_INITIALIZED`() {
        assertMapping(VelocityAdsErrorCode.SDK_NOT_INITIALIZED, MaxAdapterError.NOT_INITIALIZED)
    }

    @Test
    fun `SDK_INITIALIZATION_IN_PROGRESS maps to NOT_INITIALIZED`() {
        assertMapping(VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS, MaxAdapterError.NOT_INITIALIZED)
    }

    @Test
    fun `LOAD_ALREADY_IN_PROGRESS maps to INVALID_LOAD_STATE`() {
        assertMapping(VelocityAdsErrorCode.LOAD_ALREADY_IN_PROGRESS, MaxAdapterError.INVALID_LOAD_STATE)
    }

    @Test
    fun `LOAD_SERVICE_UNAVAILABLE maps to NOT_INITIALIZED`() {
        assertMapping(VelocityAdsErrorCode.LOAD_SERVICE_UNAVAILABLE, MaxAdapterError.NOT_INITIALIZED)
    }

    @Test
    fun `INVALID_AD_RESPONSE maps to BAD_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.INVALID_AD_RESPONSE, MaxAdapterError.BAD_REQUEST)
    }

    @Test
    fun `NO_FILL maps to NO_FILL`() {
        assertMapping(VelocityAdsErrorCode.NO_FILL, MaxAdapterError.NO_FILL)
    }

    @Test
    fun `MEDIA_UNREACHABLE maps to NO_FILL so the waterfall moves on`() {
        assertEquals(2013, VelocityAdsErrorMapper.MEDIA_UNREACHABLE)
        assertMapping(VelocityAdsErrorMapper.MEDIA_UNREACHABLE, MaxAdapterError.NO_FILL)
    }

    @Test
    fun `INTERNAL_ERROR maps to INTERNAL_ERROR`() {
        assertMapping(VelocityAdsErrorCode.INTERNAL_ERROR, MaxAdapterError.INTERNAL_ERROR)
    }

    @Test
    fun `AD_ALREADY_LOADED maps to INVALID_LOAD_STATE`() {
        assertMapping(VelocityAdsErrorCode.AD_ALREADY_LOADED, MaxAdapterError.INVALID_LOAD_STATE)
    }

    @Test
    fun `WATERFALL_LOAD_FAILED maps to INTERNAL_ERROR`() {
        assertMapping(VelocityAdsErrorCode.WATERFALL_LOAD_FAILED, MaxAdapterError.INTERNAL_ERROR)
    }

    @Test
    fun `AD_DESTROYED maps to INVALID_LOAD_STATE`() {
        assertMapping(VelocityAdsErrorCode.AD_DESTROYED, MaxAdapterError.INVALID_LOAD_STATE)
    }

    @Test
    fun `INVALID_AD_UNIT_ID maps to INVALID_CONFIGURATION`() {
        assertMapping(VelocityAdsErrorCode.INVALID_AD_UNIT_ID, MaxAdapterError.INVALID_CONFIGURATION)
    }

    @Test
    fun `AD_SPENT maps to INVALID_LOAD_STATE`() {
        assertMapping(VelocityAdsErrorCode.AD_SPENT, MaxAdapterError.INVALID_LOAD_STATE)
    }

    // ========== Fallback ==========

    @Test
    fun `unknown code maps to UNSPECIFIED`() {
        assertMapping(-1, MaxAdapterError.UNSPECIFIED)
        assertMapping(9999, MaxAdapterError.UNSPECIFIED)
    }
}
