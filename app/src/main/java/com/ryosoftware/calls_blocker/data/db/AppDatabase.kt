package com.ryosoftware.calls_blocker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Number::class, HistoryEntry::class, BlockSuggestion::class, ScheduleRule::class],
    version = 2,
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

        private val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE history ADD COLUMN flags INTEGER NOT NULL DEFAULT 0")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calls_blocker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
