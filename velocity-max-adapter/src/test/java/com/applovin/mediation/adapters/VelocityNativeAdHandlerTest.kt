package com.applovin.mediation.adapters

import android.view.View
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.nativeAds.MaxNativeAd
import io.velocityads.sdk.models.NativeAd
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityNativeAd
import io.velocityads.sdk.models.VelocityNativeAdRequest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityNativeAdHandlerTest {
    private lateinit var listener: MaxNativeAdAdapterListener

    @Before
    fun setUp() {
        listener = mock(MaxNativeAdAdapterListener::class.java)
    }

    private fun newNativeAdData(
        squareImageUrl: String? = "https://cdn.example.com/square.png",
        largeImageUrl: String? = "https://cdn.example.com/large.png",
    ): NativeAd =
        NativeAd(
            adId = "ad-1",
            title = "Test title",
            description = "Test description",
            callToAction = "Install",
            advertiserName = "Advertiser",
            sponsoredLabel = "Sponsored",
            badgeLabel = "Ad",
            advertiserIconUrl = "https://cdn.example.com/icon.png",
            largeImageUrl = largeImageUrl,
            squareImageUrl = squareImageUrl,
            clickUrl = "https://example.com/click",
            impressionUrl = "https://example.com/imp",
        )

    // ========== onAdLoaded ==========

    @Test
    fun `onAdLoaded before data is set reports INTERNAL_ERROR instead of crashing`() {
        // Given — a real, never-loaded ad: accessing `data` throws
        val handler = VelocityNativeAdHandler(listener)
        val unloadedAd = VelocityNativeAd(VelocityNativeAdRequest.Builder("test-ad-unit").build())

        // When
        handler.onAdLoaded(unloadedAd)

        // Then
        verify(listener).onNativeAdLoadFailed(MaxAdapterError.INTERNAL_ERROR)
        verify(listener, never()).onNativeAdLoaded(org.mockito.Mockito.any(), org.mockito.Mockito.any())
    }

    @Test
    fun `onAdLoaded assembles MaxNativeAd from ad data and forwards to listener`() {
        // Given
        val handler = VelocityNativeAdHandler(listener)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)
        `when`(velocityNativeAd.data).thenReturn(newNativeAdData())

        // When
        handler.onAdLoaded(velocityNativeAd)

        // Then
        val adCaptor = ArgumentCaptor.forClass(MaxNativeAd::class.java)
        verify(listener).onNativeAdLoaded(adCaptor.capture(), isNull())
        val maxNativeAd = adCaptor.value
        assertEquals("Test title", maxNativeAd.title)
        assertEquals("Test description", maxNativeAd.body)
        assertEquals("Install", maxNativeAd.callToAction)
        assertEquals("Advertiser", maxNativeAd.advertiser)
        assertEquals("https://cdn.example.com/icon.png", maxNativeAd.icon?.uri.toString())
        assertEquals("https://cdn.example.com/square.png", maxNativeAd.mainImage?.uri.toString())
    }

    @Test
    fun `onAdLoaded falls back to large image when square image is missing`() {
        // Given
        val handler = VelocityNativeAdHandler(listener)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)
        `when`(velocityNativeAd.data).thenReturn(newNativeAdData(squareImageUrl = null))

        // When
        handler.onAdLoaded(velocityNativeAd)

        // Then
        val adCaptor = ArgumentCaptor.forClass(MaxNativeAd::class.java)
        verify(listener).onNativeAdLoaded(adCaptor.capture(), isNull())
        assertEquals(
            "https://cdn.example.com/large.png",
            adCaptor.value.mainImage
                ?.uri
                .toString(),
        )
    }

    // ========== Media view loading ==========

    @Test
    fun `onAdLoaded with media loader sets loaded view as media view`() {
        // Given
        val mediaView = View(RuntimeEnvironment.getApplication())
        val requestedUrls = mutableListOf<String>()
        val loader =
            NativeMediaViewLoader { url, completion ->
                requestedUrls.add(url)
                completion(mediaView)
            }
        val handler = VelocityNativeAdHandler(listener, loader)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)
        `when`(velocityNativeAd.data).thenReturn(newNativeAdData())

        // When
        handler.onAdLoaded(velocityNativeAd)

        // Then — the main image URL was fetched and the resulting view delivered to MAX
        assertEquals(listOf("https://cdn.example.com/square.png"), requestedUrls)
        val adCaptor = ArgumentCaptor.forClass(MaxNativeAd::class.java)
        verify(listener).onNativeAdLoaded(adCaptor.capture(), isNull())
        assertEquals(mediaView, adCaptor.value.mediaView)
    }

    @Test
    fun `onAdLoaded with failing media loader still delivers ad without media view`() {
        // Given
        val loader = NativeMediaViewLoader { _, completion -> completion(null) }
        val handler = VelocityNativeAdHandler(listener, loader)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)
        `when`(velocityNativeAd.data).thenReturn(newNativeAdData())

        // When
        handler.onAdLoaded(velocityNativeAd)

        // Then
        val adCaptor = ArgumentCaptor.forClass(MaxNativeAd::class.java)
        verify(listener).onNativeAdLoaded(adCaptor.capture(), isNull())
        assertNull(adCaptor.value.mediaView)
        assertEquals("Test title", adCaptor.value.title)
    }

    @Test
    fun `onAdLoaded skips media loader when no image URLs are present`() {
        // Given
        var loaderInvoked = false
        val loader =
            NativeMediaViewLoader { _, completion ->
                loaderInvoked = true
                completion(null)
            }
        val handler = VelocityNativeAdHandler(listener, loader)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)
        `when`(velocityNativeAd.data).thenReturn(newNativeAdData(squareImageUrl = null, largeImageUrl = null))

        // When
        handler.onAdLoaded(velocityNativeAd)

        // Then — ad delivered synchronously without touching the loader
        assertEquals(false, loaderInvoked)
        val adCaptor = ArgumentCaptor.forClass(MaxNativeAd::class.java)
        verify(listener).onNativeAdLoaded(adCaptor.capture(), isNull())
        assertNull(adCaptor.value.mediaView)
    }

    // ========== Other listener forwarding ==========

    @Test
    fun `onAdFailedToLoad forwards mapped error to onNativeAdLoadFailed`() {
        // Given
        val handler = VelocityNativeAdHandler(listener)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)

        // When
        handler.onAdFailedToLoad(velocityNativeAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        // Then
        val errorCaptor = ArgumentCaptor.forClass(MaxAdapterError::class.java)
        verify(listener).onNativeAdLoadFailed(errorCaptor.capture())
        assertEquals(MaxAdapterError.NO_FILL.code, errorCaptor.value.code)
        assertEquals(VelocityAdsErrorCode.NO_FILL, errorCaptor.value.mediatedNetworkErrorCode)
        assertEquals("no fill", errorCaptor.value.mediatedNetworkErrorMessage)
    }

    @Test
    fun `onAdImpression forwards to onNativeAdDisplayed`() {
        // Given
        val handler = VelocityNativeAdHandler(listener)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)

        // When
        handler.onAdImpression(velocityNativeAd)

        // Then
        verify(listener).onNativeAdDisplayed(isNull())
    }

    @Test
    fun `onAdClicked forwards to onNativeAdClicked`() {
        // Given
        val handler = VelocityNativeAdHandler(listener)
        val velocityNativeAd = mock(VelocityNativeAd::class.java)

        // When
        handler.onAdClicked(velocityNativeAd)

        // Then
        verify(listener).onNativeAdClicked(isNull())
    }
}
