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

    private class Harness(test: TestScope, service: TranslationService) {
        val frames = mutableListOf<TranslationFrame>()
        val failures = mutableListOf<TranslationRefreshController.Failure>()
        var clears = 0
        val controller = TranslationRefreshController(
            test.backgroundScope, StandardTestDispatcher(test.testScheduler), StandardTestDispatcher(test.testScheduler),
            service, object : OverlayRenderer {
                override fun render(frame: TranslationFrame) { frames += frame }
                override fun clear() { clears++ }
            },
        )
        init {
            test.backgroundScope.launch { controller.failures.collect { failures += it } }
        }
    }
}
