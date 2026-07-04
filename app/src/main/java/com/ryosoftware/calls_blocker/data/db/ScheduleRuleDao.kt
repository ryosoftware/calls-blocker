package com.ryosoftware.calls_blocker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleRuleDao {
    @Query("DELETE FROM schedule_rules")
    suspend fun clearAll()

    @Query("SELECT * FROM schedule_rules ORDER BY start_day, start_minute")
    fun getAll(): Flow<List<ScheduleRule>>

    @Query("SELECT * FROM schedule_rules")
    suspend fun getAllList(): List<ScheduleRule>

    @Insert
    suspend fun insert(rule: ScheduleRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scheduleRules: List<ScheduleRule>)

    @Update
    suspend fun update(rule: ScheduleRule)

    @Delete
    suspend fun delete(rule: ScheduleRule)
}
