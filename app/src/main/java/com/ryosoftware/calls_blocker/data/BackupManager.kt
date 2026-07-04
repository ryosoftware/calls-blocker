package com.ryosoftware.calls_blocker.data

import android.content.Context
import android.net.Uri
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.BlockSuggestionDao
import com.ryosoftware.calls_blocker.data.db.BlockSuggestion
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.HistoryDao
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.data.db.ScheduleRuleDao
import com.ryosoftware.calls_blocker.data.db.Type
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val numberDao: NumberDao,
    private val historyDao: HistoryDao,
    private val blockSuggestionDao: BlockSuggestionDao,
    private val scheduleRuleDao: ScheduleRuleDao,
    private val settingsManager: SettingsManager,
    @param:ApplicationContext private val context: Context
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun backup(uri: Uri): Result<Unit> = runCatching {
        val backupData = BackupData(
            settings = BackupData.BackupSettings(
                screeningDialogDismissed = settingsManager.screeningDialogDismissed,
                defaultCountryIso = settingsManager.defaultCountryIso,
                blockUnknown = settingsManager.blockUnknown,
                blockHidden = settingsManager.blockHidden,
                blockGroups = settingsManager.blockGroups,
                blockedGroupIds = settingsManager.blockedGroupIds,
                blockInternational = settingsManager.blockInternational,
                allowedCountryIsos = settingsManager.allowedCountryIsos,
                blockNotCalled = settingsManager.blockNotCalled,
                notCalledWindowDays = settingsManager.notCalledWindowDays,
                blockRejected = settingsManager.blockRejected,
                rejectedWindowDays = settingsManager.rejectedWindowDays,
                blockRepeated = settingsManager.blockRepeated,
                repeatedCallCount = settingsManager.repeatedCallCount,
                repeatedCallWindowMinutes = settingsManager.repeatedCallWindowMinutes,
                skipCallLog = settingsManager.skipCallLog,
                findMyPhoneEnabled = settingsManager.findMyPhoneEnabled,
                findMyPhonePhoneNumbers = settingsManager.findMyPhonePhoneNumbers,
                findMyPhoneCallCount = settingsManager.findMyPhoneCallCount,
                findMyPhoneWindowMinutes = settingsManager.findMyPhoneWindowMinutes,
                findMyPhoneRingtoneUri = settingsManager.findMyPhoneRingtoneUri,
                contactsPermissionRequested = settingsManager.contactsPermissionRequested,
                callsLogPermissionRequested = settingsManager.callsLogPermissionRequested,
                notificationsPermissionRequested = settingsManager.notificationsPermissionRequested,
                isLoggingToFile = settingsManager.isLoggingToFile,
                lastActiveTab = settingsManager.lastActiveTab,
            ),
            numbers = numberDao.getAllList().map { number ->
                BackupData.BackupNumber(
                    phoneNumber = number.phoneNumber,
                    description = number.description,
                    type = number.type.code,
                    action = number.action.code,
                    createdAt = number.createdAt,
                )
            },
            history = historyDao.getAllList().map { entry ->
                BackupData.BackupHistoryEntry(
                    phoneNumber = entry.phoneNumber,
                    timestamp = entry.timeStamp,
                    reason = entry.reason.code,
                )
            },
            dismissedBlockSuggestions = blockSuggestionDao.getAllList().map { suggestion ->
                BackupData.BackupBlockSuggestion(
                    phoneNumber = suggestion.phoneNumber,
                    dismissedAt = suggestion.dismissedAt,
                )
            },
            scheduleRules = scheduleRuleDao.getAllList().map { rule ->
                BackupData.BackupScheduleRule(
                    startDay = rule.startDay,
                    startMinute = rule.startMinute,
                    endDay = rule.endDay,
                    endMinute = rule.endMinute,
                )
            },
        )

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.encodeToString(backupData).encodeToByteArray())
        } ?: throw IOException("Cannot open output stream")
    }

    suspend fun restore(uri: Uri): Result<Unit> = runCatching {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw IOException("Cannot open input stream")

        val backupData = json.decodeFromString<BackupData>(jsonString)
        val settings = backupData.settings

        settingsManager.screeningDialogDismissed = settings.screeningDialogDismissed
        settingsManager.defaultCountryIso = settings.defaultCountryIso
        settingsManager.blockUnknown = settings.blockUnknown
        settingsManager.blockHidden = settings.blockHidden
        settingsManager.blockGroups = settings.blockGroups
        settingsManager.blockedGroupIds = settings.blockedGroupIds
        settingsManager.blockInternational = settings.blockInternational
        settingsManager.allowedCountryIsos = settings.allowedCountryIsos
        settingsManager.blockNotCalled = settings.blockNotCalled
        settingsManager.notCalledWindowDays = settings.notCalledWindowDays ?: context.resources.getInteger(R.integer.not_called_window_days_default)
        settingsManager.blockRejected = settings.blockRejected
        settingsManager.rejectedWindowDays = settings.rejectedWindowDays ?: context.resources.getInteger(R.integer.rejected_window_days_default)
        settingsManager.blockRepeated = settings.blockRepeated
        settingsManager.repeatedCallCount = settings.repeatedCallCount ?: context.resources.getInteger(R.integer.repeated_call_count_default)
        settingsManager.repeatedCallWindowMinutes = settings.repeatedCallWindowMinutes ?: context.resources.getInteger(R.integer.repeated_call_window_minutes_default)
        settingsManager.skipCallLog = settings.skipCallLog
        settingsManager.findMyPhoneEnabled = settings.findMyPhoneEnabled
        settingsManager.findMyPhonePhoneNumbers = settings.findMyPhonePhoneNumbers
        settingsManager.findMyPhoneCallCount = settings.findMyPhoneCallCount ?: context.resources.getInteger(R.integer.find_my_phone_call_count_default)
        settingsManager.findMyPhoneWindowMinutes = settings.findMyPhoneWindowMinutes ?: context.resources.getInteger(R.integer.find_my_phone_window_minutes_default)
        settingsManager.findMyPhoneRingtoneUri = settings.findMyPhoneRingtoneUri
        settingsManager.isLoggingToFile = settings.isLoggingToFile ?: BuildConfig.DEBUG
        if (settings.contactsPermissionRequested) settingsManager.contactsPermissionRequested = true
        if (settings.callsLogPermissionRequested) settingsManager.callsLogPermissionRequested = true
        if (settings.notificationsPermissionRequested) settingsManager.notificationsPermissionRequested = true
        settingsManager.lastActiveTab = settings.lastActiveTab

        numberDao.clearAll()
        numberDao.insertAll(backupData.numbers.map { numberEntry ->
            Number(
                phoneNumber = numberEntry.phoneNumber,
                description = numberEntry.description,
                type = Type.fromCode(numberEntry.type),
                action = Action.fromCode(numberEntry.action),
                createdAt = numberEntry.createdAt,
            )
        })

        historyDao.clearAll()
        historyDao.insertAll(backupData.history.map { historyEntry ->
            HistoryEntry(
                phoneNumber = historyEntry.phoneNumber,
                timeStamp = historyEntry.timestamp,
                reason = Reason.fromCode(historyEntry.reason),
            )
        })

        blockSuggestionDao.clearAll()
        blockSuggestionDao.insertAll(backupData.dismissedBlockSuggestions.map { suggestionEntry ->
            BlockSuggestion(
                phoneNumber = suggestionEntry.phoneNumber,
                dismissedAt = suggestionEntry.dismissedAt,
            )
        })

        scheduleRuleDao.clearAll()
        scheduleRuleDao.insertAll(backupData.scheduleRules.map { scheduleEntry ->
            ScheduleRule(
                startDay = scheduleEntry.startDay,
                startMinute = scheduleEntry.startMinute,
                endDay = scheduleEntry.endDay,
                endMinute = scheduleEntry.endMinute,
            )
        })
    }
}

