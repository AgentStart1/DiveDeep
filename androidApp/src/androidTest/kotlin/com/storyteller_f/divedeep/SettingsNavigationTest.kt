package com.storyteller_f.divedeep

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeKeepsConnectionFieldsBehindServiceEntry() {
        showSettings()

        compose.onNodeWithText("开始翻译").assertExists()
        compose.onNodeWithText("API Key").assertDoesNotExist()
        compose.onNodeWithText("翻译服务").performClick()

        compose.onNodeWithText("API Key").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("返回首页").performClick()
        compose.onNodeWithText("开始翻译").assertExists()
    }

    @Test
    fun unsavedModelSurvivesReturningHome() {
        var saved: TranslationConfig? = null
        showSettings(onSave = { saved = it })
        compose.onNodeWithText("翻译服务").performClick()
        compose.onNodeWithText("Model").performScrollTo().performTextReplacement("edited-model")

        compose.onNodeWithContentDescription("返回首页").performClick()
        compose.onNodeWithText("翻译服务").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("保存翻译配置"))
        compose.onNodeWithText("保存翻译配置").performClick()

        compose.runOnIdle { assertEquals("edited-model", saved?.model) }
    }

    @Test
    fun appExclusionRowUpdatesTheSelectedPackage() {
        val blocked = mutableStateOf(emptySet<String>())
        compose.setContent {
            DiveDeepSettingsScreen(
                enabled = false,
                config = TranslationConfig(),
                blockedPackages = blocked.value,
                appEntries = listOf(AppEntry("示例应用", "example.reader")),
                llmdAuthorizationMessage = "",
                onToggle = {},
                onOpenAccessibilitySettings = {},
                onSaveConfig = {},
                onAuthorizeLlmd = {},
                onPackageBlockedChange = { name, excluded ->
                    blocked.value = if (excluded) blocked.value + name else blocked.value - name
                },
            )
        }
        compose.onNodeWithText("应用排除").performScrollTo().performClick()

        compose.onNodeWithText("示例应用").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(setOf("example.reader"), blocked.value) }
        compose.onNodeWithText("示例应用").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(emptySet<String>(), blocked.value) }
    }

    @Test
    fun accessibilityEntryInvokesSettingsAction() {
        var opened = false
        showSettings(onAccessibility = { opened = true })

        compose.onNodeWithText("去授权").performClick()

        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun authorizeUsesTheSelectedLlmdVariant() {
        var authorized: LlmdTarget? = null
        showSettings(onAuthorize = { authorized = it })
        compose.onNodeWithText("翻译服务").performClick()
        compose.onNode(isSelectable() and hasText("本机 llmd IPC")).performScrollTo().performClick()
        compose.onNode(isSelectable() and hasText(LlmdTarget.Debug.displayName)).performScrollTo().performClick()

        compose.onNodeWithText("授权 llmd ${LlmdTarget.Debug.displayName}").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(LlmdTarget.Debug, authorized) }
    }

    private fun showSettings(
        onSave: (TranslationConfig) -> Unit = {},
        onAccessibility: () -> Unit = {},
        onAuthorize: (LlmdTarget) -> Unit = {},
    ) {
        compose.setContent {
            DiveDeepSettingsScreen(
                enabled = false,
                config = TranslationConfig(backend = TranslationBackend.OpenAiHttp),
                blockedPackages = emptySet(),
                appEntries = emptyList(),
                llmdAuthorizationMessage = "",
                onToggle = {},
                onOpenAccessibilitySettings = onAccessibility,
                onSaveConfig = onSave,
                onAuthorizeLlmd = onAuthorize,
                onPackageBlockedChange = { _, _ -> },
            )
        }
    }
}
