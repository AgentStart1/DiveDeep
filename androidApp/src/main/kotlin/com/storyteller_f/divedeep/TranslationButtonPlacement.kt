package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.TextBounds

/** Keep complete touch targets disjoint; dense rows use adjacent columns. Coordinates are pixels. */
internal fun placeTranslationButton(
    desired: TextBounds,
    viewport: TextBounds,
    occupied: List<TextBounds>,
    gap: Int,
): TextBounds? {
    val step = desired.width + gap
    val maxColumns = viewport.width / step
    val offsets = sequenceOf(0) + (1..maxColumns).asSequence().flatMap { sequenceOf(-it, it) }
    return offsets.map { column ->
        val shift = column * step
        desired.copy(left = desired.left + shift, right = desired.right + shift)
    }.firstOrNull { candidate ->
        viewport.contains(candidate) && occupied.none { it.overlaps(candidate) }
    }
}

private fun TextBounds.contains(other: TextBounds): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun TextBounds.overlaps(other: TextBounds): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top
