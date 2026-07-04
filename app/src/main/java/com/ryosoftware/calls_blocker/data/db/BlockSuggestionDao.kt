package com.ryosoftware.calls_blocker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BlockSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockSuggestion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<BlockSuggestion>)

    @Query("SELECT * FROM dismissed_block_suggestions")
    suspend fun getAllList(): List<BlockSuggestion>

    @Query("DELETE FROM dismissed_block_suggestions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM dismissed_block_suggestions WHERE phone_number = :phoneNumber")
    suspend fun count(phoneNumber: String): Int
}
