package com.ryosoftware.calls_blocker.service.callsblocker.logic.allow

import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractAllowRule
import jakarta.inject.Inject

class AllowPrefixNumberRule @Inject constructor(
    private val numberRepository: NumberRepository
): AbstractAllowRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        if (numberRepository.isIncomingAllowedExact(normalizedPhoneNumber)) {
            return Reason.WHITELISTED_NUMBER
        }

        return Reason.NONE
    }
}