package com.ryosoftware.calls_blocker.service.callsblocker.logic

import android.content.Context
import android.provider.CallLog
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import javax.inject.Inject

class CallsLogHelper @Inject constructor() {
    fun isInCallsLog(context: Context, normalizedPhoneNumber: String, selection: String?, selectionArgs: Array<String>?, defaultValue: Boolean, normalizeToE164: (String?) -> String): Boolean {
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
        }.getOrDefault(defaultValue)
    }

    fun countFromCallsLog(context: Context, normalizedPhoneNumber: String, selection: String?, selectionArgs: Array<String>?, normalizeToE164: (String?) -> String): Int {
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
        }.getOrDefault(0)
    }

    fun getRecentCallsCount(context: Context, normalizedPhoneNumber: String, phoneNumber: String, windowMinutes: Int, normalizeToE164: (String?) -> String): Int {
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

        return countFromCallsLog(context, normalizedPhoneNumber, selection, selectionArgs, normalizeToE164)
    }

    fun hasOutgoingCallToNumber(context: Context, normalizedPhoneNumber: String, phoneNumber: String, windowDays: Int, normalizeToE164: (String?) -> String): Boolean {
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

        return isInCallsLog(context, normalizedPhoneNumber, selection, selectionArgs, true, normalizeToE164)
    }

}