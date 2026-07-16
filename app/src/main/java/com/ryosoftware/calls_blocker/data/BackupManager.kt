package com.ryosoftware.calls_blocker.data

import android.content.Context
import android.net.Uri
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.BlockSuggestionDao
import com.ryosoftware.calls_blocker.data.db.Direction
import com.ryosoftware.calls_blocker.data.db.BlockSuggestion
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.HistoryDao
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.db.NumberType
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.data.db.ScheduleRuleDao
import com.ryosoftware.calls_blocker.data.db.Type
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.floatOrNull
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
        val settingsMap = settingsManager.exportPrefs().mapNotNull { (key, value) ->
            val element = when (value) {
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                else -> return@mapNotNull null
            }
            key to element
        }.toMap()
        val backupData = BackupData(
            settings = settingsMap,
            numbers = numberDao.getAllList().map { number ->
                BackupData.BackupNumber(
                    phoneNumber = number.phoneNumber,
                    description = number.description,
                    action = number.action.code,
                    type = number.type.code,
                    createdAt = number.createdAt,
                )
            },
            history = historyDao.getAllList().map { entry ->
                BackupData.BackupHistoryEntry(
                    phoneNumber = entry.phoneNumber,
                    type = entry.type.code,
                    timestamp = entry.timeStamp,
                    direction = entry.direction.code,
                    reason = entry.reason.code,
                    flags = entry.flags,
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

        val restoredPrefs = mutableMapOf<String, Any?>()
        for ((key, element) in backupData.settings) {
            if (element !is JsonPrimitive) continue
            if (element.isString) { restoredPrefs[key] = element.content; continue }
            val bool = element.booleanOrNull
            if (bool != null) { restoredPrefs[key] = bool; continue }
            val int = element.intOrNull
            if (int != null) { restoredPrefs[key] = int; continue }
            val long = element.longOrNull
            if (long != null) { restoredPrefs[key] = long; continue }
            val float = element.floatOrNull
            if (float != null) { restoredPrefs[key] = float; continue }
        }
        settingsManager.importPrefs(restoredPrefs)

        numberDao.clearAll()
        numberDao.insertAll(backupData.numbers.map { numberEntry ->
            Number(
                phoneNumber = numberEntry.phoneNumber,
                description = numberEntry.description,
                action = Action.fromCode(numberEntry.action),
                type = Type.fromCode(numberEntry.type),
                createdAt = numberEntry.createdAt,
            )
        })

        historyDao.clearAll()
        historyDao.insertAll(backupData.history.map { historyEntry ->
            HistoryEntry(
                phoneNumber = historyEntry.phoneNumber,
                type = NumberType.fromCode(historyEntry.type),
                timeStamp = historyEntry.timestamp,
                direction = Direction.fromCode(historyEntry.direction),
                reason = Reason.fromCode(historyEntry.reason),
                flags = historyEntry.flags,
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
    val settings: Map<String, JsonElement> = emptyMap(),
    val numbers: List<BackupData.BackupNumber> = emptyList(),
    val history: List<BackupData.BackupHistoryEntry> = emptyList(),
    @SerialName("dismissed-block-suggestions")
    val dismissedBlockSuggestions: List<BackupData.BackupBlockSuggestion> = emptyList(),
    @SerialName("schedule-rules")
    val scheduleRules: List<BackupData.BackupScheduleRule> = emptyList(),
) {
    @Serializable
    data class BackupNumber(
        @SerialName("phone-number") val phoneNumber: String,
        val description: String = "",
        val action: Int = Action.BLOCK.code,
        val type: Int = Type.EXACT_COINCIDENCE.code,
        @SerialName("created-at") val createdAt: Long,
    )

    @Serializable
    data class BackupHistoryEntry(
        @SerialName("phone-number") val phoneNumber: String,
        val type: Int = NumberType.UNKNOWN.code,
        val timestamp: Long,
        val direction: Int = Direction.INCOMING.code,
        val reason: Int,
        val flags: Int = 0,
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
