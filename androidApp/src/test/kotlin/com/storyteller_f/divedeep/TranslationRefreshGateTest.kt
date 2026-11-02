package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.ScreenTextNode
import com.storyteller_f.divedeep.shared.TextBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TranslationRefreshGateTest {
    private val capture = TranslationCapture(
        windowId = 1,
        packageName = "example.reader",
        nodes = listOf(ScreenTextNode("title", "Hello", TextBounds(0, 0, 100, 40))),
        config = TranslationConfig(),
        targetLanguage = "zh-CN",
    )

    @Test
    fun identicalCaptureDoesNotSupersedeInFlightOrCompletedTranslation() {
        val gate = TranslationRefreshGate()
        assertEquals(1L, gate.accept(capture))
        repeat(10) { assertNull(gate.accept(capture.copy(nodes = capture.nodes.toList()))) }
        assertEquals(1L, gate.revision)
    }

    @Test
    fun changedTranslationInputsEachStartANewRevision() {
        val variants = listOf(
            capture.copy(windowId = 2),
            capture.copy(packageName = "example.other"),
            capture.copy(nodes = capture.nodes.map { it.copy(text = "Updated") }),
            capture.copy(nodes = capture.nodes.map { it.copy(bounds = TextBounds(0, 20, 100, 60)) }),
            capture.copy(config = capture.config.copy(model = "different-model")),
            capture.copy(targetLanguage = "en-US"),
        )
        variants.forEach { changed ->
            val gate = TranslationRefreshGate()
            gate.accept(capture)
            assertEquals(2L, gate.accept(changed))
        }
    }

    @Test
    fun stopInvalidatesWorkAndSamePageCanResume() {
        val gate = TranslationRefreshGate()
        assertNull(gate.stop())
        gate.accept(capture)
        assertEquals(2L, gate.stop())
        assertNull(gate.stop())
        assertEquals(3L, gate.accept(capture))
    }

    @Test
    fun failedCaptureCanRetryWithoutChangingThePage() {
        val gate = TranslationRefreshGate()
        val revision = assertNotNull(gate.accept(capture))
        gate.failed(revision)
        assertEquals(2L, gate.accept(capture))
    }

    @Test
    fun staleFailureDoesNotInvalidateNewerCapture() {
        val gate = TranslationRefreshGate()
        val stale = assertNotNull(gate.accept(capture))
        val newer = capture.copy(windowId = 2)
        gate.accept(newer)
        gate.failed(stale)
        assertNull(gate.accept(newer))
    }

    @Test
    fun stopStillClearsOverlayAfterFailure() {
        val gate = TranslationRefreshGate()
        gate.failed(assertNotNull(gate.accept(capture)))
        assertEquals(2L, gate.stop())
    }
}
