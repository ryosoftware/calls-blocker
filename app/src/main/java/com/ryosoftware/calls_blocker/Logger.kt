package com.ryosoftware.calls_blocker

import android.content.Context
import android.util.Log
import com.ryosoftware.calls_blocker.data.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

const val PHONE_NUMBER_REF = "PHONE_REF"
const val NORMALIZED_PHONE_NUMBER_REF = "PHONE_NREF"
const val TIME_REF = "TIME_REF"

interface Logger {
    val logFileTime: StateFlow<Long>

    fun log(message: String, phoneNumber: String="", normalizedPhoneNumber: String="", time: Long=0L)
    fun getLogFile(): File
    suspend fun getLogFileContents(maskPhoneNumbers: Boolean): List<String>?
    fun startInMemory(): Boolean
    fun resumeToFile()
}

@Singleton
class FileLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
) : Logger {
    private val _logFileTime = MutableStateFlow(0L)

    override val logFileTime: StateFlow<Long> = _logFileTime.asStateFlow()

    companion object {
        private const val LOG_FILE = "debug_log.txt"
    }

    @Serializable
    data class LogEntry(
        val timestamp: Long,
        val phoneNumber: String,
        val normalizedPhoneNumber: String,
        val time: Long,
        val message: String
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val buffer = mutableListOf<String>()
    private var inMemory = false

    override fun startInMemory(): Boolean {
        val currentlyInMemory = inMemory
        inMemory = true
        return !currentlyInMemory
    }

    override fun resumeToFile() {
        inMemory = false
        try {
            if (buffer.isNotEmpty() && settingsManager.isLoggingToFile) {
                val file = getLogFile()
                file.appendText(buffer.joinToString("\n") + "\n")
                _logFileTime.value = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(BuildConfig.TAG, e.toString())
        } finally {
            buffer.clear()
        }
    }

    private fun toPrintableString(message: String, maskPhoneNumbers: Boolean, phoneNumber: String, normalizedPhoneNumber: String, time: Long, timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)): String =
        message
            .replace(PHONE_NUMBER_REF, if (maskPhoneNumbers) maskNumbers(phoneNumber) else phoneNumber)
            .replace(NORMALIZED_PHONE_NUMBER_REF, if (maskPhoneNumbers) maskNumbers(normalizedPhoneNumber) else normalizedPhoneNumber)
            .replace(TIME_REF, timeFormat.format(time))

    override fun log(message: String, phoneNumber: String, normalizedPhoneNumber: String, time: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(BuildConfig.TAG, toPrintableString(message, false, phoneNumber, normalizedPhoneNumber, time))
        }

        if (settingsManager.isLoggingToFile) {
            if (inMemory) {
                val logEntry = LogEntry(timestamp = System.currentTimeMillis(), phoneNumber = phoneNumber, normalizedPhoneNumber = normalizedPhoneNumber, time=time, message = message)
                val line = json.encodeToString(LogEntry.serializer(), logEntry)
                synchronized(buffer) {
                    buffer.add(line)
                }
            } else {
                writeLogToFile(message, phoneNumber, normalizedPhoneNumber, time)
            }
        }
    }

    private fun writeLogToFile(message: String, phoneNumber: String, normalizedPhoneNumber: String, time: Long) {
        try {
            val file = getLogFile()
            val logEntry = LogEntry(timestamp = System.currentTimeMillis(), phoneNumber = phoneNumber, normalizedPhoneNumber = normalizedPhoneNumber, time=time, message = message)
            val line = json.encodeToString(LogEntry.serializer(), logEntry)

            file.appendText("$line\n")

            _logFileTime.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(BuildConfig.TAG, e.toString())
        }
    }

    override fun getLogFile(): File = File(context.filesDir, LOG_FILE)

    private fun maskNumbers(value: String): String {
        return value.replace(Regex("\\d"), "*")
    }

    override suspend fun getLogFileContents(maskPhoneNumbers: Boolean): List<String>? {
        return withContext(Dispatchers.IO) {
            try {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

                getLogFile().useLines { lines ->
                    lines
                        .filter { it.isNotBlank() }
                        .mapNotNull { line ->
                            runCatching {
                                json.decodeFromString<LogEntry>(line)
                            }.getOrNull()
                        }
                        .map { entry ->
                            val date = dateFormat.format(Date(entry.timestamp))
                            val message = toPrintableString(entry.message, maskPhoneNumbers, entry.phoneNumber, entry.normalizedPhoneNumber, entry.time, timeFormat)

                            "$date $message"
                        }
                        .toList()
                }
            } catch (e: Exception) {
                Log.e(BuildConfig.TAG, e.toString())
                null
            }
        }
    }
}
