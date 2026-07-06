package com.ryosoftware.calls_blocker.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ryosoftware.calls_blocker.data.BackupManager
import com.ryosoftware.calls_blocker.data.ContactGroup
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.fetchContactGroups
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import com.ryosoftware.calls_blocker.service.callsblocker.Logic
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.FindMyPhonePlayer
import com.ryosoftware.calls_blocker.service.callsblocker.PostServiceWorker
import com.ryosoftware.calls_blocker.service.callsblocker.REASON_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.TESTING_PURPOSES_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.TIME_PARAM
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface BackupEvent {
    data object Success : BackupEvent
    data object RestoreSuccess : BackupEvent
    data class Error(val message: String) : BackupEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val backupManager: BackupManager,
    @param:ApplicationContext private val context: Context,
    private val countryNameProvider: CountryNameProvider,
    private val scheduleRuleRepository: ScheduleRuleRepository,
) : ViewModel() {

    companion object {
        private const val ROLE_SERVICE = "role"
        private const val ROLE_CALL_SCREENING = "android.app.role.CALL_SCREENING"
    }

    val scheduleRules: StateFlow<List<ScheduleRule>> = scheduleRuleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScreeningEnabled = MutableStateFlow(false)
    val isScreeningEnabled: StateFlow<Boolean> = _isScreeningEnabled.asStateFlow()

    private val _backupEvent = MutableSharedFlow<BackupEvent>()
    val backupEvent: SharedFlow<BackupEvent> = _backupEvent.asSharedFlow()

    @Inject
    lateinit var callScreeningLogic: Logic

    @Inject
    lateinit var findMyPhonePlayer: FindMyPhonePlayer

    var isLoggingToFile by settingsManager::isLoggingToFile
    var blockUnknown by settingsManager::blockUnknown
    var blockHidden by settingsManager::blockHidden
    var blockAll by settingsManager::blockAll
    var blockInternational by settingsManager::blockInternational
    var allowedCountryIsos by settingsManager::allowedCountryIsos
    var contactsPermissionRequested by settingsManager::contactsPermissionRequested
    var callsLogPermissionRequested by settingsManager::callsLogPermissionRequested
    var notificationsPermissionRequested by settingsManager::notificationsPermissionRequested
    var skipCallLog by settingsManager::skipCallLog
    var defaultCountryIso by settingsManager::defaultCountryIso

    fun ensureDefaultCountryInAllowed() {
        val iso = settingsManager.defaultCountryIso
        if (iso.isNotEmpty()) {
            val allowed = settingsManager.allowedCountryIsos
            val isos = allowed.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (iso !in isos) {
                settingsManager.allowedCountryIsos = if (allowed.isEmpty()) iso else "$allowed,$iso"
            }
        }
    }

    fun checkScreeningStatus() {
        viewModelScope.launch {
            _isScreeningEnabled.value = isRoleHeld()
        }
    }

    fun createRequestRoleIntent(): Intent? {
        val roleManager = context.getSystemService(ROLE_SERVICE) as? android.app.role.RoleManager
        return roleManager?.createRequestRoleIntent(ROLE_CALL_SCREENING)
    }

    private fun isRoleHeld(): Boolean {
        val roleManager = context.getSystemService(ROLE_SERVICE) as? android.app.role.RoleManager
        return roleManager?.isRoleHeld(ROLE_CALL_SCREENING) == true
    }

    var blockGroups by settingsManager::blockGroups
    var blockedGroupIds by settingsManager::blockedGroupIds
    var blockRepeated by settingsManager::blockRepeated
    var repeatedCallCount by settingsManager::repeatedCallCount
    var repeatedCallWindowMinutes by settingsManager::repeatedCallWindowMinutes
    var blockNotCalled by settingsManager::blockNotCalled
    var notCalledWindowDays by settingsManager::notCalledWindowDays
    var blockRejected by settingsManager::blockRejected
    var rejectedWindowDays by settingsManager::rejectedWindowDays
    var findMyPhoneEnabled by settingsManager::findMyPhoneEnabled
    var findMyPhonePhoneNumbers by settingsManager::findMyPhonePhoneNumbers
    var findMyPhoneCallCount by settingsManager::findMyPhoneCallCount
    var findMyPhoneWindowMinutes by settingsManager::findMyPhoneWindowMinutes
    var findMyPhoneRingtoneUri by settingsManager::findMyPhoneRingtoneUri

    fun getContactGroups(): List<ContactGroup> = fetchContactGroups(context, context.contentResolver)

    fun getGroupName(group: ContactGroup): String = group.title

    fun getCountryName(country: Country): String =
        countryNameProvider.get(country)

    fun backup(uri: Uri) {
        viewModelScope.launch {
            backupManager.backup(uri)
                .onSuccess { _backupEvent.emit(BackupEvent.Success) }
                .onFailure { _backupEvent.emit(BackupEvent.Error(it.message ?: "Unknown error")) }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            backupManager.restore(uri)
                .onSuccess { _backupEvent.emit(BackupEvent.RestoreSuccess) }
                .onFailure { _backupEvent.emit(BackupEvent.Error(it.message ?: "Unknown error")) }
        }
    }

    fun addScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch { scheduleRuleRepository.add(rule) }
    }

    fun updateScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch { scheduleRuleRepository.update(rule) }
    }

    fun removeScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch { scheduleRuleRepository.remove(rule) }
    }

    fun getPhoneNumberBlockReason(phoneNumber: String): Reason? {
        val (normalizedPhoneNumber, reason) = callScreeningLogic.test(phoneNumber = phoneNumber, testingPurposes = true)

        val isAllowed = (reason in listOf(
            Reason.NONE,
            Reason.WHITELISTED_NUMBER,
            Reason.WHITELISTED_PREFIX
        ))

        return if (isAllowed) null else reason
    }

    fun testFindMyPhone() {
        val workerData = workDataOf(
            REASON_PARAM to Reason.FIND_MY_PHONE.code,
            TIME_PARAM to System.currentTimeMillis(),
            TESTING_PURPOSES_PARAM to true
        )

        val request = OneTimeWorkRequestBuilder<PostServiceWorker>()
            .setInputData(workerData)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
