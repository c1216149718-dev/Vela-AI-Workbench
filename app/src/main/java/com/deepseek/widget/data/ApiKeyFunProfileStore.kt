package com.deepseek.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.deepseek.widget.data.security.SecureCredentialStore
import java.io.File
import java.util.UUID

/**
 * APIKEY.FUN 多 Key Profile 存储（阶段 3.2）。
 *
 * - Profile 元数据列表以 JSON 存于单一偏好键 [profilesKey]，仅含别名/指纹等，不含明文密钥。
 * - 每把 Key 的真实密钥存放在独立偏好键 `api_secret_apikeyfun_{id}`，
 *   便于阶段 5 由 [SecureKeyStore] 原位加密，且删除 Profile 时连带清除密钥。
 * - 复用与 [AppPreferences] 同名的 DataStore（`deepseek_widget_prefs`），因此旧单 Key
 *   `apikey_fun_api_key` 与多 Key 数据处于同一文件，迁移逻辑可原子读取/删除旧值。
 *
 * 不变量（计划 §6.1）：
 * - 相同指纹的 Key 不能重复加入。
 * - 余额主 Key（isPrimaryForBalance）全局唯一。
 * - 删除余额主 Key 前必须先指定另一个主 Key；最后一个 Key 可直接删除。
 */
