package com.storyteller_f.divedeep

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.storyteller_f.divedeep.shared.LanguageDetector
import kotlinx.coroutines.tasks.await

/** Owns the bundled, on-device model. The accessibility service must [close] it on destruction. */
class AndroidLanguageDetector : LanguageDetector {
    private val identifier = LanguageIdentification.getClient()

    override suspend fun detectLanguage(text: String): String? =
        identifier.identifyLanguage(text).await().takeUnless { it == "und" }

    fun close() {
        identifier.close()
    }
}
