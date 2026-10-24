package com.storyteller_f.divedeep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsApiKeyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editedKeyStaysMaskedAndIsSaved() {
        var saved: TranslationConfig? = null
        showSettings(TranslationConfig(backend = TranslationBackend.OpenAiHttp)) { saved = it }

        keyField().performScrollTo().performTextReplacement("test-api-key")
        keyField().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsPasswordObfuscated, true))
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("test-api-key"),
            ))
        saveConfig()

        compose.runOnIdle { assertEquals("test-api-key", saved?.apiKey) }
    }

    @Test
    fun llmdDisablesKeyWithoutClearingSavedValue() {
        var saved: TranslationConfig? = null
        showSettings(TranslationConfig(backend = TranslationBackend.OpenAiHttp)) { saved = it }
        keyField().performScrollTo().performTextReplacement("existing-test-key")

        compose.onNode(isSelectable() and isNotSelected())
            .performScrollTo().performClick()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("API Key"))
        keyField().performScrollTo().assertIsNotEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        saveConfig()

        compose.runOnIdle {
            assertEquals(TranslationBackend.LocalLlmdIpc, saved?.backend)
            assertEquals("existing-test-key", saved?.apiKey)
        }
    }

    @Test
    fun updatedConfigReplacesKeyFieldContents() {
        var saved: TranslationConfig? = null
        val config = mutableStateOf(TranslationConfig(backend = TranslationBackend.OpenAiHttp))
        compose.setContent { Settings(config.value) { saved = it } }
        keyField().performScrollTo().performTextReplacement("unsaved")

        compose.runOnIdle { config.value = config.value.copy(apiKey = "loaded-key") }

        keyField().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText,
            AnnotatedString("loaded-key"),
        ))
        saveConfig()
        compose.runOnIdle { assertEquals("loaded-key", saved?.apiKey) }
    }

    private fun keyField() = compose.onNode(
        hasText("API Key") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
    )

    private fun saveConfig() {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("保存翻译配置"))
        compose.onNodeWithText("保存翻译配置").performClick()
    }

    private fun showSettings(config: TranslationConfig, onSave: (TranslationConfig) -> Unit) {
        compose.setContent { Settings(config, onSave) }
    }
}

@Composable
private fun Settings(config: TranslationConfig, onSave: (TranslationConfig) -> Unit) {
    DiveDeepSettingsScreen(
        enabled = false,
        config = config,
        blockedPackages = emptySet(),
        appEntries = emptyList(),
        llmdAuthorizationMessage = "",
        onToggle = {},
        onOpenAccessibilitySettings = {},
        onSaveConfig = onSave,
        onAuthorizeLlmd = {},
        onPackageBlockedChange = { _, _ -> },
    )
}
