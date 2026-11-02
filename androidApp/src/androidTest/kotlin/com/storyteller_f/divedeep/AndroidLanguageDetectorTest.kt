package com.storyteller_f.divedeep

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidLanguageDetectorTest {
    @Test
    fun bundledModelIdentifiesScreenTextLanguages() = runBlocking {
        val detector = AndroidLanguageDetector()
        try {
            withTimeout(30_000) {
                val samples = mapOf(
                    "en" to "Open the application settings to choose your preferred translation service.",
                    "zh" to "请打开应用设置，选择您需要使用的翻译服务，然后保存配置。",
                    "ja" to "アプリの設定を開いて、使用する翻訳サービスを選択してください。",
                    "no" to "Åpne innstillingene for å velge språket du ønsker å oversette teksten til.",
                )
                samples.forEach { (language, text) ->
                    assertEquals(language, detector.detectLanguage(text))
                }
            }
        } finally {
            detector.close()
        }
    }
}
