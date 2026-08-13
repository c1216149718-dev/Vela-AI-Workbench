package com.deepseek.widget.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepseek.widget.data.local.entity.DailyReviewEntity
import com.deepseek.widget.data.local.entity.FocusSessionEntity
import com.deepseek.widget.data.local.entity.ProjectEntity
import com.deepseek.widget.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkbenchDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: WorkbenchDatabase
    private val now = System.currentTimeMillis()

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkbenchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // 手动插入默认项目（内存库不触发 onCreate callback）
        database.projectDao().insert(
            ProjectEntity(id = 1, name = "收件箱", colorArgb = 0xFF6F6963.toInt(), archived = false, createdAt = now, updatedAt = now)
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun defaultInboxProjectExists() = runTest {
        val project = database.projectDao().getById(1)
        assertNotNull(project)
        assertEquals("收件箱", project!!.name)
    }

    @Test
    fun taskInsertAndObserveToday() = runTest {
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "完成报告",
                status = "PLANNED",
                priority = 2,
                plannedDate = "2026-08-04",
                sortOrder = now,
                createdAt = now,
                updatedAt = now
            )
        )
        val today = database.taskDao().observeToday("2026-08-04").first()
        assertEquals(1, today.size)
        assertEquals("完成报告", today[0].title)
        assertTrue(taskId > 0)
    }

    @Test
    fun taskCompleteSetsCompletedAt() = runTest {
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "测试任务",
                status = "PLANNED",
                plannedDate = "2026-08-04",
                reminderAt = now + 60_000L,
                sortOrder = now,
                createdAt = now,
                updatedAt = now
            )
        )
        database.taskDao().complete(taskId, now, now)
        val task = database.taskDao().getById(taskId)
        assertEquals("DONE", task!!.status)
        assertNotNull(task.completedAt)
        assertEquals(now + 60_000L, task.reminderAt)
        assertEquals("PLANNED", task.statusBeforeDone)
    }

    @Test
    fun reminderIsConsumedOnlyWhenExpectedTimestampMatches() = runTest {
        val reminderAt = now + 60_000L
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "提醒竞态测试",
                status = "PLANNED",
                reminderAt = reminderAt,
                createdAt = now,
                updatedAt = now
            )
        )

        assertEquals(0, database.taskDao().consumeReminder(taskId, reminderAt - 1L, now + 1L))
        assertEquals(reminderAt, database.taskDao().getById(taskId)!!.reminderAt)
        assertEquals(1, database.taskDao().consumeReminder(taskId, reminderAt, now + 2L))
        assertNull(database.taskDao().getById(taskId)!!.reminderAt)
    }

    @Test
    fun taskRestoreSetsPlannedStatusWhenPlannedDateExists() = runTest {
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "带日期任务",
                status = "DONE",
                statusBeforeDone = "IN_PROGRESS",
                plannedDate = "2026-08-04",
                completedAt = now,
                sortOrder = now,
                createdAt = now,
                updatedAt = now
            )
        )
        database.taskDao().restore(taskId, "IN_PROGRESS", now)
        val task = database.taskDao().getById(taskId)
        assertEquals("IN_PROGRESS", task!!.status)
        assertNull(task.completedAt)
        assertNull(task.statusBeforeDone)
    }

    @Test
    fun taskRestoreSetsBacklogWhenNoPlannedDate() = runTest {
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "无日期任务",
                status = "DONE",
                completedAt = now,
                sortOrder = now,
                createdAt = now,
                updatedAt = now
            )
        )
        database.taskDao().restore(taskId, "BACKLOG", now)
        val task = database.taskDao().getById(taskId)
        assertEquals("BACKLOG", task!!.status)
    }

    @Test
    fun focusSessionInsertAndObserveActive() = runTest {
        val sessionId = database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                status = "RUNNING",
                createdAt = now,
                updatedAt = now
            )
        )
        val active = database.focusSessionDao().observeActive().first()
        assertNotNull(active)
        assertEquals("RUNNING", active!!.status)
        assertTrue(sessionId > 0)
    }

    @Test
    fun focusSessionPauseThenResume() = runTest {
        val sessionId = database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                status = "RUNNING",
                createdAt = now,
                updatedAt = now
            )
        )
        // 暂停
        val paused = database.focusSessionDao().pause(sessionId, now + 300_000L, now + 300_000L)
        assertEquals(1, paused)
        // 恢复
        val pauseDuration = 100_000L
        val resumed = database.focusSessionDao().resume(
            sessionId,
            pauseDuration,
            now + 1_500_000L + pauseDuration,
            now + 400_000L
        )
        assertEquals(1, resumed)
        val session = database.focusSessionDao().getById(sessionId)
        assertEquals("RUNNING", session!!.status)
        assertEquals(pauseDuration, session.accumulatedPauseMillis)
    }

    @Test
    fun focusSessionComplete() = runTest {
        val sessionId = database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                status = "RUNNING",
                createdAt = now,
                updatedAt = now
            )
        )
        val result = database.focusSessionDao().complete(sessionId, now + 1_500_000L, now + 1_500_000L)
        assertEquals(1, result)
        val session = database.focusSessionDao().getById(sessionId)
        assertEquals("COMPLETED", session!!.status)
    }

    @Test
    fun focusSessionDeleteActiveRemovesShortRecord() = runTest {
        val sessionId = database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                status = "RUNNING",
                createdAt = now,
                updatedAt = now
            )
        )

        assertEquals(1, database.focusSessionDao().deleteActive(sessionId))
        assertNull(database.focusSessionDao().getById(sessionId))
    }

    @Test
    fun focusSessionAllowsOnlyOneActiveSession() = runTest {
        val first = FocusSessionEntity(
            plannedMinutes = 25,
            startedAt = now,
            expectedEndAt = now + 1_500_000L,
            status = "RUNNING",
            createdAt = now,
            updatedAt = now
        )
        val second = first.copy(startedAt = now + 1_000L, expectedEndAt = now + 1_501_000L)

        assertTrue(database.focusSessionDao().insertIfNoActive(first) > 0)
        assertEquals(-1L, database.focusSessionDao().insertIfNoActive(second))
    }

    @Test
    fun completionWorkerTransitionDoesNotCompletePausedSession() = runTest {
        val id = database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                status = "RUNNING",
                createdAt = now,
                updatedAt = now
            )
        )
        database.focusSessionDao().pause(id, now + 60_000L, now + 60_000L)

        assertEquals(0, database.focusSessionDao().completeRunning(id, now + 1_500_000L, now + 1_500_000L))
        assertEquals("PAUSED", database.focusSessionDao().getById(id)!!.status)
    }

    @Test
    fun todayOverviewIncludesCompletedTasks() = runTest {
        database.taskDao().insert(
            TaskEntity(
                title = "未完成",
                status = "PLANNED",
                plannedDate = "2026-08-04",
                createdAt = now,
                updatedAt = now
            )
        )
        database.taskDao().insert(
            TaskEntity(
                title = "已完成",
                status = "DONE",
                plannedDate = "2026-08-04",
                completedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )

        val tasks = database.taskDao().observeTodayOverview("2026-08-04").first()
        assertEquals(2, tasks.size)
        assertTrue(tasks.any { it.status == "DONE" })
    }

    @Test
    fun focusSummaryUsesActualDuration() = runTest {
        database.focusSessionDao().insert(
            FocusSessionEntity(
                plannedMinutes = 25,
                startedAt = now,
                expectedEndAt = now + 1_500_000L,
                endedAt = now + 12 * 60_000L,
                accumulatedPauseMillis = 2 * 60_000L,
                status = "COMPLETED",
                createdAt = now,
                updatedAt = now
            )
        )

        assertEquals(10, database.focusSessionDao().sumCompletedMinutes(now - 1, now + 20 * 60_000L))
    }

    @Test
    fun dailyReviewUpsertAndObserve() = runTest {
        val date = "2026-08-04"
        database.dailyReviewDao().upsert(
            DailyReviewEntity(date = date, rating = 4, note = "不错", createdAt = now, updatedAt = now)
        )
        val review = database.dailyReviewDao().getByDate(date)
        assertNotNull(review)
        assertEquals(4, review!!.rating)
        assertEquals("不错", review.note)

        // 更新
        database.dailyReviewDao().upsert(
            DailyReviewEntity(date = date, rating = 5, note = "很好", createdAt = now, updatedAt = now)
        )
        val updated = database.dailyReviewDao().getByDate(date)
        assertEquals(5, updated!!.rating)
    }

    @Test
    fun countPlannedReturnsCorrectCount() = runTest {
        database.taskDao().insert(
            TaskEntity(title = "任务1", status = "PLANNED", plannedDate = "2026-08-04", sortOrder = 1, createdAt = now, updatedAt = now)
        )
        database.taskDao().insert(
            TaskEntity(title = "任务2", status = "PLANNED", plannedDate = "2026-08-04", sortOrder = 2, createdAt = now, updatedAt = now)
        )
        database.taskDao().insert(
            TaskEntity(title = "任务3", status = "BACKLOG", plannedDate = "2026-08-04", sortOrder = 3, createdAt = now, updatedAt = now)
        )
        assertEquals(2, database.taskDao().countPlanned("2026-08-04"))
    }
}
