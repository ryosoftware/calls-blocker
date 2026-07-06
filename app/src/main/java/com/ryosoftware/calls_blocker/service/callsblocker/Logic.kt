package com.ryosoftware.calls_blocker.service.callsblocker

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telephony.TelephonyManager
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class Logic @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val numberRepository: NumberRepository,
    private val scheduleRuleRepository: ScheduleRuleRepository,
    private val logger: Logger,
) {
    companion object {
        private const val PRIVATE_NUMBER = "PRIVATE"
        private const val UNKNOWN_NUMBER = "UNKNOWN"

        private const val NO_NUMBER = "+"
    }

    private fun normalizeToE164(phoneNumber: String?, subscriptionId: Int?): String {
        if (isHiddenNumber(phoneNumber)) return ""

        requireNotNull(phoneNumber)

        return runCatching {
            logger.log("Received a call from $PHONE_NUMBER_REF and trying to normalize phone number", phoneNumber = phoneNumber)

            val possibleCountries = mutableSetOf<String?>()

            subscriptionId
                ?.let {
                    context.getSystemService(TelephonyManager::class.java)
                        .createForSubscriptionId(it)
                        .networkCountryIso
                }
                ?.uppercase()
                ?.ifEmpty { null }
                ?.let(possibleCountries::add)

            possibleCountries.addAll(PhoneUtils.getNetworkCountriesIso(context).orEmpty())

            possibleCountries.add(settingsManager.defaultCountryIso.ifEmpty { null })

            val normalizeResult = possibleCountries
                .asSequence()
                .map { country ->
                    PhoneUtils.normalizeToE164OrNull(phoneNumber = phoneNumber, networkCountryIso = country, logger = logger)
                }
                .firstOrNull { it.normalizedPhoneNumber != null }

            return normalizeResult?.normalizedPhoneNumber ?: phoneNumber
        }.getOrDefault(phoneNumber)
    }

    private fun normalizeToE164(phoneNumber: String?): String {
        if (isHiddenNumber(phoneNumber)) return ""

        requireNotNull(phoneNumber)

        val possibleCountries = mutableSetOf<String?>()

        possibleCountries.addAll(PhoneUtils.getNetworkCountriesIso(context).orEmpty())

        possibleCountries.add(settingsManager.defaultCountryIso.ifEmpty { null })

        val normalizeResult = possibleCountries
            .asSequence()
            .map { country ->
                PhoneUtils.normalizeToE164OrNull(phoneNumber = phoneNumber, networkCountryIso = country, logger = logger)
            }
            .firstOrNull { it.normalizedPhoneNumber != null }

        return normalizeResult?.normalizedPhoneNumber ?: phoneNumber
    }

    private fun isHiddenNumber(phoneNumber: String?): Boolean =
        phoneNumber.isNullOrBlank() || phoneNumber == PRIVATE_NUMBER || phoneNumber == UNKNOWN_NUMBER || phoneNumber == NO_NUMBER

    private fun isUnknownNumber(normalizedPhoneNumber: String): Boolean {
        if (!context.hasReadContactsPermission()) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedPhoneNumber))

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        context.contentResolver.query(uri, projection, null, null, null)?.use {
            return it.count == 0
        }

        return true
    }

    private fun isFromBlockedGroup(normalizedPhoneNumber: String): Boolean {
        if (!context.hasReadContactsPermission()) return false

        val blockedIds = settingsManager.blockedGroupIds.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        if (blockedIds.isEmpty()) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedPhoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.CONTACT_ID)
        val contactId =
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return false

        val groupProjection = arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        val groupSelection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val groupArgs = arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)

        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                groupProjection,
                groupSelection,
                groupArgs,
                null
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .any { cursor.getLong(0) in blockedIds }
            } ?: false
        }.getOrElse { false }
    }

    private fun isInCallsLog(normalizedPhoneNumber: String, selection: String?, selectionArgs: Array<String>?, defaultValue: Boolean): Boolean {
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(CallLog.Calls.NUMBER)

        return runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val cache = HashMap<String, Boolean>()
                    while (cursor.moveToNext()) {
                        val rawNumber = cursor.getString(0) ?: continue
                        val match = cache.getOrPut(rawNumber) {
                            normalizedPhoneNumber == normalizeToE164(rawNumber)
                        }
                        if (match) return@use true
                    }
                    false
                } ?: defaultValue
        }.getOrElse { defaultValue }
    }

    private fun countFromCallsLog(normalizedPhoneNumber: String, selection: String?, selectionArgs: Array<String>?): Int {
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(CallLog.Calls.NUMBER)

        return runCatching {
            var count = 0
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val cache = HashMap<String, Boolean>()
                    while (cursor.moveToNext()) {
                        val rawNumber = cursor.getString(0) ?: continue
                        val match = cache.getOrPut(rawNumber) {
                            normalizedPhoneNumber == normalizeToE164(rawNumber)
                        }
                        if (match) count++
                    }
                }
            count
        }.getOrElse { 0 }
    }

    private fun getRecentCallsCount(normalizedPhoneNumber: String, phoneNumber: String, windowMinutes: Int): Int {
        if (!context.hasReadCallLogPermission()) return 0

        val since = System.currentTimeMillis() - (windowMinutes * 60_000L)
        val lastDigits = normalizedPhoneNumber.commonSuffixWith(phoneNumber)

        val types = listOf(
            CallLog.Calls.INCOMING_TYPE,
            CallLog.Calls.MISSED_TYPE,
            CallLog.Calls.BLOCKED_TYPE,
            CallLog.Calls.REJECTED_TYPE
        )

        val selection =
            "${CallLog.Calls.DATE} > ? AND " +
            "${CallLog.Calls.NUMBER} LIKE ? AND " +
            "${CallLog.Calls.TYPE} IN (${types.joinToString(",") { "?" }})"

        val selectionArgs = arrayOf(
            since.toString(),
            "%$lastDigits",
            *types.map { it.toString() }.toTypedArray()
        )

        return countFromCallsLog(normalizedPhoneNumber, selection, selectionArgs)
    }

    private fun hasOutgoingCallToNumber(normalizedPhoneNumber: String, phoneNumber: String, windowDays: Int): Boolean {
        if (!context.hasReadCallLogPermission()) return true

        val since = System.currentTimeMillis() - (windowDays * 24L * 60 * 60 * 1000)
        val lastDigits = normalizedPhoneNumber.commonSuffixWith(phoneNumber)

        val selection =
            "${CallLog.Calls.DATE} > ? AND " +
            "${CallLog.Calls.NUMBER} LIKE ? AND " +
            "${CallLog.Calls.TYPE} = ?"

        val selectionArgs = arrayOf(
            since.toString(),
            "%$lastDigits",
            CallLog.Calls.OUTGOING_TYPE.toString()
        )

        return isInCallsLog(normalizedPhoneNumber, selection, selectionArgs, true)
    }

    private fun hasRejectedCallBefore(normalizedPhoneNumber: String, phoneNumber: String, windowDays: Int): Boolean {
        if (!context.hasReadCallLogPermission()) return false

        val since = System.currentTimeMillis() - (windowDays * 24L * 60 * 60 * 1000)
        val lastDigits = normalizedPhoneNumber.commonSuffixWith(phoneNumber)

        val selection =
            "${CallLog.Calls.DATE} > ? AND " +
                    "${CallLog.Calls.NUMBER} LIKE ? AND " +
                    "${CallLog.Calls.TYPE} = ?"

        val selectionArgs = arrayOf(
            since.toString(),
            "%$lastDigits",
            CallLog.Calls.REJECTED_TYPE.toString()
        )

        return isInCallsLog(normalizedPhoneNumber, selection, selectionArgs, false)
    }

    private fun _test(normalizedPhoneNumber: String?, phoneNumber: String?, testingPurposes: Boolean): Reason {
        val isHiddenNumber = isHiddenNumber(normalizedPhoneNumber)

        // First we check if phone is hidden

        if (isHiddenNumber) {
            // For hidden numbers I reject if hidden or unknown numbers are disallowed

            val blockHidden = settingsManager.blockHidden
            val blockUnknown = settingsManager.blockUnknown

            if (blockHidden || blockUnknown) {
                return Reason.HIDDEN_NUMBER
            }

            return Reason.NONE
        }

        requireNotNull(normalizedPhoneNumber)
        requireNotNull(phoneNumber)

        // "Find my phone" has higher priority (even more than white listed)

        if (!testingPurposes) {
            val findMyPhoneEnabled = settingsManager.findMyPhoneEnabled
            if (findMyPhoneEnabled) {
                val trustedNumbers = settingsManager.findMyPhonePhoneNumbers.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (normalizedPhoneNumber in trustedNumbers) {
                    if (FindMyPhonePlayer.isRunning) {
                        return Reason.FIND_MY_PHONE_CANCELLED
                    }
                    val windowMinutes = settingsManager.findMyPhoneWindowMinutes
                    val recentCalls = getRecentCallsCount(normalizedPhoneNumber, phoneNumber, windowMinutes) + 1
                    if (recentCalls >= settingsManager.findMyPhoneCallCount) {
                        return Reason.FIND_MY_PHONE
                    }
                }
            }
        }

        // After check "find my phone", we check if phone number is whitelisted

        if (numberRepository.isIncomingAllowedExact(normalizedPhoneNumber)) {
            return Reason.WHITELISTED_NUMBER
        }

        if (numberRepository.isIncomingAllowedByPrefix(normalizedPhoneNumber)) {
            return Reason.WHITELISTED_PREFIX
        }

        // Check if block all is enabled

        val blockAll = settingsManager.blockAll
        if (blockAll) {
            return Reason.BLOCK_ALL
        }

        // Block blacklisted numbers

        val blockExactNumber = numberRepository.isIncomingBlockedExact(normalizedPhoneNumber)
        if (blockExactNumber) {
            return Reason.BLACKLISTED_NUMBER
        }

        val blockPrefixNumber = numberRepository.isIncomingBlockedByPrefix(normalizedPhoneNumber)
        if (blockPrefixNumber) {
            return Reason.BLACKLISTED_PREFIX
        }

        // Block calls during scheduled periods

        if (scheduleRuleRepository.isInScheduleBlock()) {
            return Reason.SCHEDULE
        }

        // Block Unknown numbers (numbers that are not in contacts)

        val blockUnknown = settingsManager.blockUnknown
        if (blockUnknown) {
            val isUnknownNumber = isUnknownNumber(normalizedPhoneNumber)

            if (isUnknownNumber) {
                return Reason.NOT_A_CONTACT
            }
        }

        // Block phone numbers that belongs to blocked groups

        val blockGroups = settingsManager.blockGroups
        if (blockGroups) {
            val isFromBlockedGroup = isFromBlockedGroup(normalizedPhoneNumber)

            if (isFromBlockedGroup) {
                return Reason.MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS
            }
        }

        // Block international calls

        val blockInternational = settingsManager.blockInternational
        if (blockInternational) {
            val isInternational = normalizedPhoneNumber.startsWith("+")
            if (isInternational) {
                val allowedCountriesPhoneCodes = settingsManager.getAllowedCountryPhoneCodes()
                val isBlockedByInternationalRule = allowedCountriesPhoneCodes
                    .none {
                        it.isNotEmpty() && normalizedPhoneNumber.drop(1).startsWith(it)
                    }

                if (isBlockedByInternationalRule) {
                    return Reason.INTERNATIONAL_NUMBER
                }
            }
        }

        // Block phone numbers not dialed

        val blockNotCalled = settingsManager.blockNotCalled
        if (blockNotCalled) {
            val windowDays = settingsManager.notCalledWindowDays
            val hasCalledBefore = hasOutgoingCallToNumber(normalizedPhoneNumber, phoneNumber, windowDays)

            if (!hasCalledBefore) {
                return Reason.NOT_CALLED
            }
        }

        // Block phone numbers rejected before

        val blockRejected = settingsManager.blockRejected
        if (blockRejected) {
            val windowDays = settingsManager.rejectedWindowDays
            val hasRejected = hasRejectedCallBefore(normalizedPhoneNumber, phoneNumber, windowDays)

            if (hasRejected) {
                return Reason.REJECTED_BEFORE
            }
        }

        // Block repeated calls

        val blockRepeated = settingsManager.blockRepeated
        if (blockRepeated) {
            val windowMinutes = settingsManager.repeatedCallWindowMinutes
            val recentCalls = getRecentCallsCount(normalizedPhoneNumber, phoneNumber, windowMinutes) + 1

            if (recentCalls >= settingsManager.repeatedCallCount) {
                return Reason.REPEATED_CALL
            }
        }

        // Call is not blocked

        return Reason.NONE
    }

    fun normalizePhoneNumber(phoneNumber: String?, subscriptionId: Int? = null): String? {
        val isHiddenNumber = isHiddenNumber(phoneNumber)

        val normalizedPhoneNumber = when {
            isHiddenNumber -> ""
            else -> normalizeToE164(phoneNumber, subscriptionId)
        }

        return normalizedPhoneNumber
    }

    fun normalizePhoneNumber(callDetails: Call.Details): String? =
        normalizePhoneNumber(callDetails.handle?.schemeSpecificPart ?: "", callDetails.accountHandle?.id?.toIntOrNull())

    fun test(phoneNumber: String?, subscriptionId: Int? = null, testingPurposes: Boolean = false): Pair<String?, Reason> {
         val startTime = SystemClock.elapsedRealtime()

         val normalizedPhoneNumber = normalizePhoneNumber(phoneNumber, subscriptionId)
         val reason = _test(normalizedPhoneNumber, phoneNumber, testingPurposes)

         val processTime = SystemClock.elapsedRealtime() - startTime
         logger.log("Call filtering process finished in ${processTime}ms; post call screening worker will be initialized")

         return normalizedPhoneNumber to reason
     }

    fun test(callDetails: Call.Details): Pair<String?, Reason> =
        test(callDetails.handle?.schemeSpecificPart ?: "", callDetails.accountHandle?.id?.toIntOrNull(), false)
}