package com.storyteller_f.divedeep

import android.os.Build
import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real cross-app and model tests; excluded unless the instrumentation argument realLlmd=true. */
@RealLlmdIntegration
class OverlayLlmdIntegrationTest {
    @get:Rule
    internal val environment = AndroidE2eEnvironment()

    @Test
    fun translatedOverlayIsVisibleOnFixture() {
        val button = environment.translatedTitle()
        assertEquals("${environment.title} 已翻译", button.contentDescription)
        assertTrue(environment.hasOverlayWindow())
    }

    @Test
    fun floatingButtonsKeepTouchTargetsSeparateOnDenseFixture() {
        environment.translatedTitle()
        val bounds = environment.device.findObjects(By.pkg(environment.appPackage).clazz("android.widget.ImageButton"))
            .map { it.visibleBounds }
        assertTrue("The fixture must expose neighboring translation buttons", bounds.size > 1)
        val overlaps = bounds.indices.any { index ->
            bounds.drop(index + 1).any { other -> Rect.intersects(bounds[index], other) }
        }
        assertFalse("Floating buttons must not compete for the same touch coordinates", overlaps)
    }

    @Test
    fun tappingTransparentIconPaddingOpensTranslationDetails() {
        val bounds = environment.translatedTitle().visibleBounds
        val x = bounds.left + bounds.width() / TOUCH_PADDING_DIVISOR
        assertTrue(environment.device.click(x, bounds.centerY()))
        assertEquals(environment.title, environment.requireObject(By.res(environment.appPackage, "translation_source")).text)
    }

    @Test
    fun translationResumesAfterControlCenterCloses() {
        assumeTrue("Requires Nubia's separate control center", Build.MANUFACTURER.equals("nubia", ignoreCase = true))
        environment.translatedTitle()
        environment.captureScreen("control-center", "before")

        val device = environment.device
        val rightEdge = (device.displayWidth * CONTROL_CENTER_EDGE).toInt()
        val end = device.displayHeight / 2
        device.executeShellCommand("input swipe $rightEdge 1 $rightEdge $end $SWIPE_DURATION")
        try {
            awaitControlCenter()
            environment.captureScreen("control-center", "open")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertTrue("Translation buttons must share a window", environment.overlayWindowCount() <= MAX_OVERLAY_WINDOWS)
            }
        } finally {
            environment.dismissSystemPanels()
        }
        environment.requireObject(By.res(AndroidE2eEnvironment.FIXTURE_PACKAGE, "title_text").text(environment.title))
        environment.captureScreen("control-center", "closed")

        assertEquals("${environment.title} 已翻译", environment.requireObject(
            By.pkg(environment.appPackage).desc("${environment.title} 已翻译"),
        ).contentDescription)
        environment.requireObject(By.res(AndroidE2eEnvironment.FIXTURE_PACKAGE, "primary_button")).click()
        environment.requireObject(By.pkg(environment.appPackage).desc("已点击 1 次 已翻译"), AndroidE2eEnvironment.MODEL_TIMEOUT)
        assertTrue(environment.hasOverlayWindow())
    }

    private fun awaitControlCenter() = runBlocking {
        withTimeout(AndroidE2eEnvironment.UI_TIMEOUT) {
            while (!environment.controlCenterFocused()) {
                delay(POLL_INTERVAL)
            }
        }
    }

    @Test
    fun underlyingPageRespondsWhileOverlayIsVisible() {
        environment.translatedTitle()
        repeat(2) { index ->
            environment.requireObject(By.res(AndroidE2eEnvironment.FIXTURE_PACKAGE, "primary_button")).click()
            environment.requireObject(By.res(AndroidE2eEnvironment.FIXTURE_PACKAGE, "primary_button")
                .text("已点击 ${index + 1} 次"))
            assertTrue("Overlay must remain visible during the underlying interaction", environment.hasOverlayWindow())
        }
    }

    @Test
    fun tappingTranslationOpensActualSourceAndResult() {
        environment.translatedTitle().click()
        val source = environment.requireObject(By.res(environment.appPackage, "translation_source"))
        assertEquals(environment.title, source.text)
        val result = environment.requireObject(By.res(environment.appPackage, "translation_result")).text
        assertTrue("Translation result must contain text", result.isNotBlank())
        assertFalse("A loading indicator is not a translation", result == "翻译中")
        assertFalse("Mock translation must not satisfy the real-model test", result.contains("[English]"))
    }

    @Test
    fun openSheetKeepsItsWindowAndTranslationWhilePageIsUnchanged() {
        environment.translatedTitle().click()
        val expected = environment.requireObject(By.res(environment.appPackage, "translation_result")).text
        assertTrue(expected.isNotBlank() && expected != "翻译中")
        val windowId = checkNotNull(environment.translationSheetWindowId())
        environment.captureScreen("sheet-stability", "open")
        runBlocking {
            repeat(STABILITY_SAMPLES) {
                delay(POLL_INTERVAL)
                assertEquals("Opening details must not restart translation", expected,
                    environment.requireObject(By.res(environment.appPackage, "translation_result")).text)
                assertEquals("The detail window must not be recreated", windowId, environment.translationSheetWindowId())
            }
        }
    }

    @Test
    fun closingDetailsLeavesTranslationButtonsAvailableForReopening() {
        environment.translatedTitle().click()
        environment.requireObject(By.res(environment.appPackage, "translation_close")).click()
        assertTrue(environment.device.wait(
            Until.gone(By.res(environment.appPackage, "translation_sheet")), AndroidE2eEnvironment.UI_TIMEOUT,
        ))
        environment.translatedTitle().click()
        assertEquals(environment.title, environment.requireObject(By.res(environment.appPackage, "translation_source")).text)
    }

    @Test
    fun destroyingServiceRemovesButtonsAndOpenDetails() {
        environment.translatedTitle().click()
        environment.requireObject(By.res(environment.appPackage, "translation_source"))
        assertTrue(environment.hasOverlayWindow())
        environment.disableService()
        // JUnit4's test body is a synchronous boundary; poll real lifecycle completion without Thread.sleep.
        runBlocking {
            withTimeout(AndroidE2eEnvironment.UI_TIMEOUT) {
                while (environment.hasOverlayWindow()) delay(POLL_INTERVAL)
            }
        }
        assertTrue(environment.device.wait(
            Until.gone(By.res(environment.appPackage, "translation_sheet")), AndroidE2eEnvironment.UI_TIMEOUT,
        ))
        assertFalse(environment.device.hasObject(By.pkg(environment.appPackage).desc("${environment.title} 已翻译")))
    }

    private companion object {
        const val POLL_INTERVAL = 200L
        const val CONTROL_CENTER_EDGE = 0.92
        const val SWIPE_DURATION = 500
        const val MAX_OVERLAY_WINDOWS = 2
        const val STABILITY_SAMPLES = 25
        const val TOUCH_PADDING_DIVISOR = 8
    }
}
