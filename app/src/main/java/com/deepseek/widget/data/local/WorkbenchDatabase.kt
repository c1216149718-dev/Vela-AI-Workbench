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
import com.deepseek.widget.data.local.entity.AiUsageDailyEntity
import com.deepseek.widget.data.local.entity.AiUsageModelPeriodEntity
import com.deepseek.widget.data.local.entity.AiUsageSyncStateEntity
import com.deepseek.widget.data.local.entity.DailyReviewEntity
import com.deepseek.widget.data.local.entity.FocusSessionEntity
import com.deepseek.widget.data.local.entity.ProjectEntity
import com.deepseek.widget.data.local.entity.TaskEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        FocusSessionEntity::class,
        DailyReviewEntity::class,
        AiUsageDailyEntity::class,
        AiUsageModelPeriodEntity::class,
        AiUsageSyncStateEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class WorkbenchDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun aiUsageDailyDao(): AiUsageDailyDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
