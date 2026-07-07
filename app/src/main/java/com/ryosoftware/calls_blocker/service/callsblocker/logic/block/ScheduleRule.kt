package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import jakarta.inject.Inject

class ScheduleRule @Inject constructor(
    private val scheduleRuleRepository: ScheduleRuleRepository,
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        if (scheduleRuleRepository.isInScheduleBlock()) {
            return Reason.SCHEDULE
        }

        return Reason.NONE
    }
}