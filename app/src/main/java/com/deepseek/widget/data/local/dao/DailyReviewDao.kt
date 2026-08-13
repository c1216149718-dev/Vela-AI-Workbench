package com.deepseek.widget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepseek.widget.data.local.entity.DailyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReviewDao {

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    fun observeByDate(date: String): Flow<DailyReviewEntity?>

    @Query("SELECT * FROM daily_reviews WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeRange(start: String, end: String): Flow<List<DailyReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DailyReviewEntity)

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    suspend fun getByDate(date: String): DailyReviewEntity?
}
