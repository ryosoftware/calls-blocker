package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractSecondLevelBlockRule
import jakarta.inject.Inject

class BlockWhenDndRule @Inject constructor(
    private val settingsManager: SettingsManager,
): AbstractSecondLevelBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        return if (settingsManager.shouldBlockDueToDnd()) {
            Reason.DND_ACTIVE
        } else {
            Reason.NONE
        }
    }
}