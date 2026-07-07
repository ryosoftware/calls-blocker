package com.ryosoftware.calls_blocker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.repository.HistoryRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: HistoryRepository,
    numberDao: NumberDao,
    private val numberRepository: NumberRepository,
    private val countryNameProvider: CountryNameProvider,
    private val settingsManager: SettingsManager,
) : ViewModel() {
    companion object {
        fun getReasonString(context: Context, reason: Reason): String =
            when (reason) {
                Reason.WHITELISTED_NUMBER -> context.getString(R.string.reason_whitelisted_number)
                Reason.WHITELISTED_PREFIX -> context.getString(R.string.reason_whitelisted_prefix)
                Reason.BLOCK_ALL -> context.getString(R.string.reason_block_all)
                Reason.HIDDEN_NUMBER -> context.getString(R.string.reason_hidden)
                Reason.BLACKLISTED_NUMBER -> context.getString(R.string.reason_blacklisted_number)
                Reason.BLACKLISTED_PREFIX -> context.getString(R.string.reason_blacklisted_prefix)
                Reason.NOT_A_CONTACT -> context.getString(R.string.reason_unknown_number)
                Reason.MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS -> context.getString(R.string.reason_group)
                Reason.INTERNATIONAL_NUMBER -> context.getString(R.string.reason_international)
                Reason.NOT_CALLED -> context.getString(R.string.reason_not_called)
                Reason.REJECTED_BEFORE -> context.getString(R.string.reason_rejected_before)
                Reason.REPEATED_CALL -> context.getString(R.string.reason_repeated_call)
                Reason.SCHEDULE -> context.getString(R.string.reason_schedule)
                Reason.NONE -> context.getString(R.string.reason_unknown)
                Reason.FIND_MY_PHONE -> context.getString(R.string.reason_find_my_phone)
                Reason.FIND_MY_PHONE_CANCELLED -> context.getString(R.string.reason_find_my_phone_cancelled)
            }
    }

    var blockHidden by settingsManager::blockHidden

    val history: StateFlow<List<HistoryEntry>> = repo.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedPhoneNumbers: StateFlow<Set<String>> = numberDao.getPhoneNumbers(Action.BLOCK)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allowedPhoneNumbers: StateFlow<Set<String>> = numberDao.getPhoneNumbers(Action.ALLOW)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    fun removeEntries(ids: List<Long>) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                repo.removeEntries(ids)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun removeEntry(entry: HistoryEntry, deleteAllFromNumber: Boolean, alsoRemoveFromNumbers: Boolean) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                if (deleteAllFromNumber) {
                    repo.removeByPhoneNumber(entry.phoneNumber)
                }
                else {
                    repo.remove(entry)
                }

                if (alsoRemoveFromNumbers) {
                    numberRepository.removeByPhoneNumber(entry.phoneNumber)
                }
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun blockNumber(phoneNumber: String) {
        viewModelScope.launch {
            numberRepository.add(phoneNumber, "", Action.BLOCK, Type.EXACT_COINCIDENCE)
        }
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            numberRepository.removeByPhoneNumber(phoneNumber)
        }
    }

    fun allowNumber(phoneNumber: String) {
        viewModelScope.launch {
            numberRepository.add(phoneNumber, "", Action.ALLOW, Type.EXACT_COINCIDENCE)
        }
    }

    fun unallowNumber(phoneNumber: String) {
        viewModelScope.launch {
            numberRepository.removeByPhoneNumber(phoneNumber)
        }
    }

    fun getCountryName(country: Country): String =
        countryNameProvider.get(country)

    suspend fun exportCsv(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val entries = repo.allEntries.first()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    val headers = listOf(context.getString(R.string.csv_column_phone_number), context.getString(R.string.csv_column_date), context.getString(R.string.csv_column_reason))
                    writer.write(headers.joinToString(",") + "\n")
                    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
                    for (entry in entries) {
                        val date = dateFormat.format(Date(entry.timeStamp))
                        val reason = getReasonString(context, entry.reason)
                        val escapedPhone = entry.phoneNumber.replace("\"", "\"\"")
                        writer.write("\"$escapedPhone\",$date,$reason\n")
                    }
                }
            }
        }
    }
}
