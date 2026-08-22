package com.deepseek.widget.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OfficialBillParserTest {
    @Test
    fun csvPreviewMapsExactImportedCostAndUsage() {
        val csv = "date,model,currency,actual_cost,requests,total_tokens\n2026-08-18,gpt-5,USD,1.25,3,900\n"
        val preview = OfficialBillParser.preview(ProviderRegistry.OPENAI, "costs.csv", csv.toByteArray())
        assertEquals(1, preview.records.size)
        assertEquals(BigDecimal("1.25"), preview.records.single().cost)
        assertEquals(MetricProvenance.EXACT_IMPORT, preview.records.single().provenance)
        assertEquals(setOf("USD"), preview.currency)
    }

    @Test
    fun malformedBillNeverInventsZeroRecords() {
        val preview = OfficialBillParser.preview(ProviderRegistry.ZHIPU, "bill.csv", "model,cost\nglm,2\n".toByteArray())
        assertTrue(preview.records.isEmpty())
        assertTrue(preview.warnings.any { it.contains("日期") })
    }

    @Test
    fun fileHashIsStableForDeduplication() {
        assertEquals(OfficialBillParser.sha256("same".toByteArray()), OfficialBillParser.sha256("same".toByteArray()))
    }
}
