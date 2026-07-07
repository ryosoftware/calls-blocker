package com.ryosoftware.calls_blocker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberDao {
    @Query("SELECT * FROM numbers WHERE action = :action AND type = :type ORDER BY phone_number ASC LIMIT :limit OFFSET :offset")
    suspend fun getByTypeBatch(action: Action, type: Type, limit: Int, offset: Int): List<Number>

    @Query("SELECT * FROM numbers")
    suspend fun getAllList(): List<Number>

    @Query("DELETE FROM numbers")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(number: Number)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(numbers: List<Number>)

    @Delete
    suspend fun delete(number: Number)

    @Query("DELETE FROM numbers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE numbers SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String)

    @Query("DELETE FROM numbers WHERE phone_number = :phoneNumber AND action = :action AND type = :type")
    suspend fun deleteByPhoneNumber(phoneNumber: String, action: Action, type: Type = Type.EXACT_COINCIDENCE)

    @Query("SELECT phone_number FROM numbers WHERE action = :action AND type = :type")
    suspend fun getNumbersByType(action: Action, type: Type): List<String>

    @Query("SELECT COUNT(*) FROM numbers WHERE action = :action")
    fun getCountByAction(action: Action): Flow<Int>

    @Query("SELECT phone_number FROM numbers WHERE action = :action")
    fun getPhoneNumbers(action: Action): Flow<List<String>>
}
