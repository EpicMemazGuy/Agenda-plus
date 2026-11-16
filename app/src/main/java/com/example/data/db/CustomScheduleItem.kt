package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_schedule_items")
data class CustomScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val dateIso: String = "", // yyyy-MM-dd when non-recurring
    val daysOfWeekCsv: String = "", // e.g. "1,3,5" where 1=Mon..7=Sun when recurring
    val isRecurring: Boolean = false,
    val colorKey: String = "BLUE", // BLUE, EMERALD, AMBER, PURPLE, CORAL
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun appliesToDate(targetDateIso: String, targetDayOfWeek: Int): Boolean {
        return if (isRecurring) {
            daysOfWeekCsv
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .contains(targetDayOfWeek)
        } else {
            dateIso == targetDateIso
        }
    }
}
