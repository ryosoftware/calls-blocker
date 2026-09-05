package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractSecondLevelBlockRule
import jakarta.inject.Inject

class ScheduleRule @Inject constructor(
    private val scheduleRuleRepository: ScheduleRuleRepository,
    private val settingsManager: SettingsManager,
): AbstractSecondLevelBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val scheduleActive = scheduleRuleRepository.isInScheduleBlock()

        return if (settingsManager.shouldBlockDueToSchedule(scheduleActive)) {
            Reason.SCHEDULE
        } else {
            Reason.NONE
        }
    }
}