package com.deepseek.widget.data.provider

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentTc3SignerTest {
    @Test
    fun signerUsesOfficialTc3ScopeAndStableDigest() {
        val signed = TencentTc3Signer.sign(
            secretId = "AKIDEXAMPLE",
            secretKey = "Gu5t9xGARNpq86cd98joQYCN3QNYQi__",
            service = "cvm",
            host = "cvm.tencentcloudapi.com",
            action = "DescribeInstances",
            version = "2017-03-12",
            region = "ap-guangzhou",
            payload = "{\"Limit\": 1, \"Filters\": [{\"Values\": [\"unnamed\"], \"Name\": \"instance-name\"}]}",
            instant = Instant.ofEpochSecond(1551113065)
        )
        assertEquals(1551113065, signed.timestamp)
        assertTrue(signed.authorization.startsWith("TC3-HMAC-SHA256 Credential=AKIDEXAMPLE/2019-02-25/cvm/tc3_request"))
        assertEquals(
            "3ed86088d40cf7b355a8d4da757cc172324e466878e4b37f5aa2f99407bebde4",
            signed.authorization.substringAfter("Signature=")
        )
    }
}
