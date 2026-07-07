package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractPriorityBlockRule
import jakarta.inject.Inject

class BlockExactNumberRule @Inject constructor(
    private val numberRepository: NumberRepository
): AbstractPriorityBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockExactNumber = numberRepository.isIncomingBlockedExact(normalizedPhoneNumber)

        if (blockExactNumber) {
            return Reason.BLACKLISTED_NUMBER
        }

        return Reason.NONE
    }
}