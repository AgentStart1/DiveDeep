package com.storyteller_f.divedeep

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.FrameLayout
import androidx.annotation.RequiresApi

/** UI-thread window ownership. Commit applies a complete batch of button changes. */
internal interface TranslationButtonWindows {
    fun add(button: ImageButton, bounds: Rect)
    fun move(button: ImageButton, bounds: Rect)
    fun remove(button: ImageButton)
    fun commit()
    fun close()
}

internal fun translationButtonWindows(
    context: Context,
    windowManager: WindowManager,
    params: (Int, Int) -> WindowManager.LayoutParams,
): TranslationButtonWindows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    GroupedTranslationButtonWindow(context, windowManager, params)
} else {
    LegacyTranslationButtonWindows(windowManager, params)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GroupedTranslationButtonWindow(
    context: Context,
    private val windowManager: WindowManager,
    params: (Int, Int) -> WindowManager.LayoutParams,
) : TranslationButtonWindows {
    private val layer = FrameLayout(context).apply {
        // Accessibility bounds are physical screen coordinates, independent of locale direction.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }
    private val windowParams = params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
        fitInsetsTypes = 0
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    private var attached = false
    private var touchRegion = Region()

    init {
        layer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = applyTouchRegion()
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
        layer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyTouchRegion() }
    }

    override fun add(button: ImageButton, bounds: Rect) {
        layer.addView(button, childParams(bounds))
    }

    override fun move(button: ImageButton, bounds: Rect) {
        val old = button.layoutParams as FrameLayout.LayoutParams
        val currentBounds = Rect(old.leftMargin, old.topMargin, old.leftMargin + old.width, old.topMargin + old.height)
        if (currentBounds != bounds) {
            button.layoutParams = childParams(bounds)
        }
    }

    override fun remove(button: ImageButton) {
        layer.removeView(button)
    }

    override fun commit() {
        if (layer.childCount == 0) {
            close()
            return
        }
        touchRegion = Region().apply {
            for (index in 0 until layer.childCount) {
                val child = layer.getChildAt(index)
                val bounds = child.layoutParams as FrameLayout.LayoutParams
                op(
                    Rect(bounds.leftMargin, bounds.topMargin, bounds.leftMargin + bounds.width, bounds.topMargin + bounds.height),
                    Region.Op.UNION,
                )
            }
        }
        if (!attached) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.addView(layer, windowParams)
            attached = true
        }
        applyTouchRegion()
    }

    override fun close() {
        if (attached) {
            windowManager.removeView(layer)
            attached = false
        }
    }

    private fun applyTouchRegion() {
        if (!attached) return
        val surface = layer.rootSurfaceControl ?: return
        // Only buttons receive input; every gap must keep reaching the underlying application.
        surface.setTouchableRegion(touchRegion)
        if (windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager.updateViewLayout(layer, windowParams)
        }
    }

    private fun childParams(bounds: Rect) = FrameLayout.LayoutParams(bounds.width(), bounds.height()).apply {
        leftMargin = bounds.left
        topMargin = bounds.top
    }
}

private class LegacyTranslationButtonWindows(
    private val windowManager: WindowManager,
    private val params: (Int, Int) -> WindowManager.LayoutParams,
) : TranslationButtonWindows {
    override fun add(button: ImageButton, bounds: Rect) {
        windowManager.addView(button, params(bounds.width(), bounds.height()).apply {
            x = bounds.left
            y = bounds.top
        })
    }

    override fun move(button: ImageButton, bounds: Rect) {
        val layout = button.layoutParams as WindowManager.LayoutParams
        if (layout.x != bounds.left || layout.y != bounds.top) {
            layout.x = bounds.left
            layout.y = bounds.top
            windowManager.updateViewLayout(button, layout)
        }
    }

    override fun remove(button: ImageButton) = windowManager.removeView(button)
    override fun commit() = Unit
    override fun close() = Unit
}
