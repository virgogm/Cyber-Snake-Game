package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StageDao {
    @Query("SELECT * FROM stage_records WHERE stageId = :stageId")
    fun getRecord(stageId: Int): Flow<StageRecord?>

    @Query("SELECT * FROM stage_records WHERE stageId = :stageId")
    suspend fun getRecordSync(stageId: Int): StageRecord?

    @Query("SELECT * FROM stage_records ORDER BY stageId ASC")
    fun getAllRecords(): Flow<List<StageRecord>>

    @Query("SELECT * FROM stage_records ORDER BY stageId ASC")
    suspend fun getAllRecordsSync(): List<StageRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: StageRecord)
}
