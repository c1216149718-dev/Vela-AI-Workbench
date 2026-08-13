package com.deepseek.widget.data.repository

import com.deepseek.widget.data.local.dao.DailyReviewDao
import com.deepseek.widget.data.local.entity.DailyReviewEntity
import com.deepseek.widget.domain.model.DailyReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReviewRepository {
    fun observeByDate(date: String): Flow<DailyReview?>
    fun observeRange(start: String, end: String): Flow<List<DailyReview>>
    suspend fun upsert(date: String, rating: Int?, note: String)
    suspend fun getByDate(date: String): DailyReview?
}

class ReviewRepositoryImpl(
    private val dao: DailyReviewDao
) : ReviewRepository {

    override fun observeByDate(date: String): Flow<DailyReview?> =
        dao.observeByDate(date).map { it?.toDomain() }

    override fun observeRange(start: String, end: String): Flow<List<DailyReview>> =
        dao.observeRange(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(date: String, rating: Int?, note: String) {
        val existing = dao.getByDate(date)
        val now = System.currentTimeMillis()
        dao.upsert(
            DailyReviewEntity(
                date = date,
                rating = rating,
                note = note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    override suspend fun getByDate(date: String): DailyReview? = dao.getByDate(date)?.toDomain()
}

internal fun DailyReviewEntity.toDomain(): DailyReview = DailyReview(
    date = date,
    rating = rating,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)
