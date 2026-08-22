package com.deepseek.widget.ui.components

import com.deepseek.widget.feature.entry.calculateEntryBandHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VelaEditorialContractTest {

    @Test
    fun pageTitlesHaveOneLockedRole() {
        val pageTitles = VelaTitle.entries.filter { it.role == VelaTitleRole.PAGE }.map { it.name }.toSet()
        assertEquals(setOf("TODAY", "TASKS", "INSIGHTS", "SETTINGS", "TOOLS"), pageTitles)
    }

    @Test
    fun sectionTitlesHaveOneLockedRole() {
        val sections = VelaTitle.entries.filter { it.role == VelaTitleRole.SECTION }.map { it.name }.toSet()
        assertTrue(sections.containsAll(setOf("NEXT", "DAILY_REFLECTION", "DATA_SOURCES", "WIDGET", "USAGE_DETAIL")))
        assertEquals(VelaTitle.entries.size, VelaTitle.entries.map { it.drawableRes }.distinct().size)
    }

    @Test
    fun entryOnlyStretchesTheQuietMaterialBand() {
        assertEquals(50f, calculateEntryBandHeight(1080f, 2160f), 0.01f)
        assertEquals(290f, calculateEntryBandHeight(1080f, 2400f), 0.01f)
        assertEquals(410f, calculateEntryBandHeight(1080f, 2520f), 0.01f)
        assertEquals(0f, calculateEntryBandHeight(1080f, 2000f), 0.01f)
    }
}
