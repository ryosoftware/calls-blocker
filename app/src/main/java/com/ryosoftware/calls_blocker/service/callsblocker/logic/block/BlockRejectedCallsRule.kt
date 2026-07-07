package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import android.content.Context
import android.provider.CallLog
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.CallsLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class BlockRejectedCallsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val callsLogHelper: CallsLogHelper
): AbstractBlockRule {
    private fun hasRejectedCallBefore(normalizedPhoneNumber: String, phoneNumber: String, windowDays: Int, normalizeToE164: (String?) -> String): Boolean {
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

        return callsLogHelper.isInCallsLog(context, normalizedPhoneNumber, selection, selectionArgs, false, normalizeToE164)
    }

    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockRejected = settingsManager.blockRejected

        if (blockRejected) {
            val windowDays = settingsManager.rejectedWindowDays
            val hasRejected = hasRejectedCallBefore(normalizedPhoneNumber, phoneNumber, windowDays, normalizeToE164)

            if (hasRejected) {
                return Reason.REJECTED_BEFORE
            }
        }

        return Reason.NONE
    }
}