@Serializable
data class BackupData(
    val settings: BackupData.BackupSettings = BackupData.BackupSettings(),
    val numbers: List<BackupData.BackupNumber> = emptyList(),
    val history: List<BackupData.BackupHistoryEntry> = emptyList(),
    @SerialName("dismissed-block-suggestions")
    val dismissedBlockSuggestions: List<BackupData.BackupBlockSuggestion> = emptyList(),
    @SerialName("schedule-rules")
    val scheduleRules: List<BackupData.BackupScheduleRule> = emptyList(),
) {
    @Serializable
    data class BackupSettings(
        @SerialName("screening-dialog-dismissed") val screeningDialogDismissed: Boolean = false,
        @SerialName("default-country-iso") val defaultCountryIso: String = "",
        @SerialName("block-unknown") val blockUnknown: Boolean = false,
        @SerialName("block-hidden") val blockHidden: Boolean = false,
        @SerialName("block-groups") val blockGroups: Boolean = false,
        @SerialName("blocked-group-ids") val blockedGroupIds: String = "",
        @SerialName("block-international") val blockInternational: Boolean = false,
        @SerialName("allowed-country-isos") val allowedCountryIsos: String = "",
        @SerialName("block-not-called") val blockNotCalled: Boolean = false,
        @SerialName("block-not-called-window-days") val notCalledWindowDays: Int? = null,
        @SerialName("block-rejected") val blockRejected: Boolean = false,
        @SerialName("block-rejected-window-days") val rejectedWindowDays: Int? = null,
        @SerialName("block-repeated") val blockRepeated: Boolean = false,
        @SerialName("block-repeated-count") val repeatedCallCount: Int? = null,
        @SerialName("block-repeated-window-minutes") val repeatedCallWindowMinutes: Int? = null,
        @SerialName("skip-call-log") val skipCallLog: Boolean = false,
        @SerialName("find-my-phone-enabled") val findMyPhoneEnabled: Boolean = false,
        @SerialName("find-my-phone-numbers") val findMyPhonePhoneNumbers: String = "",
        @SerialName("find-my-phone-call-count") val findMyPhoneCallCount: Int? = null,
        @SerialName("find-my-phone-window-minutes") val findMyPhoneWindowMinutes: Int? = null,
        @SerialName("find-my-phone-ringtone-uri") val findMyPhoneRingtoneUri: String = "",
        @SerialName("contacts-permission-requested") val contactsPermissionRequested: Boolean = false,
        @SerialName("calls-log-permission-requested") val callsLogPermissionRequested: Boolean = false,
        @SerialName("notifications-permission-requested") val notificationsPermissionRequested: Boolean = false,
        @SerialName("is-logging-to-file") val isLoggingToFile: Boolean? = null,
        @SerialName("last-active-tab") val lastActiveTab: String = "",
    )

    @Serializable
    data class BackupNumber(
        @SerialName("phone-number") val phoneNumber: String,
        val description: String = "",
        val type: Int,
        val action: Int,
        @SerialName("created-at") val createdAt: Long,
    )

    @Serializable
    data class BackupHistoryEntry(
        @SerialName("phone-number") val phoneNumber: String,
        val timestamp: Long,
        val reason: Int,
    )

    @Serializable
    data class BackupBlockSuggestion(
        @SerialName("phone-number") val phoneNumber: String,
        @SerialName("dismissed-at") val dismissedAt: Long,
    )

    @Serializable
    data class BackupScheduleRule(
        @SerialName("start-day") val startDay: Int,
        @SerialName("start-minute") val startMinute: Int,
        @SerialName("end-day") val endDay: Int,
        @SerialName("end-minute") val endMinute: Int,
    )
}
