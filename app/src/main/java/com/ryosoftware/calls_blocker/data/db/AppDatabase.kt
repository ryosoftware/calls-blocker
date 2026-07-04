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
@TypeConverters(CallReasonConverter::class, TypeConverter::class, ActionConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun numberDao(): NumberDao
    abstract fun historyDao(): HistoryDao
    abstract fun blockSuggestionDao(): BlockSuggestionDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `start_day` INTEGER NOT NULL,
                        `start_minute` INTEGER NOT NULL,
                        `end_day` INTEGER NOT NULL,
                        `end_minute` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
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
