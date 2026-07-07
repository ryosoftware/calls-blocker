package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import android.content.Context
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.CallsLogHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class BlockNotDialedCallsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val callsLogHelper: CallsLogHelper
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockNotCalled = settingsManager.blockNotCalled

        if (blockNotCalled) {
            val windowDays = settingsManager.notCalledWindowDays
            val hasCalledBefore = callsLogHelper.hasOutgoingCallToNumber(context, normalizedPhoneNumber, phoneNumber, windowDays, normalizeToE164)

            if (!hasCalledBefore) {
                return Reason.NOT_CALLED
            }
        }

        return Reason.NONE
    }
}