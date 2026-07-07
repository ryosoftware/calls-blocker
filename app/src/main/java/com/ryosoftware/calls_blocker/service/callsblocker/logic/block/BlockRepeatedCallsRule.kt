package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import android.content.Context
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.CallsLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class BlockRepeatedCallsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val callsLogHelper: CallsLogHelper
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockRepeated = settingsManager.blockRepeated

        if (blockRepeated) {
            val windowMinutes = settingsManager.repeatedCallWindowMinutes
            val recentCalls = callsLogHelper.getRecentCallsCount(context, normalizedPhoneNumber, phoneNumber, windowMinutes, normalizeToE164) + 1

            if (recentCalls >= settingsManager.repeatedCallCount) {
                return Reason.REPEATED_CALL
            }
        }

        return Reason.NONE
    }
}