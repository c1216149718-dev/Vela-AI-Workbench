package com.deepseek.widget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deepseek.widget.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING','PAUSED') ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING','PAUSED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE startedAt BETWEEN :start AND :end ORDER BY startedAt DESC")
    fun observeHistory(start: Long, end: Long): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: FocusSessionEntity): Long

    @Transaction
    suspend fun insertIfNoActive(session: FocusSessionEntity): Long {
        if (getActive() != null) return -1L
        return insert(session)
    }

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("UPDATE focus_sessions SET status = 'COMPLETED', endedAt = :endedAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('RUNNING','PAUSED')")
    suspend fun complete(id: Long, endedAt: Long, updatedAt: Long): Int

    @Query("UPDATE focus_sessions SET status = 'COMPLETED', endedAt = :endedAt, updatedAt = :updatedAt WHERE id = :id AND status = 'RUNNING'")
    suspend fun completeRunning(id: Long, endedAt: Long, updatedAt: Long): Int

    @Query("UPDATE focus_sessions SET status = 'CANCELLED', endedAt = :endedAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('RUNNING','PAUSED')")
    suspend fun cancel(id: Long, endedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM focus_sessions WHERE id = :id AND status IN ('RUNNING','PAUSED')")
    suspend fun deleteActive(id: Long): Int

    @Query("UPDATE focus_sessions SET status = 'PAUSED', pausedAt = :pausedAt, updatedAt = :updatedAt WHERE id = :id AND status = 'RUNNING'")
    suspend fun pause(id: Long, pausedAt: Long, updatedAt: Long): Int

    @Query("UPDATE focus_sessions SET status = 'RUNNING', pausedAt = NULL, accumulatedPauseMillis = :accumulated, expectedEndAt = :expectedEndAt, updatedAt = :updatedAt WHERE id = :id AND status = 'PAUSED'")
    suspend fun resume(id: Long, accumulated: Long, expectedEndAt: Long, updatedAt: Long): Int

    @Query("SELECT COALESCE(SUM(MAX(0, (endedAt - startedAt - accumulatedPauseMillis) / 60000)), 0) FROM focus_sessions WHERE status = 'COMPLETED' AND endedAt IS NOT NULL AND startedAt BETWEEN :start AND :end")
    suspend fun sumCompletedMinutes(start: Long, end: Long): Int

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): FocusSessionEntity?
}
