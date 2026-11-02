package com.storyteller_f.divedeep

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.storyteller_f.divedeep.shared.TranslationRefreshController
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DiveDeepAccessibilityService : AccessibilityService() {
    private companion object {
        const val TAG = "DiveDeepAccessibility"
    }

    private lateinit var refreshController: TranslationRefreshController
    private lateinit var accessibilityCaptureDriver: AndroidAccessibilityCaptureDriver
    private lateinit var translationService: ConfiguredTranslationService
    private lateinit var languageDetector: AndroidLanguageDetector
    private lateinit var overlayRenderer: AndroidOverlayRenderer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = MutableStateFlow(DiveDeepSettings())
    private val refreshGate = TranslationRefreshGate()
    private var serviceConnected = false
    private var overlayWindowIds: Set<Int> = emptySet()

    private fun contentRoot(): AccessibilityNodeInfo? {
        val visibleWindows = windows
        val activeRoot = rootInActiveWindow
        val activeWindow = visibleWindows.firstOrNull { it.id == activeRoot?.windowId }
        if (activeRoot != null && activeWindow?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return activeRoot
        }
        // Touching the sheet may make an accessibility overlay the active window.
        // Capture the focused application beneath it, never our own translated text.
        return visibleWindows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
        }?.root
    }

    override fun onCreate() {
        super.onCreate()
        accessibilityCaptureDriver = AndroidAccessibilityCaptureDriver(::contentRoot)
        overlayRenderer = AndroidOverlayRenderer(this)
        languageDetector = AndroidLanguageDetector()
        translationService = ConfiguredTranslationService(this) {
            settings.value.translationConfig
        }
        refreshController = TranslationRefreshController(
            parentScope = serviceScope,
            coordinationDispatcher = Dispatchers.Default.limitedParallelism(1),
            workerDispatcher = Dispatchers.IO.limitedParallelism(1),
            translationService = translationService,
            overlayRenderer = overlayRenderer,
            languageDetector = languageDetector,
            detectionDispatcher = Dispatchers.Default,
        )
        collectTranslationFailures()
        collectSettings()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnected = true
        if (settings.value.enabled) refresh()
        DiveDeepTileService.requestTileRefresh(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentSettings = settings.value
        if (!currentSettings.enabled) {
            stopRefreshing()
            return
        }
        if (event != null && isOverlayEvent(event)) return
        refresh()
    }

    private fun isOverlayEvent(event: AccessibilityEvent): Boolean {
        val currentIds = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .map { it.id }
            .toSet()
        // A removed overlay is absent from the current list but must not restart capture either.
        val overlayEvent = event.windowId in currentIds || event.windowId in overlayWindowIds
        overlayWindowIds = currentIds
        return overlayEvent
    }

    override fun onInterrupt() {
        stopRefreshing()
    }

    override fun onDestroy() {
        serviceConnected = false
        refreshController.close()
        serviceScope.cancel()
        overlayRenderer.close()
        translationService.close()
        languageDetector.close()
        super.onDestroy()
    }

    private fun refresh() {
        val currentSettings = settings.value
        val root = contentRoot()
        val shouldRefresh = serviceConnected &&
            currentSettings.enabled &&
            root != null && DiveDeepState.isPackageAllowed(currentSettings, root.packageName)
        if (!shouldRefresh) {
            stopRefreshing()
            return
        }
        val capture = TranslationCapture(
            windowId = checkNotNull(root).windowId,
            packageName = root.packageName?.toString().orEmpty(),
            nodes = accessibilityCaptureDriver.captureVisibleText(root),
            config = currentSettings.translationConfig,
            targetLanguage = Locale.getDefault().toLanguageTag(),
        )
        val revision = refreshGate.accept(capture) ?: return
        refreshController.refresh(capture.nodes, capture.targetLanguage, revision)
    }

    private fun collectTranslationFailures() {
        serviceScope.launch {
            refreshController.failures.collect { failure ->
                refreshGate.failed(failure.requestId)
                Log.e(TAG, "Translation refresh failed request=${failure.requestId} type=${failure.cause::class.simpleName}")
            }
        }
    }

    private fun stopRefreshing() {
        refreshGate.stop() ?: return
        refreshController.stop()
    }

    private fun collectSettings() {
        serviceScope.launch {
            DiveDeepState.initialize(this@DiveDeepAccessibilityService)
            DiveDeepState.settingsFlow(this@DiveDeepAccessibilityService).collect { currentSettings ->
                if (settings.value.translationConfig != currentSettings.translationConfig) refreshController.stop()
                settings.value = currentSettings
                if (currentSettings.enabled) {
                    refresh()
                } else {
                    stopRefreshing()
                }
                DiveDeepTileService.requestTileRefresh(this@DiveDeepAccessibilityService)
            }
        }
    }
}
