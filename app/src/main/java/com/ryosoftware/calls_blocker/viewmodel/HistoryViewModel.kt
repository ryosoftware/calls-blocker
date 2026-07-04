package com.ryosoftware.calls_blocker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.repository.HistoryRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.service.callsblocker.Logic
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
) : ViewModel() {
    companion object {
        fun getReasonString(context: Context, reason: Reason): String =
            when (reason) {
                Reason.REASON_WHITELISTED_NUMBER -> context.getString(R.string.reason_whitelisted_number)
                Reason.REASON_WHITELISTED_PREFIX -> context.getString(R.string.reason_whitelisted_prefix)
                Reason.REASON_FIND_MY_PHONE -> context.getString(R.string.find_my_phone_activated_no_number)
                Reason.REASON_HIDDEN_NUMBER -> context.getString(R.string.reason_hidden)
                Reason.REASON_BLACKLISTED_NUMBER -> context.getString(R.string.reason_blacklisted_number)
                Reason.REASON_BLACKLISTED_PREFIX -> context.getString(R.string.reason_blacklisted_prefix)
                Reason.REASON_UNKNOWN_NUMBER -> context.getString(R.string.reason_unknown_number)
                Reason.REASON_GROUP -> context.getString(R.string.reason_group)
                Reason.REASON_INTERNATIONAL_NUMBER -> context.getString(R.string.reason_international)
                Reason.REASON_NOT_CALLED -> context.getString(R.string.reason_not_called)
                Reason.REASON_REJECTED_BEFORE -> context.getString(R.string.reason_rejected_before)
                Reason.REASON_REPEATED_CALL -> context.getString(R.string.reason_repeated_call)
                Reason.REASON_SCHEDULE -> context.getString(R.string.reason_schedule)
                Reason.REASON_NONE -> context.getString(R.string.reason_unknown)
            }
    }
    @Inject
    lateinit var callScreeningLogic: Logic

    val history: StateFlow<List<HistoryEntry>> = repo.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedPhoneNumbers: StateFlow<Set<String>> = numberDao.getPhoneNumbersByAction(Action.ACTION_BLOCK)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allowedPhoneNumbers: StateFlow<Set<String>> = numberDao.getPhoneNumbersByAction(Action.ACTION_ALLOW)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    fun removeEntries(ids: List<Long>) {
        viewModelScope.launch {
            _isDeleting.value = true
            repo.removeEntries(ids)
            _isDeleting.value = false
        }
    }

    fun removeEntry(entry: HistoryEntry, deleteAllFromNumber: Boolean, alsoRemoveFromNumbers: Boolean) {
        viewModelScope.launch {
            _isDeleting.value = true

            if (deleteAllFromNumber) {
                repo.removeByPhoneNumber(entry.phoneNumber)
            }
            else {
                repo.remove(entry)
            }

            if (alsoRemoveFromNumbers) {
                numberRepository.removeByPhoneNumber(entry.phoneNumber)
            }

            _isDeleting.value = false
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _isDeleting.value = true

            repo.clearAll()

            _isDeleting.value = false
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
                        writer.write("\"${entry.phoneNumber}\",$date,$reason\n")
                    }
                }
            }
        }
    }
}
