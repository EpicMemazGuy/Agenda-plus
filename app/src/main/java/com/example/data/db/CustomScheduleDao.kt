package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomScheduleDao {
    @Query("SELECT * FROM custom_schedule_items ORDER BY startTime ASC, id ASC")
    fun observeAll(): Flow<List<CustomScheduleItem>>

    @Query("SELECT * FROM custom_schedule_items ORDER BY startTime ASC, id ASC")
    suspend fun getAllOnce(): List<CustomScheduleItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CustomScheduleItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CustomScheduleItem>)

    @Query("UPDATE custom_schedule_items SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletion(id: Long, completed: Boolean)

    @Query("DELETE FROM custom_schedule_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_schedule_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<CustomScheduleItem>) {
        deleteAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
