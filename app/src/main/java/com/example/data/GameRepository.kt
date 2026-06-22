package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val stageDao: StageDao) {
    val allRecords: Flow<List<StageRecord>> = stageDao.getAllRecords()

    suspend fun getRecordSync(stageId: Int): StageRecord? {
        return stageDao.getRecordSync(stageId)
    }

    suspend fun saveRecord(stageId: Int, score: Int, completed: Boolean) {
        val existing = stageDao.getRecordSync(stageId)
        val newHighScore = if (existing != null) {
            maxOf(existing.highScore, score)
        } else {
            score
        }
        val isCompletedNow = if (existing != null) {
            existing.isCompleted || completed
        } else {
            completed
        }
        stageDao.insertOrUpdateRecord(
            StageRecord(
                stageId = stageId,
                highScore = newHighScore,
                isCompleted = isCompletedNow
            )
        )
    }

    suspend fun preloadStages() {
        val all = stageDao.getAllRecordsSync()
        if (all.size < 30) {
            for (i in 1..30) {
                if (all.none { it.stageId == i }) {
                    stageDao.insertOrUpdateRecord(StageRecord(stageId = i, highScore = 0, isCompleted = false))
                }
            }
        }
    }
}
