package com.deepseek.widget.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内存版 Preferences DataStore 替身，用于 JVM 单测，绕开 DataStore 文件后端在 Windows 上
 * 的原子 rename / 文件锁竞态（多测试同 JVM 内累积时偶发 "Unable to rename .tmp"）。
 * 仍忠实实现 DataStore<Preferences> 契约（data 流 + updateData 原子更新），
 * 因此 store 的业务逻辑（指纹去重、主 Key 唯一、旧 Key 迁移）得到等价验证。
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> get() = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            next
        }
}

class ApiKeyFunProfileStoreTest {

    private fun newStore(): ApiKeyFunProfileStore {
        return ApiKeyFunProfileStore(InMemoryPreferencesDataStore())
    }

    @Test
    fun fingerprint_deterministic_and_distinct() {
        val a = KeyFingerprint.sha256("same-key")
        val b = KeyFingerprint.sha256("same-key")
        val c = KeyFingerprint.sha256("other-key")
        assertEquals(a, b)
        assertTrue(a.length == 64)
        assertTrue(a != c)
    }

    @Test
    fun addFirstKey_becomesPrimary_andSecretStored() = runTest {
        val store = newStore()
        val result = store.addKey("KEY-A", "别名一", makePrimary = false)
        assertTrue(result is ApiKeyFunProfileStore.AddKeyResult.Added)
        val profiles = store.getProfiles()
        assertEquals(1, profiles.size)
        assertTrue(profiles[0].isPrimaryForBalance)
        assertTrue(profiles[0].enabled)
        assertEquals("KEY-A", store.getSecret(profiles[0].id))
        assertEquals("KEY-A", store.getPrimarySecret())
    }

    @Test
    fun addDuplicate_fingerprintRejected_onlyOneProfile() = runTest {
        val store = newStore()
        store.addKey("KEY-A", "a", makePrimary = true)
        val dup = store.addKey("KEY-A", "b", makePrimary = true)
        assertTrue(dup is ApiKeyFunProfileStore.AddKeyResult.AlreadyExists)
        assertEquals(1, store.getProfiles().size)
    }

    @Test
    fun addSecondKey_notPrimaryByDefault() = runTest {
        val store = newStore()
        store.addKey("KEY-A", "a", makePrimary = false) // 空列表，自动成为主
        store.addKey("KEY-B", "b", makePrimary = false)
        val profiles = store.getProfiles()
        assertEquals(2, profiles.size)
        assertEquals(1, profiles.count { it.isPrimaryForBalance })
        assertTrue(profiles.first().isPrimaryForBalance)
    }

    @Test
    fun setPrimary_onlyOnePrimary() = runTest {
        val store = newStore()
        val a = (store.addKey("KEY-A", "a", makePrimary = true) as ApiKeyFunProfileStore.AddKeyResult.Added).profile
        val b = (store.addKey("KEY-B", "b", makePrimary = false) as ApiKeyFunProfileStore.AddKeyResult.Added).profile
        store.setPrimary(b.id)
        val profiles = store.getProfiles()
        assertEquals(1, profiles.count { it.isPrimaryForBalance })
        assertEquals(b.id, profiles.first { it.isPrimaryForBalance }.id)
        assertEquals(false, profiles.first { it.id == a.id }.isPrimaryForBalance)
    }

    @Test
    fun deletePrimaryWhileOthersExist_rejected() = runTest {
        val store = newStore()
        val a = (store.addKey("KEY-A", "a", makePrimary = true) as ApiKeyFunProfileStore.AddKeyResult.Added).profile
        store.addKey("KEY-B", "b", makePrimary = false)
        val result = store.deleteProfile(a.id)
        assertTrue(result is ApiKeyFunProfileStore.DeleteProfileResult.PrimaryMustBeReassigned)
        assertEquals(2, store.getProfiles().size)
    }

    @Test
    fun deleteLast_succeedsAndRemovesSecret() = runTest {
        val store = newStore()
        val a = (store.addKey("KEY-A", "a", makePrimary = true) as ApiKeyFunProfileStore.AddKeyResult.Added).profile
        val result = store.deleteProfile(a.id)
        assertTrue(result is ApiKeyFunProfileStore.DeleteProfileResult.Deleted)
        assertEquals(0, store.getProfiles().size)
        assertNull(store.getSecret(a.id))
        assertFalse(store.isAnyConfigured())
    }

    @Test
    fun migrateFromLegacy_movesOldKey() = runTest {
        val store = newStore()
        store.dataStore.edit { it[ApiKeyFunProfileStore.legacyKeyKey] = "OLD-KEY-123" }
        val migrated = store.migrateFromLegacy()
        assertTrue(migrated)
        val profiles = store.getProfiles()
        assertEquals(1, profiles.size)
        assertTrue(profiles[0].isPrimaryForBalance)
        assertEquals(ApiKeyFunProfile.DEFAULT_ALIAS, profiles[0].alias)
        assertEquals("OLD-KEY-123", store.getPrimarySecret())
        // 旧字段已删除
        val stillLegacy = store.dataStore.data.first()[ApiKeyFunProfileStore.legacyKeyKey]
        assertNull(stillLegacy)
    }

    @Test
    fun migrateFromLegacy_noopWhenProfilesExist() = runTest {
        val store = newStore()
        store.addKey("EXISTING", "k", makePrimary = true)
        store.dataStore.edit { it[ApiKeyFunProfileStore.legacyKeyKey] = "OLD-KEY-123" }
        val migrated = store.migrateFromLegacy()
        assertFalse(migrated)
        assertEquals(1, store.getProfiles().size)
    }

    @Test
    fun getPrimarySecret_nullWhenNone() = runTest {
        val store = newStore()
        assertNull(store.getPrimarySecret())
        assertFalse(store.isAnyConfigured())
    }

    @Test
    fun savePrimaryKey_newKeyWhenNoPrimary_isAddedAsPrimary() = runTest {
        val store = newStore()
        val result = store.savePrimaryKey("FRESH-KEY")
        assertTrue(result is ApiKeyFunProfileStore.SavePrimaryResult.Added)
        assertEquals("FRESH-KEY", store.getPrimarySecret())
    }

    @Test
    fun savePrimaryKey_existingFingerprint_promotedAndNotDuplicated() = runTest {
        val store = newStore()
        store.addKey("KEY-A", "a", makePrimary = true)
        store.addKey("KEY-B", "b", makePrimary = false)
        val result = store.savePrimaryKey("KEY-A")
        assertTrue(result is ApiKeyFunProfileStore.SavePrimaryResult.Promoted)
        assertEquals(2, store.getProfiles().size) // 不新增重复项
        // 重定向现有主 Key 到新密钥
        val redirect = store.savePrimaryKey("BRAND-NEW-KEY")
        assertTrue(redirect is ApiKeyFunProfileStore.SavePrimaryResult.Updated)
        assertEquals(2, store.getProfiles().size)
        assertEquals("BRAND-NEW-KEY", store.getPrimarySecret())
    }
}
