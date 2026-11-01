package com.storyteller_f.divedeep

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.ImageButton

/** A 24 dp visible control centered inside its 48 dp transparent touch target. */
internal class TranslationIconButton(context: Context) : ImageButton(context) {
    private companion object {
        const val ICON_PADDING = 16
        const val BACKGROUND_INSET = 12
        const val RADIUS = 12
    }

    private var translated: Boolean? = null

    init {
        minimumWidth = 0
        minimumHeight = 0
        setImageResource(R.drawable.ic_translate)
        scaleType = ScaleType.FIT_CENTER
        stateListAnimator = null
        elevation = 0f
        backgroundTintList = null
    }

    fun showTranslationState(isTranslated: Boolean) {
        if (translated == isTranslated) return
        translated = isTranslated
        imageTintList = ColorStateList.valueOf(if (isTranslated) OverlayStyle.PRIMARY else OverlayStyle.MUTED)
        val fill = context.overlayBackground(
            if (isTranslated) OverlayStyle.TEAL_SOFT else OverlayStyle.SURFACE,
            RADIUS,
        ).apply { setStroke(context.overlayDp(1), OverlayStyle.OUTLINE) }
        val inset = context.overlayDp(BACKGROUND_INSET)
        background = RippleDrawable(
            ColorStateList.valueOf(OverlayStyle.OUTLINE),
            InsetDrawable(fill, inset),
            InsetDrawable(context.overlayBackground(OverlayStyle.WHITE, RADIUS), inset),
        ).apply { paddingMode = LayerDrawable.PADDING_MODE_STACK }
        // Background padding must not replace the image padding or accumulate across ripple layers.
        val padding = context.overlayDp(ICON_PADDING)
        setPadding(padding, padding, padding, padding)
    }
}
