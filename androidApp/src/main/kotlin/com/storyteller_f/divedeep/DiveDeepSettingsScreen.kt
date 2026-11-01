package com.storyteller_f.divedeep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private enum class SettingsPage { Home, Translation, Apps }

private val DiveDeepColors = lightColorScheme(
    primary = Color(0xFF007D85),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F4F4),
    onPrimaryContainer = Color(0xFF006A70),
    background = Color(0xFFFFFEFC),
    onBackground = Color(0xFF091438),
    surface = Color(0xFFFFFEFC),
    onSurface = Color(0xFF091438),
    surfaceVariant = Color(0xFFF1F5F6),
    onSurfaceVariant = Color(0xFF697386),
    outline = Color(0xFF737F8B),
    outlineVariant = Color(0xFFE5E9ED),
)

@Composable
internal fun DiveDeepSettingsScreen(
    enabled: Boolean,
    config: TranslationConfig,
    blockedPackages: Set<String>,
    appEntries: List<AppEntry>,
    llmdAuthorizationMessage: String,
    onToggle: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSaveConfig: (TranslationConfig) -> Unit,
    onAuthorizeLlmd: (LlmdTarget) -> Unit,
    onPackageBlockedChange: (String, Boolean) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    // Keep the unsaved form alive when visiting the home screen or application list.
    val draft = remember(config) { TranslationSettingsDraft(config) }
    BackHandler(enabled = page != SettingsPage.Home) { page = SettingsPage.Home }

    MaterialTheme(colorScheme = DiveDeepColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    when (page) {
                        SettingsPage.Home -> SettingsHome(
                            enabled = enabled,
                            config = config,
                            blockedCount = blockedPackages.size,
                            onToggle = onToggle,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenTranslation = { page = SettingsPage.Translation },
                            onOpenApps = { page = SettingsPage.Apps },
                        )
                        SettingsPage.Translation -> {
                            SettingsPageHeader("翻译服务") { page = SettingsPage.Home }
                            TranslationSettings(
                                draft = draft,
                                authorizationMessage = llmdAuthorizationMessage,
                                onSaveConfig = onSaveConfig,
                                onAuthorizeLlmd = onAuthorizeLlmd,
                            )
                        }
                        SettingsPage.Apps -> {
                            SettingsPageHeader("应用排除") { page = SettingsPage.Home }
                            AppExclusions(appEntries, blockedPackages, onPackageBlockedChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回首页")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

internal data class AppEntry(val label: String, val packageName: String)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DiveDeepSettingsPreview() {
    DiveDeepSettingsScreen(
        enabled = false,
        config = TranslationConfig(),
        blockedPackages = emptySet(),
        appEntries = emptyList(),
        llmdAuthorizationMessage = "",
        onToggle = {},
        onOpenAccessibilitySettings = {},
        onSaveConfig = {},
        onAuthorizeLlmd = {},
        onPackageBlockedChange = { _, _ -> },
    )
}
