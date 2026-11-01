package com.storyteller_f.divedeep

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationIconButtonTest {
    @Test
    fun translationGlyphIsActuallyDrawnInsideTheLargerTouchTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val button = TranslationIconButton(context).apply { showTranslationState(true) }
            val size = context.overlayDp(TOUCH_TARGET_DP)
            val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            button.measure(spec, spec)
            button.layout(0, 0, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                button.draw(Canvas(bitmap))
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                assertTrue("The translation glyph must be visible, not just its circular background",
                    pixels.any { it == OverlayStyle.PRIMARY })
            } finally {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val TOUCH_TARGET_DP = 48
    }
}
