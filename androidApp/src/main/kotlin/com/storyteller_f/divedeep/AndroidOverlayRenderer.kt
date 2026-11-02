package com.storyteller_f.divedeep

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowInsets
import com.storyteller_f.divedeep.shared.OverlayRenderer
import com.storyteller_f.divedeep.shared.ScreenTextNode
import com.storyteller_f.divedeep.shared.TextBounds
import com.storyteller_f.divedeep.shared.TranslationFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AndroidOverlayRenderer(
    private val service: AccessibilityService,
) : OverlayRenderer {
    private companion object {
        const val BUTTON_WIDTH = 48
        const val BUTTON_HEIGHT = 48
        const val BUTTON_MARGIN = 4
        const val BUTTON_VISUAL_INSET = 12
        const val MIN_NODE_HEIGHT = 12
        const val SHEET_MAX_WIDTH = 600
    }

    private data class ButtonUiState(
        val nodeId: String,
        val nodeText: String,
        val bounds: Rect,
        val translated: Boolean,
    )

    private data class SheetUiState(
        val sourceText: String,
        val targetLanguage: String,
        val translatedText: String?,
    )

    private data class OverlayUiState(
        val buttons: List<ButtonUiState> = emptyList(),
        val sheet: SheetUiState? = null,
    )

    private val computeDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val rendererScope = CoroutineScope(SupervisorJob() + computeDispatcher)
    private val uiState = MutableStateFlow(OverlayUiState())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val buttonWindows = translationButtonWindows(service, windowManager, ::overlayParams)
    private val previousButtonPositions = mutableMapOf<String, Rect>()
    private var retainedFrame: TranslationFrame? = null
    private val buttonViews = mutableMapOf<String, TranslationIconButton>()
    private var sheetViews: TranslationSheet? = null

    private var selectedNodeId: String? = null

    private var latestFrame: TranslationFrame? = null

    init {
        rendererScope.launch(Dispatchers.Main.immediate) {
            uiState.collect(::applyUiState)
        }
    }

    override fun render(frame: TranslationFrame) {
        rendererScope.launch {
            latestFrame = frame
            uiState.value = computeUiState(frame)
        }
    }

    override fun clear() {
        rendererScope.launch {
            selectedNodeId = null
            latestFrame = null
            retainedFrame = null
            previousButtonPositions.clear()
            uiState.value = OverlayUiState()
        }
    }

    fun close() {
        rendererScope.cancel()
        removeStaleButtons(emptySet())
        buttonWindows.close()
        hideSheet()
    }

    private fun computeUiState(incoming: TranslationFrame): OverlayUiState {
        val frame = incoming.retainingResultsFrom(retainedFrame)
        retainedFrame = frame
        val translatedByNodeId = frame.items.associateBy { it.nodeId }
        val overlayBounds = overlayBounds()
        val occupied = mutableListOf<TextBounds>()
        val buttons = frame.nodes.mapNotNull { node ->
            val desired = buttonBoundsFor(node, overlayBounds) ?: return@mapNotNull null
            val buttonBounds = placeTranslationButton(
                desired.toTextBounds(), overlayBounds.toTextBounds(), occupied, service.overlayDp(BUTTON_MARGIN),
            ) ?: return@mapNotNull null
            occupied += buttonBounds
            ButtonUiState(
                nodeId = node.id,
                nodeText = node.text,
                bounds = buttonBounds.toRect(),
                translated = translatedByNodeId[node.id] != null,
            )
        }

        val selectedNode = selectedNodeId?.let { nodeId ->
            frame.nodes.firstOrNull { it.id == nodeId }
        }
        if (selectedNode == null) {
            selectedNodeId = null
            return OverlayUiState(buttons = buttons)
        }

        return OverlayUiState(
            buttons = buttons,
            sheet = SheetUiState(
                sourceText = selectedNode.text,
                targetLanguage = frame.targetLanguage,
                translatedText = translatedByNodeId[selectedNode.id]?.translatedText,
            ),
        )
    }

    private fun onButtonClicked(nodeId: String) {
        rendererScope.launch {
            selectedNodeId = nodeId
            val frame = latestFrame ?: return@launch
            uiState.value = computeUiState(frame)
        }
    }

    private fun dismissSheet() {
        rendererScope.launch {
            selectedNodeId = null
            uiState.value = latestFrame?.let(::computeUiState) ?: OverlayUiState()
        }
    }

    private fun isIdle(state: OverlayUiState): Boolean =
        state.buttons.isEmpty() && state.sheet == null && buttonViews.isEmpty() && sheetViews == null

    private fun applyUiState(state: OverlayUiState) {
        if (isIdle(state)) return

        val activeNodeIds = mutableSetOf<String>()
        state.buttons.forEach { buttonState ->
            activeNodeIds += buttonState.nodeId
            showButton(buttonState) {
                onButtonClicked(buttonState.nodeId)
            }
        }
        removeStaleButtons(activeNodeIds)
        buttonWindows.commit()

        val sheetState = state.sheet
        if (sheetState == null) {
            hideSheet()
        } else {
            showSheet(sheetState)
        }
    }

    private fun showButton(
        state: ButtonUiState,
        onClick: () -> Unit,
    ) {
        val existing = buttonViews[state.nodeId]
        if (existing != null) {
            updateButton(existing, state)
            existing.setOnClickListener { onClick() }
            buttonWindows.move(existing, state.bounds)
            return
        }

        val button = translationButton(state, onClick)
        buttonWindows.add(button, state.bounds)
        buttonViews[state.nodeId] = button
    }

    private fun removeStaleButtons(activeNodeIds: Set<String>) {
        val iterator = buttonViews.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeNodeIds) {
                runCatching { buttonWindows.remove(entry.value) }
                iterator.remove()
            }
        }
    }

    private fun showSheet(state: SheetUiState) {
        sheetViews?.let { existing ->
            existing.update(state.sourceText, state.targetLanguage, state.translatedText)
            return
        }
        val sheet = TranslationSheet(service, ::dismissSheet)
        sheet.update(state.sourceText, state.targetLanguage, state.translatedText)
        windowManager.addView(
            sheet.root,
            overlayParams(minOf(overlayBounds().width(), service.overlayDp(SHEET_MAX_WIDTH)), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN.inv()
            },
        )
        sheetViews = sheet
    }

    private fun hideSheet() {
        sheetViews?.let { runCatching { windowManager.removeView(it.root) } }
        sheetViews = null
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun translationButton(
        state: ButtonUiState,
        onClick: () -> Unit,
    ): TranslationIconButton =
        TranslationIconButton(service).apply {
            updateButton(this, state)
            setOnClickListener { onClick() }
        }

    private fun updateButton(
        button: TranslationIconButton,
        state: ButtonUiState,
    ) {
        button.showTranslationState(state.translated)
        val description = "${state.nodeText} ${if (state.translated) "已翻译" else "翻译中"}"
        if (button.contentDescription != description) button.contentDescription = description
    }

    private fun buttonBoundsFor(node: ScreenTextNode, overlayBounds: Rect): Rect? {
        val visibleBounds = node.bounds.toRect().intersectedWith(overlayBounds)
        val width = service.overlayDp(BUTTON_WIDTH)
        val height = service.overlayDp(BUTTON_HEIGHT)
        val margin = service.overlayDp(BUTTON_MARGIN)
        if (visibleBounds.width() < width || visibleBounds.height() < service.overlayDp(MIN_NODE_HEIGHT)) {
            previousButtonPositions.remove(node.id)
            return null
        }

        val desired = Rect(
            node.bounds.right - width - margin,
            node.bounds.top - service.overlayDp(BUTTON_VISUAL_INSET),
            node.bounds.right - margin,
            node.bounds.top - service.overlayDp(BUTTON_VISUAL_INSET) + height,
        )
        val positioned = if (overlayBounds.contains(desired)) {
            desired
        } else {
            previousButtonPositions[node.id]?.takeIf { overlayBounds.contains(it) }
                ?: desired.clampedTo(overlayBounds)
        }
        previousButtonPositions[node.id] = positioned
        return positioned
    }

    private fun overlayBounds(): Rect {
        val metrics = service.resources.displayMetrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val window = windowManager.currentWindowMetrics
            val insets = window.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            return Rect(insets.left, insets.top, metrics.widthPixels - insets.right, metrics.heightPixels - insets.bottom)
        }
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun TextBounds.toRect(): Rect = Rect(left, top, right, bottom)

    private fun Rect.toTextBounds(): TextBounds = TextBounds(left, top, right, bottom)

    private fun Rect.intersectedWith(other: Rect): Rect {
        val result = Rect(this)
        return if (result.intersect(other)) result else Rect()
    }

    private fun Rect.clampedTo(container: Rect): Rect {
        val left = this.left.coerceIn(container.left, container.right - width())
        val top = this.top.coerceIn(container.top, container.bottom - height())
        return Rect(left, top, left + width(), top + height())
    }
}
