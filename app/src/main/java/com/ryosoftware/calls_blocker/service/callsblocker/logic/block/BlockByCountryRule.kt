package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import jakarta.inject.Inject

class BlockByCountryRule @Inject constructor(
    private val settingsManager: SettingsManager,
): AbstractBlockRule {
    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockInternational = settingsManager.blockInternational

        if (blockInternational) {
            val isInternational = normalizedPhoneNumber.startsWith("+")

            if (isInternational) {
                val allowedCountriesPhoneCodes = settingsManager.getAllowedCountryPhoneCodes()
                val isBlockedByInternationalRule = allowedCountriesPhoneCodes
                    .none {
                        it.isNotEmpty() && normalizedPhoneNumber.drop(1).startsWith(it)
                    }

                if (isBlockedByInternationalRule) {
                    return Reason.INTERNATIONAL_NUMBER
                }
            }
        }

        return Reason.NONE
    }
}