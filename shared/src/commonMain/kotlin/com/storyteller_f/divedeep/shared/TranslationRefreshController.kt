package com.storyteller_f.divedeep.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns presentation and translation independently. [coordinationDispatcher] must serialize actions
 * off the UI thread; blocking translation runs on [workerDispatcher]. Superseded work is cancelled
 * without waiting for its backend and can never publish to a newer screen. The owner must [close].
 */
class TranslationRefreshController(
    parentScope: CoroutineScope,
    coordinationDispatcher: CoroutineDispatcher,
    private val workerDispatcher: CoroutineDispatcher,
    private val translationService: TranslationService,
    private val overlayRenderer: OverlayRenderer,
) {
    data class Failure(val requestId: Long, val cause: Throwable)

    private val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + coordinationDispatcher)
    private val failureEvents = Channel<Failure>(Channel.CONFLATED)
    val failures = failureEvents.receiveAsFlow()
    private var generation = 0L
    private var translationJob: Job? = null

    fun refresh(nodes: List<ScreenTextNode>, targetLanguage: String, requestId: Long) {
        val screen = TranslationFrame(targetLanguage, emptyList(), nodes.translationNodes())
        scope.launch {
            val currentGeneration = ++generation
            translationJob?.cancel()
            // Geometry must follow capture even when an old backend call cannot finish or cancel.
            overlayRenderer.render(screen)
            translationJob = scope.launch(workerDispatcher) {
                translate(screen, requestId, currentGeneration)
            }
        }
    }

    private suspend fun translate(screen: TranslationFrame, requestId: Long, currentGeneration: Long) {
        val renderer = object : OverlayRenderer {
            override fun render(frame: TranslationFrame) {
                scope.launch {
                    if (generation == currentGeneration) overlayRenderer.render(frame)
                }
            }

            override fun clear() = Unit
        }
        runCatching {
            DiveDeepEngine(
                ContentCaptureDriver { screen.nodes }, translationService, renderer, { screen.targetLanguage },
            ).refresh()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            scope.launch {
                if (generation == currentGeneration) failureEvents.trySend(Failure(requestId, error))
            }
        }
    }

    fun stop() {
        scope.launch {
            generation++
            translationJob?.cancel()
            translationJob = null
            overlayRenderer.clear()
        }
    }

    fun close() {
        scope.cancel()
        failureEvents.close()
        overlayRenderer.clear()
    }
}
