package com.ryosoftware.calls_blocker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history")
    suspend fun getAllList(): List<HistoryEntry>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Insert
    suspend fun insertAll(entries: List<HistoryEntry>)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM history WHERE phone_number = :phoneNumber")
    suspend fun deleteByPhoneNumber(phoneNumber: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
