package com.deepseek.widget.data

import kotlinx.serialization.Serializable

/**
 * APIKEY.FUN 多 Key Profile（阶段 3.2）。
 *
 * 仅保存元数据；真实密钥不在此对象内，而是存放在独立的 DataStore secret 项
 * （`api_secret_apikeyfun_{id}`），由 [ApiKeyFunProfileStore] 管理。
 * 阶段 5 的 [SecureKeyStore] 将原位加密这些 secret 项，本结构无需再次迁移。
 */
@Serializable
data class ApiKeyFunProfile(
    val id: String,
    val alias: String,
    val credentialRef: String,
    val fingerprint: String,
    val isPrimaryForBalance: Boolean,
    val enabled: Boolean,
    val createdAt: Long
) {
    companion object {
        const val MAX_ALIAS_LENGTH = 30
        const val DEFAULT_ALIAS = "默认 Key"
    }
}
