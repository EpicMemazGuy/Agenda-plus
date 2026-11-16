package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CustomScheduleItem::class],
    version = 1,
    exportSchema = false
)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun customScheduleDao(): CustomScheduleDao

    companion object {
        @Volatile
        private var instance: AgendaDatabase? = null

        fun getInstance(context: Context): AgendaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgendaDatabase::class.java,
                    "agenda_plus_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
