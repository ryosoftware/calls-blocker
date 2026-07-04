package com.ryosoftware.calls_blocker.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.R
import kotlin.reflect.KProperty

class SettingsManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "calls_blocker_settings"
        private const val KEY_DEFAULT_COUNTRY_ISO = "default-country-iso"
        private const val KEY_BLOCK_UNKNOWN = "block-unknown"
        private const val KEY_BLOCK_HIDDEN = "block-hidden"
        private const val KEY_BLOCK_GROUPS = "block-groups"
        private const val KEY_BLOCKED_GROUP_IDS = "blocked-group-ids"
        private const val KEY_BLOCK_INTERNATIONAL = "block-international"
        private const val KEY_ALLOWED_COUNTRY_ISOS = "allowed-country-isos"
        private const val KEY_BLOCK_NOT_CALLED = "block-not-called"
        private const val KEY_BLOCK_NOT_CALLED_WINDOW_DAYS = "block-not-called-window-days"
        private const val KEY_BLOCK_REJECTED = "block-rejected"
        private const val KEY_BLOCK_REJECTED_WINDOW_DAYS = "block-rejected-window-days"
        private const val KEY_BLOCK_REPEATED = "block-repeated"
        private const val KEY_BLOCK_REPEATED_CALL_COUNT = "block-repeated-count"
        private const val KEY_BLOCK_REPEATED_CALL_WINDOW_MINUTES = "block-repeated-window-minutes"
        private const val KEY_SKIP_CALL_LOG = "skip-call-log"
        private const val KEY_CONTACTS_PERMISSION_REQUESTED = "contacts-permission-requested"
        private const val KEY_CALLS_LOG_PERMISSION_REQUESTED = "calls-log-permission-requested"
        private const val KEY_NOTIFICATIONS_PERMISSION_REQUESTED = "notifications-permission-requested"
        private const val KEY_FIND_MY_PHONE_ENABLED = "find-my-phone-enabled"
        private const val KEY_FIND_MY_PHONE_NUMBERS = "find-my-phone-numbers"
        private const val KEY_FIND_MY_PHONE_CALL_COUNT = "find-my-phone-call-count"
        private const val KEY_FIND_MY_PHONE_WINDOW_MINUTES = "find-my-phone-window-minutes"
        private const val KEY_FIND_MY_PHONE_RINGTONE_URI = "find-my-phone-ringtone-uri"
        private const val KEY_LOGGING_TO_FILE_ENABLED = "logging-to-file"
        private const val KEY_DIALOG_DISMISSED = "screening-dialog-dismissed"
        private const val KEY_LAST_ACTIVE_TAB = "last-active-tab"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private class BooleanDelegate(
        private val prefs: SharedPreferences,
        private val key: String,
        private val default: Boolean
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = prefs.getBoolean(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit { putBoolean(key, value) }
        }
    }

    private class StringDelegate(
        private val prefs: SharedPreferences,
        private val key: String,
        private val default: String
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String = prefs.getString(key, default) ?: default
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit { putString(key, value) }
        }
    }

    private class IntDelegate(
        private val prefs: SharedPreferences,
        private val key: String,
        private val default: Int
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = prefs.getInt(key, default)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            prefs.edit { putInt(key, value) }
        }
    }

    private fun booleanPref(key: String, default: Boolean) = BooleanDelegate(prefs, key, default)
    private fun stringPref(key: String, default: String = "") = StringDelegate(prefs, key, default)
    private fun intPref(key: String, default: Int) = IntDelegate(prefs, key, default)

    var isLoggingToFile by booleanPref(KEY_LOGGING_TO_FILE_ENABLED, BuildConfig.DEBUG)
    var blockUnknown by booleanPref(KEY_BLOCK_UNKNOWN, false)
    var blockHidden by booleanPref(KEY_BLOCK_HIDDEN, false)
    var screeningDialogDismissed by booleanPref(KEY_DIALOG_DISMISSED, false)
    var blockInternational by booleanPref(KEY_BLOCK_INTERNATIONAL, false)
    var allowedCountryIsos by stringPref(KEY_ALLOWED_COUNTRY_ISOS)

    fun getAllowedCountryPhoneCodes(): Set<String> {
        val isos = allowedCountryIsos.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return phoneCodesForIso(isos)
    }

    fun getAllowedCountries(): List<Country> {
        val isos = allowedCountryIsos.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return countries.filter { it.iso in isos }
    }

    fun getAllCountries(): List<Country> = countries

    fun getCountryByIso(iso: String): Country? =
        countries.firstOrNull { it.iso == iso }

    fun isScreeningActive(): Boolean {
        val roleManager = context.applicationContext.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun createRequestRoleIntent(): Intent? {
        val roleManager = context.applicationContext.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    var lastActiveTab by stringPref(KEY_LAST_ACTIVE_TAB)
    var contactsPermissionRequested by booleanPref(KEY_CONTACTS_PERMISSION_REQUESTED, false)
    var callsLogPermissionRequested by booleanPref(KEY_CALLS_LOG_PERMISSION_REQUESTED, false)
    var notificationsPermissionRequested by booleanPref(KEY_NOTIFICATIONS_PERMISSION_REQUESTED, false)
    var skipCallLog by booleanPref(KEY_SKIP_CALL_LOG, false)
    var defaultCountryIso by stringPref(KEY_DEFAULT_COUNTRY_ISO)
    var findMyPhoneEnabled by booleanPref(KEY_FIND_MY_PHONE_ENABLED, false)
    var findMyPhonePhoneNumbers by stringPref(KEY_FIND_MY_PHONE_NUMBERS)
    var findMyPhoneCallCount by intPref(KEY_FIND_MY_PHONE_CALL_COUNT, context.resources.getInteger(R.integer.find_my_phone_call_count_default))
    var findMyPhoneWindowMinutes by intPref(KEY_FIND_MY_PHONE_WINDOW_MINUTES, context.resources.getInteger(R.integer.find_my_phone_window_minutes_default))
    var findMyPhoneRingtoneUri by stringPref(KEY_FIND_MY_PHONE_RINGTONE_URI)
    var blockGroups by booleanPref(KEY_BLOCK_GROUPS, false)
    var blockedGroupIds by stringPref(KEY_BLOCKED_GROUP_IDS)
    var blockRepeated by booleanPref(KEY_BLOCK_REPEATED, false)
    var repeatedCallCount by intPref(KEY_BLOCK_REPEATED_CALL_COUNT, context.resources.getInteger(R.integer.repeated_call_count_default))
    var repeatedCallWindowMinutes by intPref(KEY_BLOCK_REPEATED_CALL_WINDOW_MINUTES, context.resources.getInteger(R.integer.repeated_call_window_minutes_default))
    var blockNotCalled by booleanPref(KEY_BLOCK_NOT_CALLED, false)
    var notCalledWindowDays by intPref(KEY_BLOCK_NOT_CALLED_WINDOW_DAYS, context.resources.getInteger(R.integer.not_called_window_days_default))
    var blockRejected by booleanPref(KEY_BLOCK_REJECTED, false)
    var rejectedWindowDays by intPref(KEY_BLOCK_REJECTED_WINDOW_DAYS, context.resources.getInteger(R.integer.rejected_window_days_default))
}
