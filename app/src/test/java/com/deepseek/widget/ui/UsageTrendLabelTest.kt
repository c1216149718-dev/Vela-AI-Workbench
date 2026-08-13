package com.deepseek.widget.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTrendLabelTest {
    @Test fun `fourteen day labels are evenly spaced and include endpoints`() {
        assertEquals(setOf(0, 3, 7, 10, 13), usageLabelIndices(14))
    }

    @Test fun `thirty day labels do not crowd the tail`() {
        assertEquals(setOf(0, 7, 15, 22, 29), usageLabelIndices(30))
    }

    @Test fun `short ranges keep every label`() {
        assertEquals((0..6).toSet(), usageLabelIndices(7))
    }
}
