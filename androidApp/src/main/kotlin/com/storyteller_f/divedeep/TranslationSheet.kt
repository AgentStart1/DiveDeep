package com.storyteller_f.divedeep

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import java.util.Locale
import kotlin.math.roundToInt

internal object OverlayStyle {
    const val PRIMARY = 0xFF007D85.toInt()
    const val INK = 0xFF091438.toInt()
    const val MUTED = 0xFF526174.toInt()
    const val SURFACE = 0xFFFFFEFC.toInt()
    const val SOFT = 0xFFF1F5F6.toInt()
    const val TEAL_SOFT = 0xFFE4F4F4.toInt()
    const val OUTLINE = 0xFFC0DDDF.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}

internal fun Context.overlayDp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.overlayBackground(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = overlayDp(radiusDp).toFloat()
}

/** Keeps window identity, scroll position and selection stable while translation results arrive. */
internal class TranslationSheet(context: Context, onClose: () -> Unit) {
    private companion object {
        const val EDGE = 20
        const val CORNER = 28
        const val CARD_CORNER = 16
        const val CARD_PADDING = 16
        const val ACTION_HEIGHT = 48
        const val SMALL_GAP = 8
        const val CONTENT_GAP = 20
        const val TITLE_SP = 20f
        const val RESULT_SP = 18f
        const val SOURCE_SP = 14f
        const val LABEL_SP = 12f
        const val BODY_LINE_GAP = 4
        const val ELEVATION = 12
        const val CLOSE_ELEVATION = 2
        const val MAX_BODY_FRACTION = 0.48f
        const val DISABLED_ALPHA = 0.5f
    }

    private val language = label(context, "", LABEL_SP, OverlayStyle.PRIMARY)
    private val result = label(context, "", RESULT_SP, OverlayStyle.INK).apply { id = R.id.translation_result }
    private val source = label(context, "", SOURCE_SP, OverlayStyle.MUTED).apply { id = R.id.translation_source }
    private val copy = action(context, "复制译文").apply {
        id = R.id.translation_copy
        setTextColor(OverlayStyle.WHITE)
        background = context.overlayBackground(OverlayStyle.PRIMARY, CARD_CORNER)
        setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("译文", result.text))
        }
    }

    val root: View = LinearLayout(context).apply {
        id = R.id.translation_sheet
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.overlayDp(EDGE), context.overlayDp(SMALL_GAP), context.overlayDp(EDGE), context.overlayDp(EDGE))
        background = context.overlayBackground(OverlayStyle.SURFACE, CORNER).apply {
            val radius = context.overlayDp(CORNER).toFloat()
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        elevation = context.overlayDp(ELEVATION).toFloat()
        ViewCompat.setAccessibilityPaneTitle(this, "翻译详情")
        addView(header(context, onClose))
        addView(body(context))
        addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.overlayDp(ACTION_HEIGHT)).apply {
            topMargin = context.overlayDp(CONTENT_GAP)
        })
    }

    fun update(sourceText: String, targetLanguage: String, translatedText: String?) {
        updateText(source, sourceText)
        val name = Locale.forLanguageTag(targetLanguage).getDisplayName(Locale.getDefault())
        updateText(language, name.ifBlank { targetLanguage })
        updateText(result, translatedText ?: "翻译中")
        copy.isEnabled = translatedText != null
        copy.alpha = if (copy.isEnabled) 1f else DISABLED_ALPHA
    }

    private fun header(context: Context, onClose: () -> Unit): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        // The close button's shadow extends beyond this row into the surrounding padding.
        clipChildren = false
        clipToPadding = false
        addView(label(context, "翻译详情", TITLE_SP, OverlayStyle.INK).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, context.overlayDp(ACTION_HEIGHT), 1f))
        addView(action(context, "关闭").apply {
            id = R.id.translation_close
            contentDescription = "关闭翻译详情"
            setTextColor(OverlayStyle.MUTED)
            background = context.overlayBackground(OverlayStyle.SOFT, CARD_CORNER)
            stateListAnimator = null
            elevation = context.overlayDp(CLOSE_ELEVATION).toFloat()
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(context.overlayDp(ACTION_HEIGHT), context.overlayDp(ACTION_HEIGHT)))
    }

    private fun body(context: Context): View = SheetScrollView(context, MAX_BODY_FRACTION).apply {
        id = R.id.translation_scroll
        isFillViewport = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.overlayDp(CONTENT_GAP), 0, 0)
            addView(language)
            addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.overlayDp(SMALL_GAP)
                bottomMargin = context.overlayDp(CONTENT_GAP)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = context.overlayBackground(OverlayStyle.SOFT, CARD_CORNER)
                val padding = context.overlayDp(CARD_PADDING)
                setPadding(padding, padding, padding, padding)
                addView(label(context, "原文", LABEL_SP, OverlayStyle.MUTED))
                addView(source, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.overlayDp(SMALL_GAP)
                })
            })
        })
    }

    private fun label(context: Context, value: String, size: Float, color: Int) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        setLineSpacing(context.overlayDp(BODY_LINE_GAP).toFloat(), 1f)
    }

    private fun action(context: Context, value: String) = Button(context).apply {
        text = value
        textSize = SOURCE_SP
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
    }

    private fun updateText(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }
}

private class SheetScrollView(context: Context, private val heightFraction: Float) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxHeight = (resources.displayMetrics.heightPixels * heightFraction).roundToInt()
        val available = MeasureSpec.getSize(heightMeasureSpec).takeIf { MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED }
            ?: maxHeight
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minOf(maxHeight, available), MeasureSpec.AT_MOST))
    }
}
