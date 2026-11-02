package com.storyteller_f.divedeep.shared

/** Returns a BCP-47 language tag, or null when the text cannot be reliably identified. */
fun interface LanguageDetector {
    suspend fun detectLanguage(text: String): String?
}

internal suspend fun List<ScreenTextNode>.foreignLanguageNodes(
    detector: LanguageDetector,
    targetLanguage: String,
): List<ScreenTextNode> {
    val target = targetLanguage.primaryLanguage()
    return filter { node ->
        val language = detector.detectLanguage(node.text)?.primaryLanguage()
        language != null && language.isNotBlank() && language != "und" && language != target
    }
}

private fun String.primaryLanguage(): String =
    when (val language = substringBefore('-').substringBefore('_').lowercase()) {
        // ML Kit reports Norwegian as "no", while Android's Bokmål locale is "nb-NO".
        "nb" -> "no"
        else -> language
    }
