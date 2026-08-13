package com.deepseek.widget.data

import java.security.MessageDigest

/**
 * 密钥指纹：对原始 API Key 做 SHA-256，用于在不展示明文密钥的前提下
 * 拒绝重复添加同一把 Key。纯函数，可在 JVM 单测中直接验证。
 */
object KeyFingerprint {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
