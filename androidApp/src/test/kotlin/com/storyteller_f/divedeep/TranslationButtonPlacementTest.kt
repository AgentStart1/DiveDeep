package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.TextBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationButtonPlacementTest {
    private val viewport = TextBounds(0, 24, 360, 780)
    private val desired = TextBounds(292, 100, 356, 148)

    @Test
    fun freeButtonStaysAtItsSourceAnchor() {
        assertEquals(desired, placeTranslationButton(desired, viewport, emptyList(), 4))
    }

    @Test
    fun neighboringRowsUseSeparateColumnsForTheirWholeTouchTargets() {
        val next = desired.copy(top = 128, bottom = 176)
        assertEquals(TextBounds(224, 128, 288, 176), placeTranslationButton(next, viewport, listOf(desired), 4))
    }

    @Test
    fun aFreeRightColumnCanBeUsedNearTheLeftEdge() {
        val left = desired.copy(left = 0, right = 64)
        assertEquals(TextBounds(68, 100, 132, 148), placeTranslationButton(left, viewport, listOf(left), 4))
    }

    @Test
    fun exhaustedRowDoesNotStackAnotherTouchTargetOnExistingButtons() {
        val full = listOf(TextBounds(0, 100, 360, 148))
        assertNull(placeTranslationButton(desired, viewport, full, 4))
    }

    @Test
    fun buttonsCannotEnterSystemBarsOutsideTheViewport() {
        assertNull(placeTranslationButton(desired.copy(top = 0, bottom = 48), viewport, emptyList(), 4))
    }
}
