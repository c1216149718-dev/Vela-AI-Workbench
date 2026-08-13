package com.deepseek.widget.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class WorkbenchMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkbenchDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2KeepsTasksAndCreatesAiUsageDaily() {
        // 用 v1 schema 建库并写入一条任务
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, notes, projectId, status, priority, " +
                    "plannedDate, dueAt, reminderAt, estimateMinutes, sortOrder, sourceType, " +
                    "sourceUrl, createdAt, updatedAt, completedAt) VALUES " +
                    "(100, '迁移前任务', '', NULL, 'PLANNED', 1, '2026-08-04', NULL, NULL, " +
                    "NULL, 0, 'MANUAL', NULL, 1000, 1000, NULL)"
            )
            close()
        }

        // 运行迁移并校验结果 schema 与当前（v2）一致
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, WorkbenchDatabase.MIGRATION_1_2)

        // 原有数据在迁移后仍存在
        val cursor = db.query("SELECT title FROM tasks WHERE id = 100")
        assertTrue(cursor.moveToFirst())
        assertEquals("迁移前任务", cursor.getString(0))
        cursor.close()

        // 新表 ai_usage_daily 已存在且可写入
        db.execSQL(
            "INSERT INTO ai_usage_daily (provider, credentialId, date, model, currency, " +
                "cost, requests, inputTokens, outputTokens, totalTokens, isEstimated, updatedAt) VALUES " +
                "('APIKEY_FUN', 'k1', '2026-08-04', 'gpt-4o', 'USD', '1.23', 10, 1000, 2000, " +
                "3000, 0, 2000)"
        )
        val usageCursor = db.query(
            "SELECT cost, totalTokens FROM ai_usage_daily " +
                "WHERE provider='APIKEY_FUN' AND credentialId='k1' AND date='2026-08-04' AND model='gpt-4o'"
        )
        assertTrue(usageCursor.moveToFirst())
        assertEquals("1.23", usageCursor.getString(0))
        assertEquals(3000, usageCursor.getLong(1))
        usageCursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2AiUsageDailyHasCompositePrimaryKey() {
        helper.createDatabase(TEST_DB, 1).apply { close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, WorkbenchDatabase.MIGRATION_1_2)

        // 复合主键：相同 (provider, credentialId, date, model) 的第二次插入应被 REPLACE，行数保持 1
        val insert = { id: Int ->
            db.execSQL(
                "INSERT OR REPLACE INTO ai_usage_daily (provider, credentialId, date, model, " +
                    "currency, cost, requests, inputTokens, outputTokens, totalTokens, isEstimated, " +
                    "updatedAt) VALUES ('DEEPSEEK', 'sk', '2026-08-04', 'deepseek-chat', 'CNY', " +
                    "'$id.00', 5, 500, 500, 1000, 0, 1000)"
            )
        }
        insert(1)
        insert(2)
        val count = db.query("SELECT COUNT(*) FROM ai_usage_daily")
        assertTrue(count.moveToFirst())
        assertEquals(1, count.getInt(0))
        count.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3KeepsTasksAndAddsScheduleState() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO tasks (id, title, notes, projectId, status, priority, " +
                    "plannedDate, dueAt, reminderAt, estimateMinutes, sortOrder, sourceType, " +
                    "sourceUrl, createdAt, updatedAt, completedAt) VALUES " +
                    "(200, '保留任务', '', NULL, 'IN_PROGRESS', 2, '2026-08-09', NULL, NULL, " +
                    "NULL, 0, 'MANUAL', NULL, 1000, 1000, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, WorkbenchDatabase.MIGRATION_2_3)
        val cursor = db.query(
            "SELECT title, startAt, reminderOffsetMinutes, statusBeforeDone FROM tasks WHERE id = 200"
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("保留任务", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.isNull(2))
        assertTrue(cursor.isNull(3))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4KeepsExistingUsageAndCreatesPeriodCache() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO ai_usage_daily (provider, credentialId, date, model, currency, " +
                    "cost, requests, inputTokens, outputTokens, totalTokens, isEstimated, updatedAt) VALUES " +
                    "('APIKEY_FUN', 'old-key', '2026-08-10', '__all__', 'USD', '2.50', 8, 10, 20, 30, 0, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, WorkbenchDatabase.MIGRATION_3_4)
        val old = db.query("SELECT cost FROM ai_usage_daily WHERE credentialId='old-key'")
        assertTrue(old.moveToFirst())
        assertEquals("2.50", old.getString(0))
        old.close()

        db.execSQL(
            "INSERT INTO ai_usage_model_period (provider, credentialId, credentialLabel, periodStart, " +
                "periodEnd, model, currency, cost, requests, inputTokens, outputTokens, totalTokens, " +
                "isEstimated, updatedAt) VALUES ('APIKEY_FUN', 'old-key', '工作 Key', '2026-08-04', " +
                "'2026-08-10', 'claude', 'USD', '2.50', 8, 10, 20, 30, 0, 2000)"
        )
        db.execSQL(
            "INSERT INTO ai_usage_sync_state (provider, credentialId, credentialLabel, periodStart, " +
                "periodEnd, lastSuccessAt, lastAttemptAt, errorMessage) VALUES " +
                "('APIKEY_FUN', 'old-key', '工作 Key', '2026-08-04', '2026-08-10', 2000, 2000, '')"
        )
        val created = db.query("SELECT COUNT(*) FROM ai_usage_model_period")
        assertTrue(created.moveToFirst())
        assertEquals(1, created.getInt(0))
        created.close()
        db.close()
    }
}
