package com.ryosoftware.calls_blocker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Number::class, HistoryEntry::class, BlockSuggestion::class, ScheduleRule::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ActionConverter::class, DirectionConverter::class, NumberTypeConverter::class, ReasonConverter::class, TypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun numberDao(): NumberDao
    abstract fun historyDao(): HistoryDao
    abstract fun blockSuggestionDao(): BlockSuggestionDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calls_blocker.db"
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
