package com.applovin.mediation.adapters

import android.view.View
import android.widget.FrameLayout
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import io.velocityads.sdk.models.VelocityNativeAd
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityMaxNativeAdTest {
    private lateinit var velocityNativeAd: VelocityNativeAd
    private lateinit var maxNativeAd: VelocityMaxNativeAd

    @Before
    fun setUp() {
        velocityNativeAd = mock(VelocityNativeAd::class.java)
        maxNativeAd =
            VelocityMaxNativeAd(
                builder =
                    MaxNativeAd
                        .Builder()
                        .setAdFormat(MaxAdFormat.NATIVE)
                        .setTitle("title"),
                velocityNativeAd = velocityNativeAd,
            )
    }

    // ========== prepareForInteraction (manual render path) ==========

    @Test
    fun `prepareForInteraction registers clickable views with the container and returns true`() {
        // Given — a publisher-rendered container with explicit clickable views
        val context = RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)
        val clickable1 = View(context)
        val clickable2 = View(context)

        // When
        val handled = maxNativeAd.prepareForInteraction(listOf(clickable1, clickable2), container)

        // Then
        assertTrue(handled, "Manual render path must report the interaction as handled")
        verify(velocityNativeAd).registerViewForInteraction(container, listOf(clickable1, clickable2))
    }

    @Test
    fun `prepareForInteraction with no clickable views falls back to the container`() {
        // Given
        val context = RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)

        // When
        val handled = maxNativeAd.prepareForInteraction(emptyList(), container)

        // Then
        assertTrue(handled)
        verify(velocityNativeAd).registerViewForInteraction(container, listOf<View>(container))
    }

    // ========== prepareViewForInteraction (template render path) ==========

    @Test
    @Suppress("DEPRECATION") // Exercising the legacy template fallback path deliberately.
    fun `prepareViewForInteraction with no sub-views falls back to the root view`() {
        // Given — a MaxNativeAdView whose sub-view getters all return null
        val adView = mock(MaxNativeAdView::class.java)

        // When
        maxNativeAd.prepareViewForInteraction(adView)

        // Then
        verify(velocityNativeAd).registerViewForInteraction(adView, listOf<View>(adView))
    }
}
