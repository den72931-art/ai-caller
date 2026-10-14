package com.aicaller.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Insert
    suspend fun insert(record: CallRecord): Long

    @Update
    suspend fun update(record: CallRecord)

    @Query("SELECT * FROM call_records ORDER BY startTimeMillis DESC")
    fun observeAll(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallRecord?

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun delete(id: Long)
}