class ApiKeyFunProfileStore(
    internal val dataStore: DataStore<Preferences>,
    private val secureCredentials: SecureCredentialStore? = null
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val profilesSerializer = ListSerializer(ApiKeyFunProfile.serializer())

    private val profilesKey = stringPreferencesKey("apikeyfun_profiles_json")

    private fun secretKey(id: String) = stringPreferencesKey("api_secret_apikeyfun_$id")

    // region 读取

    suspend fun getProfiles(): List<ApiKeyFunProfile> =
        decodeProfiles(dataStore.data.first()[profilesKey])

    fun observeProfiles(): Flow<List<ApiKeyFunProfile>> =
        dataStore.data.map { prefs -> decodeProfiles(prefs[profilesKey]) }

    private fun decodeProfiles(raw: String?): List<ApiKeyFunProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ApiKeyFunProfile>>(raw) }
            .getOrDefault(emptyList())
    }

    suspend fun getSecret(id: String): String? =
        secureCredentials?.get(secureReference(id))?.takeIf { it.isNotBlank() }
            ?: dataStore.data.first()[secretKey(id)]?.takeIf { it.isNotBlank() }

    private fun secureReference(id: String) = "apikeyfun:$id:api_key"

    suspend fun getPrimaryProfile(): ApiKeyFunProfile? =
        getProfiles().firstOrNull { it.isPrimaryForBalance && it.enabled }

    suspend fun getPrimarySecret(): String? {
        val primary = getPrimaryProfile() ?: return null
        return getSecret(primary.id)
    }

    suspend fun getEnabledSecrets(): List<Pair<ApiKeyFunProfile, String>> =
        getProfiles().filter { it.enabled }.mapNotNull { profile ->
            getSecret(profile.id)?.let { profile to it }
        }

    suspend fun isAnyConfigured(): Boolean = getEnabledSecrets().isNotEmpty()

    // endregion

    // region 写入

    sealed interface AddKeyResult {
        data class Added(val profile: ApiKeyFunProfile) : AddKeyResult
        data class AlreadyExists(val profile: ApiKeyFunProfile) : AddKeyResult
        data object BlankKey : AddKeyResult
    }

    /**
     * 新增一把 Key。相同指纹已存在时返回 [AddKeyResult.AlreadyExists] 且不写入。
     * 当 [makePrimary] 为 true 时，清除其它 Profile 的主 Key 标记；
     * 否则若该列表尚无主 Key，则新 Key 自动成为主 Key。
     */
    suspend fun addKey(rawKey: String, alias: String?, makePrimary: Boolean): AddKeyResult {
        val key = rawKey.trim()
        if (key.isBlank()) return AddKeyResult.BlankKey
        val fp = KeyFingerprint.sha256(key)
        val current = getProfiles()
        val duplicate = current.firstOrNull { it.fingerprint == fp }
        if (duplicate != null) return AddKeyResult.AlreadyExists(duplicate)

        val id = UUID.randomUUID().toString()
        val isPrimary = if (makePrimary) true else current.none { it.isPrimaryForBalance }
        val normalizedAlias = (alias?.trim() ?: ApiKeyFunProfile.DEFAULT_ALIAS)
            .take(ApiKeyFunProfile.MAX_ALIAS_LENGTH)
            .ifBlank { ApiKeyFunProfile.DEFAULT_ALIAS }

        val updated = buildList {
            if (isPrimary) current.forEach { add(it.copy(isPrimaryForBalance = false)) }
            else addAll(current)
            add(
                ApiKeyFunProfile(
                    id = id,
                    alias = normalizedAlias,
                    credentialRef = "api_secret_apikeyfun_$id",
                    fingerprint = fp,
                    isPrimaryForBalance = isPrimary,
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        secureCredentials?.put(secureReference(id), key)
        dataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(profilesSerializer, updated)
            if (secureCredentials == null) prefs[secretKey(id)] = key
            else prefs.remove(secretKey(id))
        }
        return AddKeyResult.Added(updated.last())
    }

    sealed interface SavePrimaryResult {
        data class Added(val profile: ApiKeyFunProfile) : SavePrimaryResult
        data class Updated(val profile: ApiKeyFunProfile) : SavePrimaryResult
        data class Promoted(val profile: ApiKeyFunProfile) : SavePrimaryResult
        data object BlankKey : SavePrimaryResult
    }

    /**
     * 单输入框语义：把用户输入的 Key 落为“余额主 Key”。
     * - 若指纹已存在于其它 Profile，则提升该 Profile 为主 Key 并启用；
     * - 否则若已有主 Key，则把现有主 Key 重定向到新密钥（不新增重复项）；
     * - 否则新建一把主 Key。
     * 不变量：主 Key 全局仍唯一，且永远不会写入与现有 Profile 重复的密钥。
     */
    suspend fun savePrimaryKey(
        rawKey: String,
        aliasIfNew: String = ApiKeyFunProfile.DEFAULT_ALIAS
    ): SavePrimaryResult {
        val key = rawKey.trim()
        if (key.isBlank()) return SavePrimaryResult.BlankKey
        val fp = KeyFingerprint.sha256(key)
        val current = getProfiles()
        val match = current.firstOrNull { it.fingerprint == fp }
        if (match != null) {
            if (!match.isPrimaryForBalance) setPrimary(match.id)
            if (!match.enabled) setEnabled(match.id, true)
            if (secureCredentials != null) {
                secureCredentials.put(secureReference(match.id), key)
                dataStore.edit { it.remove(secretKey(match.id)) }
            } else dataStore.edit { it[secretKey(match.id)] = key }
            return SavePrimaryResult.Promoted(getProfiles().first { it.id == match.id })
        }
        val primary = current.firstOrNull { it.isPrimaryForBalance }
        return if (primary != null) {
            when (val r = replaceSecret(primary.id, key)) {
                is ReplaceSecretResult.Updated -> SavePrimaryResult.Updated(r.profile)
                else -> SavePrimaryResult.Updated(primary)
            }
        } else {
            when (val r = addKey(key, aliasIfNew, makePrimary = true)) {
                is AddKeyResult.Added -> SavePrimaryResult.Added(r.profile)
                is AddKeyResult.AlreadyExists -> SavePrimaryResult.Updated(r.profile)
                else -> SavePrimaryResult.BlankKey
            }
        }
    }

    sealed interface ReplaceSecretResult {
        data class Updated(val profile: ApiKeyFunProfile) : ReplaceSecretResult
        data object NotFound : ReplaceSecretResult
        data object AlreadyExists : ReplaceSecretResult
        data object BlankKey : ReplaceSecretResult
    }

    /** 替换某 Profile 的密钥（指纹刷新）。与列表中其它 Profile 指纹冲突时拒绝。 */
    suspend fun replaceSecret(id: String, rawKey: String): ReplaceSecretResult {
        val key = rawKey.trim()
        if (key.isBlank()) return ReplaceSecretResult.BlankKey
        val fp = KeyFingerprint.sha256(key)
        val current = getProfiles()
        if (current.none { it.id == id }) return ReplaceSecretResult.NotFound
        if (current.any { it.id != id && it.fingerprint == fp }) return ReplaceSecretResult.AlreadyExists
        val updated = current.map { if (it.id == id) it.copy(fingerprint = fp) else it }
        secureCredentials?.put(secureReference(id), key)
        dataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(profilesSerializer, updated)
            if (secureCredentials == null) prefs[secretKey(id)] = key
            else prefs.remove(secretKey(id))
        }
        return ReplaceSecretResult.Updated(updated.first { it.id == id })
    }

    suspend fun setPrimary(id: String) {
        val current = getProfiles()
        if (current.none { it.id == id }) return
        val updated = current.map { it.copy(isPrimaryForBalance = it.id == id) }
        dataStore.edit { prefs -> prefs[profilesKey] = json.encodeToString(profilesSerializer, updated) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = getProfiles()
        val target = current.firstOrNull { it.id == id } ?: return
        if (target.enabled == enabled) return
        val updated = current.map { if (it.id == id) it.copy(enabled = enabled) else it }
        dataStore.edit { prefs -> prefs[profilesKey] = json.encodeToString(profilesSerializer, updated) }
    }

    sealed interface DeleteProfileResult {
        data object Deleted : DeleteProfileResult
        data object PrimaryMustBeReassigned : DeleteProfileResult
        data object NotFound : DeleteProfileResult
    }

    /**
     * 删除 Profile 及其密钥。
     * 若目标是余额主 Key 且仍有其它 Profile 存在，则拒绝（需先指定另一个主 Key）。
     */
    suspend fun deleteProfile(id: String): DeleteProfileResult {
        val current = getProfiles()
        val target = current.firstOrNull { it.id == id } ?: return DeleteProfileResult.NotFound
        if (target.isPrimaryForBalance && current.size > 1) {
            return DeleteProfileResult.PrimaryMustBeReassigned
        }
        val updated = current.filterNot { it.id == id }
        dataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(profilesSerializer, updated)
            prefs.remove(secretKey(id))
        }
        secureCredentials?.remove(secureReference(id))
        return DeleteProfileResult.Deleted
    }

    // endregion

    // region 旧单 Key 迁移（计划 §6.1）

    /**
     * 将旧单 Key 字段 `apikey_fun_api_key` 迁移为默认 Profile（余额主 Key）。
     * 幂等：旧字段为空或 Profile 列表非空时直接返回 false。
     * 成功写入并回读后删除旧字段，避免明文 Key 长期残留。
     */
    suspend fun migrateFromLegacy(): Boolean {
        val prefs = dataStore.data.first()
        val legacy = prefs[legacyKeyKey].orEmpty().trim()
        val existing = decodeProfiles(prefs[profilesKey])
        if (legacy.isBlank() || existing.isNotEmpty()) return false
        val result = addKey(legacy, ApiKeyFunProfile.DEFAULT_ALIAS, makePrimary = true)
        return if (result is AddKeyResult.Added) {
            dataStore.edit { it.remove(legacyKeyKey) }
            true
        } else {
            false
        }
    }

    /** Existing profile secrets are copied, verified, then removed from DataStore. */
    suspend fun migrateSecretsToSecure(): Int {
        val store = secureCredentials ?: return 0
        var migrated = 0
        getProfiles().forEach { profile ->
            val old = dataStore.data.first()[secretKey(profile.id)].orEmpty()
            if (old.isBlank()) return@forEach
            store.put(secureReference(profile.id), old)
            if (store.get(secureReference(profile.id)) == old) {
                dataStore.edit { it.remove(secretKey(profile.id)) }
                migrated++
            }
        }
        return migrated
    }

    // endregion

    companion object {
        internal val legacyKeyKey = stringPreferencesKey("apikey_fun_api_key")

        fun create(context: Context): ApiKeyFunProfileStore =
            ApiKeyFunProfileStore(context.dataStore, SecureCredentialStore(context))

        /** 仅供 JVM 单测：基于临时文件构造独立 DataStore。 */
        fun createForTest(file: File): ApiKeyFunProfileStore =
            ApiKeyFunProfileStore(
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create { file }
            )
    }
}
