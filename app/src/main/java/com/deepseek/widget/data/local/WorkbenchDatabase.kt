package com.deepseek.widget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deepseek.widget.data.local.dao.AiUsageDailyDao
import com.deepseek.widget.data.local.dao.DailyReviewDao
import com.deepseek.widget.data.local.dao.FocusSessionDao
import com.deepseek.widget.data.local.dao.ProjectDao
import com.deepseek.widget.data.local.dao.TaskDao
import com.deepseek.widget.data.local.dao.ProviderProfileDao
import com.deepseek.widget.data.local.entity.AiUsageDailyEntity
import com.deepseek.widget.data.local.entity.AiUsageModelPeriodEntity
import com.deepseek.widget.data.local.entity.AiUsageSyncStateEntity
import com.deepseek.widget.data.local.entity.DailyReviewEntity
import com.deepseek.widget.data.local.entity.FocusSessionEntity
import com.deepseek.widget.data.local.entity.ProjectEntity
import com.deepseek.widget.data.local.entity.TaskEntity
import com.deepseek.widget.data.local.entity.ProviderProfileEntity
import com.deepseek.widget.data.local.entity.ProviderBalanceSnapshotEntity
import com.deepseek.widget.data.local.entity.ProviderBillImportEntity
import com.deepseek.widget.data.local.entity.ProviderUsageFactEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        FocusSessionEntity::class,
        DailyReviewEntity::class,
        AiUsageDailyEntity::class,
        AiUsageModelPeriodEntity::class,
        AiUsageSyncStateEntity::class,
        ProviderProfileEntity::class,
        ProviderBalanceSnapshotEntity::class,
        ProviderUsageFactEntity::class,
        ProviderBillImportEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class WorkbenchDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun aiUsageDailyDao(): AiUsageDailyDao
    abstract fun providerProfileDao(): ProviderProfileDao

    companion object {
        @Volatile
        private var INSTANCE: WorkbenchDatabase? = null

        /** v1 -> v2：新增 AI 用量每日明细缓存表，不改动任何已有表。 */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_usage_daily` (" +
                        "`provider` TEXT NOT NULL, " +
                        "`credentialId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`currency` TEXT NOT NULL, " +
                        "`cost` TEXT NOT NULL, " +
                        "`requests` INTEGER, " +
                        "`inputTokens` INTEGER, " +
                        "`outputTokens` INTEGER, " +
                        "`totalTokens` INTEGER, " +
                        "`isEstimated` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`provider`, `credentialId`, `date`, `model`))"
                )
            }
        }

        /** v2 -> v3：任务增加时间区间、相对提醒与完成前状态。 */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `startAt` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `reminderOffsetMinutes` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `statusBeforeDone` TEXT")
            }
        }

        /** v3 -> v4：增加区间模型缓存和刷新状态；已有任务与用量表保持不变。 */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_usage_model_period` (" +
                        "`provider` TEXT NOT NULL, `credentialId` TEXT NOT NULL, " +
                        "`credentialLabel` TEXT NOT NULL, `periodStart` TEXT NOT NULL, " +
                        "`periodEnd` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                        "`currency` TEXT NOT NULL, `cost` TEXT NOT NULL, " +
                        "`requests` INTEGER, `inputTokens` INTEGER, `outputTokens` INTEGER, " +
                        "`totalTokens` INTEGER, `isEstimated` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`provider`, `credentialId`, `periodStart`, `periodEnd`, `model`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_usage_sync_state` (" +
                        "`provider` TEXT NOT NULL, `credentialId` TEXT NOT NULL, " +
                        "`credentialLabel` TEXT NOT NULL, `periodStart` TEXT NOT NULL, " +
                        "`periodEnd` TEXT NOT NULL, `lastSuccessAt` INTEGER, " +
                        "`lastAttemptAt` INTEGER NOT NULL, `errorMessage` TEXT NOT NULL, " +
                        "PRIMARY KEY(`provider`, `credentialId`))"
                )
            }
        }

        /** v4 -> v5：统一数据源 Profile 与余额快照；凭据仅保存安全存储引用。 */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provider_profiles` (" +
                        "`id` TEXT NOT NULL, `providerId` TEXT NOT NULL, `alias` TEXT NOT NULL, " +
                        "`credentialRef` TEXT NOT NULL, `capabilities` TEXT NOT NULL, `configJson` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `backgroundSync` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastTestedAt` INTEGER, " +
                        "`lastError` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_profiles_providerId` ON `provider_profiles` (`providerId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provider_balance_snapshots` (" +
                        "`providerId` TEXT NOT NULL, `credentialId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, " +
                        "`currency` TEXT NOT NULL, `amount` TEXT NOT NULL, `isEstimated` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`providerId`, `credentialId`, `capturedAt`, `currency`))"
                )
            }
        }

        /** v5 -> v6：供应商无关的用量事实、账单导入去重及同步质量字段。 */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provider_usage_facts` (" +
                        "`providerId` TEXT NOT NULL, `credentialId` TEXT NOT NULL, `credentialLabel` TEXT NOT NULL, " +
                        "`bucketKind` TEXT NOT NULL, `periodStart` TEXT NOT NULL, `periodEnd` TEXT NOT NULL, " +
                        "`bucketDate` TEXT NOT NULL, `model` TEXT NOT NULL, `currency` TEXT NOT NULL, `cost` TEXT NOT NULL, " +
                        "`requests` INTEGER, `inputTokens` INTEGER, `outputTokens` INTEGER, `cachedTokens` INTEGER, `totalTokens` INTEGER, " +
                        "`provenance` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`providerId`, `credentialId`, `bucketKind`, `periodStart`, `periodEnd`, `bucketDate`, `model`, `currency`, `provenance`, `sourceId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_usage_facts_bucketKind_bucketDate` ON `provider_usage_facts` (`bucketKind`, `bucketDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_usage_facts_providerId_periodStart_periodEnd` ON `provider_usage_facts` (`providerId`, `periodStart`, `periodEnd`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_usage_facts_credentialId` ON `provider_usage_facts` (`credentialId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provider_bill_imports` (" +
                        "`id` TEXT NOT NULL, `providerId` TEXT NOT NULL, `credentialId` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `fileHash` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, " +
                        "`recordCount` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_bill_imports_providerId_fileHash` ON `provider_bill_imports` (`providerId`, `fileHash`)")
                db.execSQL("ALTER TABLE `provider_balance_snapshots` ADD COLUMN `provenance` TEXT NOT NULL DEFAULT 'EXACT_API'")
                db.execSQL("ALTER TABLE `provider_balance_snapshots` ADD COLUMN `accountFingerprint` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `provider_balance_snapshots` ADD COLUMN `isCloudAccount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ai_usage_sync_state` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'SUCCESS'")
                db.execSQL("ALTER TABLE `ai_usage_sync_state` ADD COLUMN `errorType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `ai_usage_sync_state` ADD COLUMN `lastCompletedAt` INTEGER")
                val providerSql = "CASE `provider` WHEN 'DEEPSEEK' THEN 'deepseek' WHEN 'APIKEY_FUN' THEN 'apikey_fun' WHEN 'tencent_hunyuan' THEN 'tencent_hunyuan' ELSE lower(`provider`) END"
                db.execSQL(
                    "INSERT OR IGNORE INTO `provider_usage_facts` (providerId, credentialId, credentialLabel, bucketKind, periodStart, periodEnd, bucketDate, model, currency, cost, requests, inputTokens, outputTokens, cachedTokens, totalTokens, provenance, sourceId, updatedAt) " +
                        "SELECT $providerSql, credentialId, credentialId, 'DAY', date, date, date, model, currency, cost, requests, inputTokens, outputTokens, NULL, totalTokens, " +
                        "CASE WHEN isEstimated = 1 THEN 'BALANCE_DELTA_ESTIMATE' ELSE 'EXACT_API' END, 'legacy-v5', updatedAt FROM ai_usage_daily"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `provider_usage_facts` (providerId, credentialId, credentialLabel, bucketKind, periodStart, periodEnd, bucketDate, model, currency, cost, requests, inputTokens, outputTokens, cachedTokens, totalTokens, provenance, sourceId, updatedAt) " +
                        "SELECT $providerSql, credentialId, credentialLabel, 'PERIOD', periodStart, periodEnd, '', model, currency, cost, requests, inputTokens, outputTokens, NULL, totalTokens, " +
                        "CASE WHEN isEstimated = 1 THEN 'BALANCE_DELTA_ESTIMATE' ELSE 'EXACT_API' END, 'legacy-v5', updatedAt FROM ai_usage_model_period"
                )
                db.execSQL("UPDATE `ai_usage_sync_state` SET `status` = CASE WHEN `errorMessage` = '' THEN 'SUCCESS' ELSE 'FAILURE' END, `lastCompletedAt` = `lastSuccessAt`")
            }
        }

        fun get(context: Context): WorkbenchDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): WorkbenchDatabase {
            val now = System.currentTimeMillis()
            return Room.databaseBuilder(
                context.applicationContext,
                WorkbenchDatabase::class.java,
                "workbench.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO projects (id, name, colorArgb, archived, createdAt, updatedAt) " +
                                "VALUES (1, '收件箱', ${0xFF6F6963.toInt()}, 0, $now, $now)"
                        )
                    }
                })
                .build()
        }
    }
}
