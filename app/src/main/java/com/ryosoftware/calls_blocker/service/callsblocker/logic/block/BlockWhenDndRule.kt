package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import jakarta.inject.Inject

class BlockWhenDndRule @Inject constructor(
    private val settingsManager: SettingsManager,
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        if (!settingsManager.blockWhenDnd) {
            return Reason.NONE
        }

        return if (settingsManager.isDndActive()) {
            Reason.DND_ACTIVE
        } else {
            Reason.NONE
        }
    }
}