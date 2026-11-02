package com.storyteller_f.divedeep.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationRefreshControllerTest {
    private val first = ScreenTextNode("first", "First", TextBounds(0, 0, 100, 40))
    private val moved = ScreenTextNode("moved", "Moved", TextBounds(0, 200, 100, 240))

    @Test
    fun onlyForeignNodesArePresentedAndTranslatedOnMixedLanguageScreen() = runTest {
        val native = first.copy(id = "native", text = "本机语言")
        val unknown = first.copy(id = "unknown", text = "12345")
        val requests = mutableListOf<TranslationRequest>()
        val harness = Harness(this, TranslationService { requests += it; translated(it) }, LanguageDetector {
            when (it) {
                native.text -> "zh-Hans"
                unknown.text -> null
                else -> "en"
            }
        })

        harness.controller.refresh(listOf(native, first, unknown), "zh-CN", 1)
        runCurrent()

        assertEquals(listOf(first), requests.flatMap { it.items })
        assertEquals(listOf(first), harness.frames.last().nodes)
        assertTrue(harness.frames.all { frame -> frame.nodes.all { it.id == first.id } })
        harness.controller.close()
    }

    @Test
    fun regionalVariantsAndUndeterminedTextDoNotTriggerTranslation() = runTest {
        val harness = Harness(this, TranslationService { error("Unexpected translation") }, LanguageDetector {
            if (it == first.text) "en-US" else "und"
        })

        harness.controller.refresh(listOf(first, moved), "en-GB", 1)
        runCurrent()

        assertTrue(harness.frames.all { it.nodes.isEmpty() && it.items.isEmpty() })
        assertTrue(harness.failures.isEmpty())
        harness.controller.close()
    }

    @Test
    fun norwegianDetectionMatchesAndroidBokmalSystemLocale() = runTest {
        val harness = Harness(this, TranslationService { error("Unexpected translation") }, LanguageDetector { "no" })

        harness.controller.refresh(listOf(first), "nb-NO", 1)
        runCurrent()

        assertTrue(harness.frames.all { it.nodes.isEmpty() && it.items.isEmpty() })
        assertTrue(harness.failures.isEmpty())
        harness.controller.close()
    }

    @Test
    fun lateLanguageDetectionCannotTranslateOrRestoreSupersededScreen() = runTest {
        val release = CompletableDeferred<String>()
        val requests = mutableListOf<TranslationRequest>()
        val harness = Harness(this, TranslationService { requests += it; translated(it) }, LanguageDetector {
            if (it == first.text) withContext(NonCancellable) { release.await() } else "fr"
        })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        assertTrue(harness.frames.last().nodes.isEmpty())

        harness.controller.refresh(listOf(moved), "en", 2)
        runCurrent()
        val published = harness.frames.size
        release.complete("fr")
        runCurrent()

        assertEquals(listOf(moved), requests.flatMap { it.items })
        assertEquals(published, harness.frames.size)
        assertEquals(listOf(moved), harness.frames.last().nodes)
        harness.controller.close()
    }

    @Test
    fun knownForeignNodeFollowsGeometryWhileDetectionIsPending() = runTest {
        val release = CompletableDeferred<String>()
        var detected = false
        val harness = Harness(this, TranslationService(::translated), LanguageDetector {
            if (detected) release.await() else "fr".also { detected = true }
        })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        val relocated = first.copy(bounds = moved.bounds)

        harness.controller.refresh(listOf(relocated), "en", 2)
        runCurrent()

        assertEquals(listOf(relocated), harness.frames.last().nodes)
        harness.controller.close()
    }

    @Test
    fun languageDetectionFailureDoesNotSendUnclassifiedTextToBackend() = runTest {
        val harness = Harness(this, TranslationService { error("Unexpected translation") }, LanguageDetector {
            error("Detection failed")
        })

        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()

        assertEquals(1L, harness.failures.single().requestId)
        assertTrue(harness.frames.all { it.nodes.isEmpty() })
        harness.controller.close()
    }

    @Test
    fun latestGeometryDoesNotWaitForUncancellableOldRequestOrAcceptItsLateResult() = runTest {
        val oldResponse = CompletableDeferred<List<TranslationItem>>()
        val harness = Harness(this, TranslationService { request ->
            if (request.items.single().id == first.id) withContext(NonCancellable) { oldResponse.await() }
            else translated(request)
        })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()

        harness.controller.refresh(listOf(moved), "en", 2)
        runCurrent()
        assertEquals(listOf(moved), harness.frames.last().nodes)
        assertEquals("Translated Moved", harness.frames.last().items.single().translatedText)
        val published = harness.frames.size

        oldResponse.complete(listOf(item(first)))
        runCurrent()
        assertTrue(harness.frames.drop(published).none { it.nodes == listOf(first) })
        harness.controller.close()
    }

    @Test
    fun stopClearsImmediatelyAndLateBackendResultCannotRestoreButtons() = runTest {
        val response = CompletableDeferred<List<TranslationItem>>()
        val harness = Harness(this, TranslationService { withContext(NonCancellable) { response.await() } })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        harness.controller.stop()
        runCurrent()
        assertEquals(1, harness.clears)
        val published = harness.frames.size

        response.complete(listOf(item(first)))
        runCurrent()
        assertEquals(published, harness.frames.size)
        harness.controller.close()
    }

    @Test
    fun failureCanBeRetriedWithoutLosingCurrentGeometry() = runTest {
        var available = false
        val harness = Harness(this, TranslationService { request ->
            check(available) { "Backend disconnected" }
            translated(request)
        })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        assertEquals(listOf(first), harness.frames.last().nodes)
        assertEquals(1L, harness.failures.single().requestId)

        available = true
        harness.controller.refresh(listOf(first), "en", 2)
        runCurrent()
        assertEquals("Translated First", harness.frames.last().items.single().translatedText)
        harness.controller.close()
    }

    @Test
    fun staleFailureDoesNotReportFailureForTheNewScreen() = runTest {
        val release = CompletableDeferred<Unit>()
        val harness = Harness(this, TranslationService { request ->
            if (request.items.single().id == first.id) {
                withContext(NonCancellable) { release.await() }
                error("Old backend request failed")
            }
            translated(request)
        })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        harness.controller.refresh(listOf(moved), "en", 2)
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertTrue(harness.failures.isEmpty())
        assertEquals(listOf(moved), harness.frames.last().nodes)
        harness.controller.close()
    }

    @Test
    fun closePreventsLateWorkFromRenderingOrRestarting() = runTest {
        val response = CompletableDeferred<List<TranslationItem>>()
        val harness = Harness(this, TranslationService { withContext(NonCancellable) { response.await() } })
        harness.controller.refresh(listOf(first), "en", 1)
        runCurrent()
        harness.controller.close()
        val published = harness.frames.size
        response.complete(listOf(item(first)))
        harness.controller.refresh(listOf(moved), "en", 2)
        runCurrent()
        assertEquals(published, harness.frames.size)
        assertEquals(1, harness.clears)
    }

    private fun translated(request: TranslationRequest) = request.items.map(::item)
    private fun item(node: ScreenTextNode) = TranslationItem(node.id, node.text, "Translated ${node.text}", node.bounds)

    private class Harness(test: TestScope, service: TranslationService, detector: LanguageDetector? = null) {
        val frames = mutableListOf<TranslationFrame>()
        val failures = mutableListOf<TranslationRefreshController.Failure>()
        var clears = 0
        val controller = TranslationRefreshController(
            test.backgroundScope, StandardTestDispatcher(test.testScheduler), StandardTestDispatcher(test.testScheduler),
            service, object : OverlayRenderer {
                override fun render(frame: TranslationFrame) { frames += frame }
                override fun clear() { clears++ }
            },
            detector,
            detectionDispatcher = StandardTestDispatcher(test.testScheduler),
        )
        init {
            test.backgroundScope.launch { controller.failures.collect { failures += it } }
        }
    }
}
