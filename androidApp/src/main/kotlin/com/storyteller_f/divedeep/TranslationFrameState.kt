package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.TranslationFrame

/** Keep valid results visible when a different node on the same page changes. */
internal fun TranslationFrame.retainingResultsFrom(previous: TranslationFrame?): TranslationFrame {
    val previousItems = previous?.takeIf { it.targetLanguage == targetLanguage }?.items.orEmpty()
    val itemsById = (previousItems + items).associateBy { it.nodeId }
    return copy(items = nodes.mapNotNull { node ->
        itemsById[node.id]?.takeIf { it.sourceText == node.text }
    })
}
