package com.deepseek.widget.data.provider

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AliyunRpcSignerTest {
    @Test
    fun signerMatchesIndependentHmacSha1Vector() {
        val signed = AliyunRpcSigner.sign(
            accessKey = "testid",
            secretKey = "testsecret",
            action = "QueryAccountBalance",
            instant = Instant.parse("2026-08-20T00:00:00Z"),
            nonce = "nonce-1"
        )

        assertEquals("uj3fEKdv8HMntHwlXkwYxwEagNU=", signed.signature)
        assertEquals(
            "AccessKeyId=testid&Action=QueryAccountBalance&Format=JSON&SignatureMethod=HMAC-SHA1&SignatureNonce=nonce-1&SignatureVersion=1.0&Timestamp=2026-08-20T00%3A00%3A00Z&Version=2017-12-14&Signature=uj3fEKdv8HMntHwlXkwYxwEagNU%3D",
            signed.query
        )
    }
}
