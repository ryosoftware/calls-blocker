package com.ryosoftware.calls_blocker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberDao {
    @Query("SELECT * FROM numbers ORDER BY created_at DESC")
    fun getAll(): Flow<List<Number>>

    @Query("SELECT * FROM numbers WHERE type = :type AND action = :action ORDER BY phone_number ASC LIMIT :limit OFFSET :offset")
    suspend fun getByTypeBatch(type: Type, action: Action = Action.ACTION_BLOCK, limit: Int, offset: Int): List<Number>

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

    @Query("DELETE FROM numbers WHERE phone_number = :phoneNumber AND type = :type")
    suspend fun deleteByPhoneNumber(phoneNumber: String, type: Type = Type.EXACT_COINCIDENCE)

    @Query("SELECT phone_number FROM numbers WHERE type = :type AND action = :action")
    suspend fun getNumbersByTypeAndAction(type: Type, action: Action): List<String>

    @Query("SELECT COUNT(*) FROM numbers WHERE action = :action")
    fun getCountByAction(action: Action): Flow<Int>

    @Query("SELECT phone_number FROM numbers WHERE action = :action")
    fun getPhoneNumbersByAction(action: Action): Flow<List<String>>
}
