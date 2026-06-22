package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stage_records")
data class StageRecord(
    @PrimaryKey val stageId: Int,
    val highScore: Int = 0,
    val isCompleted: Boolean = false
)
