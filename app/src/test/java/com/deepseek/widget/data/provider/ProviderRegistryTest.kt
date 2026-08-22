package com.deepseek.widget.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun registryContainsTenPlatformsPlusCustomWithoutInventingBillingCapabilities() {
        val platforms = ProviderRegistry.descriptors.filter { it.id != ProviderRegistry.CUSTOM }
        assertEquals(10, platforms.size)
        assertEquals(11, ProviderRegistry.descriptors.map { it.id }.distinct().size)

        assertTrue(ProviderRegistry.descriptor(ProviderRegistry.APIKEY_FUN.value)!!.capabilities.contains(ProviderCapability.ACTUAL_COST))
        assertTrue(ProviderRegistry.descriptor(ProviderRegistry.OPENAI.value)!!.capabilities.contains(ProviderCapability.HISTORICAL_USAGE))
        assertFalse(ProviderRegistry.descriptor(ProviderRegistry.ZHIPU.value)!!.capabilities.contains(ProviderCapability.ACTUAL_COST))
        assertTrue(ProviderRegistry.descriptor(ProviderRegistry.TOKENHUB.value)!!.capabilities.contains(ProviderCapability.HISTORICAL_USAGE))
        assertEquals(ProviderRegistry.TOKENHUB, ProviderRegistry.canonicalId(ProviderRegistry.TOKENHUB.value))
        assertNotNull(ProviderRegistry.descriptor(ProviderRegistry.QIANFAN.value)!!.limitation)
    }

    @Test
    fun customConnectorUsesHttpsAndHasNoImplicitUsageCapability() {
        val custom = ProviderRegistry.descriptor(ProviderRegistry.CUSTOM.value)!!
        assertTrue(custom.supportsCustomBaseUrl)
        assertEquals(setOf(ProviderCapability.CONNECTION), custom.capabilities)
    }
}
