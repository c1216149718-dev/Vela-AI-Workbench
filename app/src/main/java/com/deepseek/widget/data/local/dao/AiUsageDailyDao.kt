package com.deepseek.widget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepseek.widget.data.local.entity.AiUsageDailyEntity
import com.deepseek.widget.data.local.entity.AiUsageModelPeriodEntity
import com.deepseek.widget.data.local.entity.AiUsageSyncStateEntity
import com.deepseek.widget.data.local.entity.ProviderBillImportEntity
import com.deepseek.widget.data.local.entity.ProviderUsageFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageDailyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacts(entities: List<ProviderUsageFactEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBillImport(entity: ProviderBillImportEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM provider_bill_imports WHERE providerId = :providerId AND fileHash = :fileHash)")
    suspend fun hasBillImport(providerId: String, fileHash: String): Boolean

    @Query(
        "SELECT * FROM provider_usage_facts WHERE bucketKind = 'DAY' AND bucketDate >= :startDate AND bucketDate <= :endDate " +
            "ORDER BY bucketDate ASC, providerId ASC, credentialId ASC, model ASC"
    )
    fun observeDailyFacts(startDate: String, endDate: String): Flow<List<ProviderUsageFactEntity>>

    @Query(
        "SELECT * FROM provider_usage_facts WHERE bucketKind = 'PERIOD' AND periodStart = :startDate AND periodEnd = :endDate " +
            "ORDER BY providerId ASC, credentialLabel ASC, model ASC"
    )
    fun observeModelFacts(startDate: String, endDate: String): Flow<List<ProviderUsageFactEntity>>

    @Query(
        "DELETE FROM provider_usage_facts WHERE providerId = :providerId AND credentialId = :credentialId " +
            "AND bucketKind = :bucketKind AND provenance = :provenance AND " +
            "((bucketKind = 'DAY' AND bucketDate >= :startDate AND bucketDate <= :endDate) OR " +
            "(bucketKind != 'DAY' AND periodStart = :startDate AND periodEnd = :endDate))"
    )
    suspend fun deleteFacts(
        providerId: String,
        credentialId: String,
        bucketKind: String,
        startDate: String,
        endDate: String,
        provenance: String
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiUsageDailyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AiUsageDailyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelPeriods(entities: List<AiUsageModelPeriodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(entity: AiUsageSyncStateEntity)

    @Query(
        "SELECT * FROM ai_usage_daily WHERE date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, provider ASC, credentialId ASC, model ASC"
    )
    fun observeAllRange(startDate: String, endDate: String): Flow<List<AiUsageDailyEntity>>

    @Query(
        "SELECT * FROM ai_usage_daily WHERE date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, provider ASC, credentialId ASC, model ASC"
    )
    suspend fun getAllRange(startDate: String, endDate: String): List<AiUsageDailyEntity>

    @Query(
        "SELECT * FROM ai_usage_model_period WHERE periodStart = :startDate AND periodEnd = :endDate " +
            "ORDER BY provider ASC, credentialLabel ASC, model ASC"
    )
    fun observeModelPeriod(startDate: String, endDate: String): Flow<List<AiUsageModelPeriodEntity>>

    @Query("SELECT * FROM ai_usage_sync_state ORDER BY provider ASC, credentialLabel ASC")
    fun observeSyncStates(): Flow<List<AiUsageSyncStateEntity>>

    @Query("SELECT * FROM ai_usage_sync_state WHERE provider = :provider AND credentialId = :credentialId LIMIT 1")
    suspend fun getSyncState(provider: String, credentialId: String): AiUsageSyncStateEntity?

    @Query("DELETE FROM ai_usage_sync_state WHERE provider = :provider AND credentialId = :credentialId")
    suspend fun deleteSyncState(provider: String, credentialId: String)

    @Query(
        "DELETE FROM ai_usage_daily WHERE provider = :provider AND credentialId = :credentialId " +
            "AND date >= :startDate AND date <= :endDate"
    )
    suspend fun deleteRange(provider: String, credentialId: String, startDate: String, endDate: String)

    @Query(
        "DELETE FROM ai_usage_model_period WHERE provider = :provider AND credentialId = :credentialId " +
            "AND periodStart = :startDate AND periodEnd = :endDate"
    )
    suspend fun deleteModelPeriod(provider: String, credentialId: String, startDate: String, endDate: String)

    @Query(
        "SELECT * FROM ai_usage_daily " +
            "WHERE provider = :provider AND credentialId = :credentialId " +
            "AND date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, model ASC"
    )
    fun observeRange(
        provider: String,
        credentialId: String,
        startDate: String,
        endDate: String
    ): Flow<List<AiUsageDailyEntity>>

    @Query(
        "SELECT * FROM ai_usage_daily " +
            "WHERE provider = :provider AND date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, credentialId ASC, model ASC"
    )
    fun observeProviderRange(
        provider: String,
        startDate: String,
        endDate: String
    ): Flow<List<AiUsageDailyEntity>>

    @Query("DELETE FROM ai_usage_daily WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT MAX(updatedAt) FROM ai_usage_daily WHERE provider = :provider AND credentialId = :credentialId")
    suspend fun lastUpdatedAt(provider: String, credentialId: String): Long?
}
