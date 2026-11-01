package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.ScreenTextNode

internal data class TranslationCapture(
    val windowId: Int,
    val packageName: String,
    val nodes: List<ScreenTextNode>,
    val config: TranslationConfig,
    val targetLanguage: String,
)

/** Confined to the service callback thread. Events only refresh when captured input changes. */
internal class TranslationRefreshGate {
    private sealed interface State {
        data object Idle : State
        data object Failed : State
        data class Active(val capture: TranslationCapture) : State
    }

    private var state: State = State.Idle
    var revision: Long = 0
        private set

    fun accept(next: TranslationCapture): Long? {
        if ((state as? State.Active)?.capture == next) return null
        state = State.Active(next)
        return ++revision
    }

    fun stop(): Long? {
        if (state == State.Idle) return null
        state = State.Idle
        return ++revision
    }

    fun failed(failedRevision: Long) {
        if (revision == failedRevision) state = State.Failed
    }
}
