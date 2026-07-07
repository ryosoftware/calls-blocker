package com.ryosoftware.calls_blocker.service.callsblocker.logic

import com.ryosoftware.calls_blocker.data.db.Reason

interface AbstractRule {
    suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason
}

interface AbstractAllowRule : AbstractRule
interface AbstractBlockRule : AbstractRule