package com.storyteller_f.divedeep.fixture

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private var primaryTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fixture)
        if (intent.getBooleanExtra("test_scrollable", false)) {
            findViewById<View>(R.id.fixture_content).minimumHeight =
                resources.displayMetrics.heightPixels * SCROLLABLE_CONTENT_SCREENS
        }
        // A unique source prevents the real-model integration test from passing on an old cache entry.
        intent.getStringExtra("test_title")?.let { title ->
            findViewById<TextView>(R.id.title_text).text = title
        }
        val primaryButton = findViewById<Button>(R.id.primary_button)
        val english = intent.getBooleanExtra("test_english", false)
        if (english) {
            findViewById<TextView>(R.id.long_paragraph).text =
                "This paragraph checks screen text capture, translation controls, and scrolling content updates."
            primaryButton.text = "Start processing the order"
            findViewById<Button>(R.id.secondary_button).text = "View the detailed instructions"
        }
        primaryButton.setOnClickListener {
            primaryTapCount += 1
            primaryButton.text = if (english) "The button was clicked $primaryTapCount times" else "已点击 $primaryTapCount 次"
        }
    }

    private companion object {
        const val SCROLLABLE_CONTENT_SCREENS = 2
    }
}
