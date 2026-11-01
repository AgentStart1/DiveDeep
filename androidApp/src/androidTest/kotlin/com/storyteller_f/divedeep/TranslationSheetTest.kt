package com.storyteller_f.divedeep

import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Model-free native layout regressions, separate from real LLMD IPC coverage. */
class TranslationSheetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun longContentScrollsWithoutPushingCloseAndCopyOutsideTheSheet() = instrumentation.runOnMainSync {
        val sheet = TranslationSheet(instrumentation.targetContext) {}
        sheet.update("Original paragraph. ".repeat(200), "zh-CN", "这是一段较长的译文。".repeat(200))
        measure(sheet.root)

        val scroll = sheet.root.findViewById<ScrollView>(R.id.translation_scroll)
        val close = sheet.root.findViewById<Button>(R.id.translation_close)
        val copy = sheet.root.findViewById<Button>(R.id.translation_copy)
        assertTrue("The body must scroll", scroll.getChildAt(0).height > scroll.height)
        assertTrue("Close must stay above the scrollable body", close.bottom <= scroll.top)
        assertTrue("Copy must remain inside the sheet", copy.bottom <= sheet.root.height)
        assertTrue("Details must leave part of the page visible",
            sheet.root.height < instrumentation.targetContext.resources.displayMetrics.heightPixels)
    }

    @Test
    fun unchangedResultPreservesScrollPositionAndNativeViews() = instrumentation.runOnMainSync {
        val sheet = TranslationSheet(instrumentation.targetContext) {}
        val source = "Original paragraph. ".repeat(200)
        val translated = "较长的译文。".repeat(200)
        sheet.update(source, "zh-CN", translated)
        measure(sheet.root)
        val scroll = sheet.root.findViewById<ScrollView>(R.id.translation_scroll)
        scroll.scrollTo(0, scroll.getChildAt(0).height / 2)
        val position = scroll.scrollY
        assertTrue(position > 0)

        sheet.update(source, "zh-CN", translated)
        measure(sheet.root)

        assertEquals(position, scroll.scrollY)
        assertEquals(translated, sheet.root.findViewById<TextView>(R.id.translation_result).text.toString())
    }

    @Test
    fun copyIsEnabledOnlyWhenTranslationArrives() = instrumentation.runOnMainSync {
        val sheet = TranslationSheet(instrumentation.targetContext) {}
        sheet.update("Hello", "zh-CN", null)
        val copy = sheet.root.findViewById<Button>(R.id.translation_copy)
        assertFalse(copy.isEnabled)

        sheet.update("Hello", "zh-CN", "你好")

        assertTrue(copy.isEnabled)
        assertEquals("你好", sheet.root.findViewById<TextView>(R.id.translation_result).text.toString())
    }

    @Test
    fun closeInvokesDismissalAction() = instrumentation.runOnMainSync {
        var dismissed = false
        val sheet = TranslationSheet(instrumentation.targetContext) { dismissed = true }
        sheet.root.findViewById<Button>(R.id.translation_close).performClick()
        assertTrue(dismissed)
    }

    private fun measure(view: View) {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        view.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
