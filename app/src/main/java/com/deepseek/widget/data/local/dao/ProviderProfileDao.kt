package com.deepseek.widget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepseek.widget.data.local.entity.ProviderBalanceSnapshotEntity
import com.deepseek.widget.data.local.entity.ProviderProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderProfileDao {
    @Query("SELECT * FROM provider_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE enabled = 1 ORDER BY providerId, updatedAt DESC")
    suspend fun getEnabled(): List<ProviderProfileEntity>

    @Query("SELECT COUNT(*) FROM provider_profiles WHERE providerId = :providerId")
    suspend fun countByProvider(providerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProviderProfileEntity)

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(snapshot: ProviderBalanceSnapshotEntity)

    @Query("SELECT * FROM provider_balance_snapshots ORDER BY capturedAt DESC")
    fun observeBalances(): Flow<List<ProviderBalanceSnapshotEntity>>

    @Query(
        "SELECT b.* FROM provider_balance_snapshots b WHERE b.capturedAt = (" +
            "SELECT MAX(x.capturedAt) FROM provider_balance_snapshots x " +
            "WHERE x.providerId = b.providerId AND x.credentialId = b.credentialId AND x.currency = b.currency)"
    )
    suspend fun getLatestBalances(): List<ProviderBalanceSnapshotEntity>
}
