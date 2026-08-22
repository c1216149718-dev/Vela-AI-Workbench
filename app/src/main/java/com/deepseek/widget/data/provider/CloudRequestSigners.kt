package com.deepseek.widget.data.provider

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class TencentSignedHeaders(
    val authorization: String,
    val timestamp: Long,
    val action: String,
    val version: String,
    val region: String
)

/** Tencent Cloud TC3-HMAC-SHA256, kept dependency-free so it can be vector-tested. */
internal object TencentTc3Signer {
    fun sign(
        secretId: String,
        secretKey: String,
        service: String,
        host: String,
        action: String,
        version: String,
        region: String,
        payload: String,
        instant: Instant = Instant.now()
    ): TencentSignedHeaders {
        val algorithm = "TC3-HMAC-SHA256"
        val timestamp = instant.epochSecond
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(instant)
        val contentType = "application/json; charset=utf-8"
        val signedHeaders = "content-type;host"
        val canonicalHeaders = "content-type:$contentType\nhost:$host\n"
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n${sha256Hex(payload)}"
        val credentialScope = "$date/$service/tc3_request"
        val stringToSign = "$algorithm\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest)}"
        val secretDate = hmac(("TC3$secretKey").toByteArray(StandardCharsets.UTF_8), date)
        val secretService = hmac(secretDate, service)
        val secretSigning = hmac(secretService, "tc3_request")
        val signature = hmac(secretSigning, stringToSign).toHex()
        val authorization = "$algorithm Credential=$secretId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
        return TencentSignedHeaders(authorization, timestamp, action, version, region)
    }
}

internal data class BceSignedHeaders(
    val authorization: String,
    val timestamp: String,
    val signedHeaders: String
)

/**
 * Baidu BCE v1 request signer. Only the stable host and x-bce-date headers are signed so the
 * canonical request is identical across OkHttp versions. Query names and values use RFC 3986.
 */
internal object BceV1Signer {
    private const val EXPIRATION_SECONDS = 1_800

    fun sign(
        accessKey: String,
        secretKey: String,
        method: String,
        path: String,
        query: Map<String, String>,
        host: String,
        instant: Instant = Instant.now()
    ): BceSignedHeaders {
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(instant)
        val authPrefix = "bce-auth-v1/$accessKey/$timestamp/$EXPIRATION_SECONDS"
        val signedHeaders = "host;x-bce-date"
        val canonicalHeaders = listOf(
            "host:${rfc3986(host)}",
            "x-bce-date:${rfc3986(timestamp)}"
        ).joinToString("\n")
        val canonicalQuery = query.entries
            .sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString("&") { (key, value) -> "${rfc3986(key)}=${rfc3986(value)}" }
        val canonicalRequest = listOf(
            method.uppercase(),
            canonicalPath(path),
            canonicalQuery,
            canonicalHeaders
        ).joinToString("\n")
        val signingKey = hmac(secretKey.toByteArray(StandardCharsets.UTF_8), authPrefix).toHex()
        val signature = hmac(signingKey.toByteArray(StandardCharsets.UTF_8), canonicalRequest).toHex()
        return BceSignedHeaders(
            authorization = "$authPrefix/$signedHeaders/$signature",
            timestamp = timestamp,
            signedHeaders = signedHeaders
        )
    }

    private fun canonicalPath(path: String): String {
        val normalized = if (path.startsWith('/')) path else "/$path"
        return normalized.split('/').joinToString("/") { segment -> rfc3986(segment) }
    }
}

internal data class AliyunRpcRequest(
    val query: String,
    val signature: String
)

/** Alibaba Cloud legacy RPC signature used by BSS OpenAPI 2017-12-14. */
internal object AliyunRpcSigner {
    fun sign(
        accessKey: String,
        secretKey: String,
        action: String,
        parameters: Map<String, String> = emptyMap(),
        instant: Instant = Instant.now(),
        nonce: String = UUID.randomUUID().toString()
    ): AliyunRpcRequest {
        val common = buildMap {
            put("AccessKeyId", accessKey)
            put("Action", action)
            put("Format", "JSON")
            put("SignatureMethod", "HMAC-SHA1")
            put("SignatureNonce", nonce)
            put("SignatureVersion", "1.0")
            put(
                "Timestamp",
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(instant)
            )
            put("Version", "2017-12-14")
            putAll(parameters)
        }
        val canonicalized = common.entries.sortedBy { it.key }.joinToString("&") { (key, value) ->
            "${rfc3986(key)}=${rfc3986(value)}"
        }
        val stringToSign = "GET&%2F&${rfc3986(canonicalized)}"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec("$secretKey&".toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8)))
        return AliyunRpcRequest(
            query = "$canonicalized&Signature=${rfc3986(signature)}",
            signature = signature
        )
    }
}

internal fun rfc3986(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    .replace("+", "%20")
    .replace("*", "%2A")
    .replace("%7E", "~")

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).toHex()

internal fun hmac(key: ByteArray, value: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
}

internal fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
