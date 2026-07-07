package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import jakarta.inject.Inject

class BlockAllRule @Inject constructor(
    private val settingsManager: SettingsManager,
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockAll = settingsManager.blockAll

        if (blockAll) {
            return Reason.BLOCK_ALL
        }

        return Reason.NONE
    }
}