package com.deepseek.widget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deepseek.widget.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE plannedDate = :date AND status != 'CANCELLED' AND status != 'DONE' ORDER BY priority DESC, sortOrder ASC")
    fun observeToday(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE plannedDate = :date AND status != 'CANCELLED' ORDER BY status = 'DONE', priority DESC, sortOrder ASC")
    fun observeTodayOverview(date: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE " +
            "((plannedDate = :date) OR (plannedDate IS NULL AND status = 'BACKLOG')) " +
            "AND status NOT IN ('CANCELLED','DONE') " +
            "ORDER BY CASE WHEN plannedDate = :date THEN 0 ELSE 1 END, " +
            "priority DESC, updatedAt DESC, sortOrder ASC"
    )
    fun observeNextSteps(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'PLANNED' ORDER BY plannedDate ASC, priority DESC, sortOrder ASC")
    fun observePlanned(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'DONE' AND status != 'CANCELLED' ORDER BY priority DESC, sortOrder ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE plannedDate = :date AND status != 'CANCELLED' AND status != 'DONE' ORDER BY priority DESC, sortOrder ASC")
    suspend fun getToday(date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE reminderAt IS NOT NULL AND reminderAt > :now AND status NOT IN ('DONE','CANCELLED')")
    suspend fun getPendingReminders(now: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET statusBeforeDone = status, status = 'DONE', completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id AND status NOT IN ('DONE','CANCELLED')")
    suspend fun complete(id: Long, completedAt: Long, updatedAt: Long)

    @Query("UPDATE tasks SET status = :status, statusBeforeDone = NULL, completedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE tasks SET status = 'IN_PROGRESS', updatedAt = :updatedAt WHERE id = :id AND status NOT IN ('DONE','CANCELLED','IN_PROGRESS')")
    suspend fun markInProgress(id: Long, updatedAt: Long): Int

    @Query("UPDATE tasks SET reminderAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearReminder(id: Long, updatedAt: Long)

    @Query("UPDATE tasks SET reminderAt = NULL, updatedAt = :updatedAt WHERE id = :id AND reminderAt = :expectedReminderAt AND status NOT IN ('DONE','CANCELLED')")
    suspend fun consumeReminder(id: Long, expectedReminderAt: Long, updatedAt: Long): Int

    @Transaction
    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Long)

    @Transaction
    suspend fun updateSortOrders(orders: List<TaskOrderUpdate>) {
        orders.forEach { updateSortOrder(it.id, it.sortOrder) }
    }

    @Query("SELECT COUNT(*) FROM tasks WHERE plannedDate = :date AND status = 'PLANNED'")
    suspend fun countPlanned(date: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE plannedDate = :date AND status = 'DONE' AND completedAt BETWEEN :start AND :end")
    suspend fun countCompleted(date: String, start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE dueAt IS NOT NULL AND dueAt < :now AND status NOT IN ('DONE','CANCELLED')")
    suspend fun countOverdue(now: Long): Int
}

data class TaskOrderUpdate(val id: Long, val sortOrder: Long)
