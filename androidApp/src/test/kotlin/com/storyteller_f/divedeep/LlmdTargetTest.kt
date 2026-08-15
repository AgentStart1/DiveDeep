package com.storyteller_f.divedeep

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmdTargetTest {
    @Test
    fun packageNamesMatchAndroidBuildVariants() {
        assertEquals("com.storytellerf.llmd", LlmdTarget.Release.packageName)
        assertEquals("com.storytellerf.llmd.daily", LlmdTarget.Daily.packageName)
        assertEquals("com.storytellerf.llmd.debug", LlmdTarget.Debug.packageName)
    }

    @Test
    fun unknownOrMissingPreferenceUsesRelease() {
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference(null))
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference("unknown"))
    }

    @Test
    fun savedPreferenceRestoresEachTarget() {
        LlmdTarget.entries.forEach { target ->
            assertEquals(target, LlmdTarget.fromPreference(target.preferenceValue))
        }
    }
}
