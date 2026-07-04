package com.ryosoftware.calls_blocker.di

import android.content.Context
import com.ryosoftware.calls_blocker.data.BackupManager
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.FileLogger
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.data.db.AppDatabase
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.BlockSuggestionDao
import com.ryosoftware.calls_blocker.data.db.HistoryDao
import com.ryosoftware.calls_blocker.data.db.ScheduleRuleDao
import com.ryosoftware.calls_blocker.data.repository.BlockSuggestionsRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.data.repository.HistoryRepository
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideNumberDao(database: AppDatabase): NumberDao =
        database.numberDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao =
        database.historyDao()

    @Provides
    fun provideBlockSuggestionDao(database: AppDatabase): BlockSuggestionDao =
        database.blockSuggestionDao()

    @Provides
    fun provideScheduleRuleDao(database: AppDatabase): ScheduleRuleDao =
        database.scheduleRuleDao()

    @Provides
    @Singleton
    fun provideScheduleRuleRepository(dao: ScheduleRuleDao): ScheduleRuleRepository =
        ScheduleRuleRepository(dao)

    @Provides
    @Singleton
    fun provideNumberRepository(dao: NumberDao): NumberRepository =
        NumberRepository(dao)

    @Provides
    @Singleton
    fun provideHistoryRepository(dao: HistoryDao): HistoryRepository =
        HistoryRepository(dao)

    @Provides
    @Singleton
    fun provideBlockSuggestionsRepository(dao: BlockSuggestionDao): BlockSuggestionsRepository =
        BlockSuggestionsRepository(dao)

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager =
        SettingsManager(context)

    @Provides
    @Singleton
    fun provideLogger(logger: FileLogger): Logger = logger

    @Provides
    @Singleton
    fun provideBackupManager(
        numberDao: NumberDao,
        historyDao: HistoryDao,
        blockSuggestionDao: BlockSuggestionDao,
        scheduleRuleDao: ScheduleRuleDao,
        settingsManager: SettingsManager,
        @ApplicationContext context: Context
    ): BackupManager = BackupManager(
        numberDao,
        historyDao,
        blockSuggestionDao,
        scheduleRuleDao,
        settingsManager,
        context
    )
}
