package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import jakarta.inject.Inject

class BlockPrefixNumberRule @Inject constructor(
    private val numberRepository: NumberRepository
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockPrefixNumber = numberRepository.isIncomingBlockedByPrefix(normalizedPhoneNumber)

        if (blockPrefixNumber) {
            return Reason.BLACKLISTED_PREFIX
        }

        return Reason.NONE
    }
}