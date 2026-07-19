package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EnergyLevel {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isImportant: Boolean = false,
    val deadlineMs: Long? = null,
    val energyRequired: EnergyLevel = EnergyLevel.MEDIUM,
    val category: String = "ทั่วไป",
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val isCompleted: Boolean = false,
    val imagePath: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)
