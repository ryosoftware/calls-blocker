package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_rules")
data class ScheduleRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "start_day")
    val startDay: Int,
    @ColumnInfo(name = "start_minute")
    val startMinute: Int,
    @ColumnInfo(name = "end_day")
    val endDay: Int,
    @ColumnInfo(name = "end_minute")
    val endMinute: Int,
)
