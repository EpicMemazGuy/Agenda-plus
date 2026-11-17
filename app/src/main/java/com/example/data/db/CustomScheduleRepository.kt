package com.example.data.db

import kotlinx.coroutines.flow.Flow

class CustomScheduleRepository(
    private val dao: CustomScheduleDao
) {
    fun observeAll(): Flow<List<CustomScheduleItem>> = dao.observeAll()

    suspend fun getAllOnce(): List<CustomScheduleItem> = dao.getAllOnce()

    suspend fun upsert(item: CustomScheduleItem): Long = dao.upsert(item)

    suspend fun replaceAll(items: List<CustomScheduleItem>) = dao.replaceAll(items)

    suspend fun setCompleted(id: Long, completed: Boolean) = dao.updateCompletion(id, completed)

    suspend fun delete(id: Long) = dao.deleteById(id)
}
