package com.storyteller_f.divedeep.shared

import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse

class TranslationRefreshBlockingTest {
    @Test
    fun newScreenIsDetectedAndPresentedWhileOldBackendStillBlocksItsWorker() = runBlocking {
        // Real blocking I/O deliberately models a backend that ignores coroutine cancellation.
        val first = ScreenTextNode("first", "First page", TextBounds(0, 0, 100, 40))
        val next = first.copy(id = "next", text = "Next page")
        val started = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val frames = Channel<TranslationFrame>(Channel.UNLIMITED)
        PipedInputStream().use { input ->
            PipedOutputStream(input).use { output ->
                val controller = controller(this, frames, TranslationService { request ->
                    if (request.items.single().id == first.id) {
                        started.complete(Unit)
                        input.read()
                        returned.complete(Unit)
                    }
                    emptyList()
                })
                try {
                    withTimeout(5_000) {
                        controller.refresh(listOf(first), "en", 1)
                        started.await()

                        controller.refresh(listOf(next), "en", 2)
                        frames.receiveAsFlow().first { it.nodes == listOf(next) }

                        assertFalse(returned.isCompleted, "New controls must appear before the old request returns")
                    }
                } finally {
                    controller.close()
                    output.write(1)
                    if (started.isCompleted) withTimeout(5_000) { returned.await() }
                    frames.close()
                }
            }
        }
    }

    private fun controller(
        parent: CoroutineScope,
        frames: Channel<TranslationFrame>,
        service: TranslationService,
    ) = TranslationRefreshController(
        parentScope = parent,
        coordinationDispatcher = Dispatchers.Default.limitedParallelism(1),
        workerDispatcher = Dispatchers.IO.limitedParallelism(1),
        translationService = service,
        overlayRenderer = object : OverlayRenderer {
            override fun render(frame: TranslationFrame) { frames.trySend(frame) }
            override fun clear() = Unit
        },
        languageDetector = LanguageDetector { "fr" },
        detectionDispatcher = Dispatchers.Default,
    )
}
