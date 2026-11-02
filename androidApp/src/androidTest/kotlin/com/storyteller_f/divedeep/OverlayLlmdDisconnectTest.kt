package com.storyteller_f.divedeep

import android.graphics.Rect
import androidx.test.uiautomator.By
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run separately: intentionally stops the real provider; restore its loaded model before reuse. */
@RealLlmdIntegration
class OverlayLlmdDisconnectTest {
    @get:Rule
    internal val environment = AndroidE2eEnvironment(scrollableFixture = true)

    @Test
    fun buttonsFollowScrollingWhenLlmdDiesDuringAnUncachedRequest() = runBlocking {
        environment.translatedTitle()
        val device = environment.device
        val tag = "Offline request ${UUID.randomUUID()}"
        val started = countLog("IPC request started")
        val pending = By.pkg(environment.appPackage).descStartsWith(tag)
        val input = By.res(AndroidE2eEnvironment.FIXTURE_PACKAGE, "search_input")
        environment.requireObject(input).text =
            tag + " Explain this screen text in another language with all details preserved. ".repeat(LONG_TEXT_REPEATS)
        awaitCondition("An uncached real IPC request must start before killing LLMD") {
            countLog("IPC request started") > started
        }
        val oldBounds = Rect(environment.requireObject(pending).visibleBounds)
        val sourceBounds = Rect(environment.requireObject(input).visibleBounds)
        val disconnects = countLog("IPC binding closed pending=")
        environment.captureScreen("llmd-death", "pending")

        val target = LlmdTarget.fromPreference(
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("llmdVariant") ?: "release",
        )
        device.executeShellCommand("am force-stop ${target.packageName}")
        awaitCondition("Disconnection must finish pending IPC calls immediately") {
            countLog("IPC binding closed pending=") > disconnects
        }
        environment.captureScreen("llmd-death", "provider-stopped")

        // Stay away from the system edge and below the EditText so the parent page handles the gesture.
        val x = device.displayWidth / 2
        val start = (device.displayHeight * SCROLL_START).toInt()
        val end = (device.displayHeight * SCROLL_END).toInt()
        device.executeShellCommand("input swipe $x $start $x $end $SWIPE_DURATION")
        awaitCondition("The fixture page must actually scroll") {
            device.findObject(input)?.visibleBounds?.let { it.top != sourceBounds.top } == true
        }
        awaitCondition("Translation controls must follow the scrolled source within five seconds") {
            device.findObject(pending)?.visibleBounds?.let { it.top != oldBounds.top } == true
        }
        assertTrue("Translation controls must stay responsive without the provider", environment.hasOverlayWindow())
        environment.captureScreen("llmd-death", "scrolled")
    }

    private suspend fun awaitCondition(message: String, condition: () -> Boolean) {
        val completed = withTimeoutOrNull(POSITION_TIMEOUT) {
            while (!condition()) delay(POLL_INTERVAL)
            true
        }
        assertTrue(message, completed == true)
    }

    private fun countLog(marker: String): Int = environment.device
        .executeShellCommand("logcat -d -s DiveDeepIpc:D").lineSequence().count { it.contains(marker) }

    private companion object {
        const val LONG_TEXT_REPEATS = 40
        const val POSITION_TIMEOUT = 5_000L
        const val POLL_INTERVAL = 100L
        const val SCROLL_START = 0.85
        const val SCROLL_END = 0.70
        const val SWIPE_DURATION = 500
    }
}
