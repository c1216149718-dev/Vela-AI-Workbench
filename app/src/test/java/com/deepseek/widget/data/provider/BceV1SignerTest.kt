package com.deepseek.widget.data.provider

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BceV1SignerTest {
    @Test
    fun signerIsStableAndUsesOnlyDeclaredHeaders() {
        val signed = BceV1Signer.sign(
            accessKey = "my_ak",
            secretKey = "my_sk",
            method = "POST",
            path = "/v2/service",
            query = mapOf("Action" to "DescribeServiceMetric"),
            host = "qianfan.baidubce.com",
            instant = Instant.parse("2014-06-13T05:57:36Z")
        )

        assertEquals("2014-06-13T05:57:36Z", signed.timestamp)
        assertEquals("host;x-bce-date", signed.signedHeaders)
        assertTrue(signed.authorization.startsWith("bce-auth-v1/my_ak/2014-06-13T05:57:36Z/1800/host;x-bce-date/"))
        assertEquals(64, signed.authorization.substringAfterLast('/').length)
    }

    @Test
    fun rfc3986UsesPercent20AndPreservesTilde() {
        assertEquals("a%20b~c%2A", rfc3986("a b~c*"))
    }
}
