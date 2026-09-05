package com.ryosoftware.calls_blocker.service.callsblocker.logic.allow

import android.content.Context
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.CallsLogHelper
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractSecondLevelAllowRule
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class AllowRepeatedCallsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val callsLogHelper: CallsLogHelper
): AbstractSecondLevelAllowRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val allowRepeated = settingsManager.allowRepeated

        if (allowRepeated) {
            val windowMinutes = settingsManager.allowRepeatedCallWindowMinutes
            val recentCalls = callsLogHelper.getRecentCallsCount(context, normalizedPhoneNumber, phoneNumber, windowMinutes, normalizeToE164) + 1

            if (recentCalls >= settingsManager.allowRepeatedCallCount) {
                return Reason.ALLOWED_REPEATED_CALL
            }
        }

        return Reason.NONE
    }
}
