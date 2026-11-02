package com.storyteller_f.divedeep

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var enabled by mutableStateOf(false)
    private var translationConfig by mutableStateOf(TranslationConfig())
    private var blockedPackages by mutableStateOf<Set<String>>(emptySet())
    private var appEntries by mutableStateOf<List<AppEntry>>(emptyList())
    private var llmdAuthorizationMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appEntries = loadInstalledApps()
        collectSettings()

        setContent {
            DiveDeepSettingsScreen(
                enabled = enabled,
                config = translationConfig,
                blockedPackages = blockedPackages,
                appEntries = appEntries,
                llmdAuthorizationMessage = llmdAuthorizationMessage,
                onToggle = {
                    lifecycleScope.launch {
                        DiveDeepState.toggle(this@MainActivity)
                        DiveDeepTileService.requestTileRefresh(this@MainActivity)
                    }
                },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onSaveConfig = { config ->
                    lifecycleScope.launch {
                        if (config.backend == TranslationBackend.LocalLlmdIpc && !isLlmdAuthorized(config)) {
                            llmdAuthorizationMessage =
                                "请先在 llmd ${config.llmdTarget.displayName} 中授权 DiveDeep"
                            openLlmdAuthorization(config.llmdTarget)
                            return@launch
                        }
                        DiveDeepState.setTranslationConfig(this@MainActivity, config)
                        llmdAuthorizationMessage = if (config.backend == TranslationBackend.LocalLlmdIpc) {
                            "已保存 llmd ${config.llmdTarget.displayName} 配置"
                        } else {
                            "已保存 API 配置"
                        }
                    }
                },
                onAuthorizeLlmd = { target ->
                    openLlmdAuthorization(target)
                },
                onPackageBlockedChange = { packageName, blocked ->
                    lifecycleScope.launch {
                        DiveDeepState.setPackageBlocked(this@MainActivity, packageName, blocked)
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        appEntries = loadInstalledApps()
        DiveDeepTileService.requestTileRefresh(this)
    }

    private fun collectSettings() {
        lifecycleScope.launch {
            DiveDeepState.initialize(this@MainActivity)
            DiveDeepState.settingsFlow(this@MainActivity).collect { settings ->
                enabled = settings.enabled
                translationConfig = settings.translationConfig
                blockedPackages = settings.blockedPackages
            }
        }
    }

    private fun openLlmdAuthorization(target: LlmdTarget) {
        val intent = Intent(LlmdIpcTranslationService.ACTION_AUTHORIZE_CALLER)
            .setPackage(target.packageName)
            .putExtra(LlmdIpcTranslationService.EXTRA_CALLER_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            llmdAuthorizationMessage = "未安装支持授权的 llmd ${target.displayName}"
        }
    }

    private suspend fun isLlmdAuthorized(config: TranslationConfig): Boolean =
        withContext(Dispatchers.IO) {
            val service = LlmdIpcTranslationService(this@MainActivity) { config }
            try {
                val response = service.health()
                val body = JSONObject(response)
                body.optJSONObject("error")?.optString("type") != "authorization_required" &&
                    body.optString("status") == "ok"
            } catch (_: TranslationException) {
                false
            } finally {
                service.close()
            }
        }

    private fun loadInstalledApps(): List<AppEntry> =
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map { info ->
                AppEntry(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.packageName,
                )
            }
            .sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.packageName })
}
