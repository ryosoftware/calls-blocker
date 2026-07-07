package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import android.content.Context
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.FindMyPhonePlayer
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.CallsLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class BlockByFindMyPhoneRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val callsLogHelper: CallsLogHelper
): AbstractRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
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
                val recentCalls = callsLogHelper.getRecentCallsCount(context, normalizedPhoneNumber, phoneNumber, windowMinutes, normalizeToE164) + 1

                if (recentCalls >= settingsManager.findMyPhoneCallCount) {
                    return Reason.FIND_MY_PHONE
                }
            }
        }

        return Reason.NONE
    }
}