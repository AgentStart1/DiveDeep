package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.ScreenTextNode
import com.storyteller_f.divedeep.shared.TextBounds
import com.storyteller_f.divedeep.shared.TranslationFrame
import com.storyteller_f.divedeep.shared.TranslationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationFrameStateTest {
    private val title = ScreenTextNode("title", "Hello", TextBounds(0, 0, 100, 40))
    private val translated = TranslationItem(title.id, title.text, "你好", title.bounds)
    private val previous = TranslationFrame("zh-CN", listOf(translated), listOf(title))

    @Test
    fun refreshOfUnchangedSourceDoesNotReplaceResultWithLoading() {
        val refreshing = previous.copy(items = emptyList())
        assertEquals(listOf(translated), refreshing.retainingResultsFrom(previous).items)
    }

    @Test
    fun changingAnotherNodeKeepsTheSelectedNodesResult() {
        val changedPage = previous.copy(items = emptyList(), nodes = listOf(title, title.copy(id = "counter", text = "2")))
        assertEquals(listOf(translated), changedPage.retainingResultsFrom(previous).items)
    }

    @Test
    fun newResultReplacesPreviousResult() {
        val replacement = translated.copy(translatedText = "您好")
        val refreshed = previous.copy(items = listOf(replacement))
        assertEquals(listOf(replacement), refreshed.retainingResultsFrom(previous).items)
    }

    @Test
    fun changedSourceWithReusedIdDoesNotRetainOldTranslation() {
        val changed = previous.copy(items = emptyList(), nodes = listOf(title.copy(text = "Goodbye")))
        assertTrue(changed.retainingResultsFrom(previous).items.isEmpty())
    }

    @Test
    fun differentTargetLanguageDoesNotRetainPreviousTranslation() {
        val changed = previous.copy(targetLanguage = "ja-JP", items = emptyList())
        assertTrue(changed.retainingResultsFrom(previous).items.isEmpty())
    }

    @Test
    fun removedNodesAreDiscardedAndCannotReappearOnLaterFrames() {
        val removed = previous.copy(items = emptyList(), nodes = emptyList()).retainingResultsFrom(previous)
        assertTrue(removed.items.isEmpty())
        assertTrue(previous.copy(items = emptyList()).retainingResultsFrom(removed).items.isEmpty())
    }
}
