package com.ryosoftware.calls_blocker.service.callsblocker

import android.content.Context
import android.os.SystemClock
import android.telecom.Call
import android.telephony.TelephonyManager
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractAllowRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.allow.AllowExactNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.allow.AllowPrefixNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockAllRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockByCountryRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockByFindMyPhoneRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockExactNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockGroupsOfContactsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockNotContactsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockNotDialedCallsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockPrefixNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockRejectedCallsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockRepeatedCallsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.ScheduleRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.evaluateFirst
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow

class Logic @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val findMyPhoneRule: BlockByFindMyPhoneRule,
    private val allowRules: Set<@JvmSuppressWildcards AbstractAllowRule>,
    private val blockRules: Set<@JvmSuppressWildcards AbstractBlockRule>,

    private val logger: Logger,
) {
    companion object {
        private const val PRIVATE_NUMBER = "PRIVATE"
        private const val UNKNOWN_NUMBER = "UNKNOWN"

        private const val NO_NUMBER = "+"
    }

    private var possibleCountries = mutableSetOf<String?>()

    private fun isHiddenNumber(phoneNumber: String?): Boolean =
        phoneNumber.isNullOrBlank() || phoneNumber == PRIVATE_NUMBER || phoneNumber == UNKNOWN_NUMBER || phoneNumber == NO_NUMBER

    private fun initializePossibleCountries() {
        possibleCountries.clear()

        possibleCountries.addAll(PhoneUtils.getNetworkCountriesIso(context).orEmpty())
        possibleCountries.add(settingsManager.defaultCountryIso.ifEmpty { null })
    }
    private fun normalizeToE164(phoneNumber: String?, subscriptionId: Int?): String {
        if (isHiddenNumber(phoneNumber)) return ""

        requireNotNull(phoneNumber)

        return runCatching {
            logger.log("Received a call from $PHONE_NUMBER_REF and trying to normalize phone number", phoneNumber = phoneNumber)

            val possibleCountriesPlusSubscriptionIdCountry = mutableSetOf<String?>()

            subscriptionId
                ?.let {
                    context.getSystemService(TelephonyManager::class.java)
                        .createForSubscriptionId(it)
                        .networkCountryIso
                }
                ?.uppercase()
                ?.ifEmpty { null }
                ?.let(possibleCountriesPlusSubscriptionIdCountry::add)

            possibleCountriesPlusSubscriptionIdCountry.addAll(possibleCountries)

            val normalizeResult = possibleCountriesPlusSubscriptionIdCountry
                .asSequence()
                .map { country ->
                    PhoneUtils.normalizeToE164OrNull(phoneNumber = phoneNumber, networkCountryIso = country, logger = logger)
                }
                .firstOrNull { it.normalizedPhoneNumber != null }

            return normalizeResult?.normalizedPhoneNumber ?: phoneNumber
        }.getOrDefault(phoneNumber)
    }

    private fun normalizeToE164(phoneNumber: String?): String {
        if (isHiddenNumber(phoneNumber)) return ""

        requireNotNull(phoneNumber)

        val normalizeResult = possibleCountries
            .asSequence()
            .map { country ->
                PhoneUtils.normalizeToE164OrNull(phoneNumber = phoneNumber, networkCountryIso = country, logger = logger)
            }
            .firstOrNull { it.normalizedPhoneNumber != null }

        return normalizeResult?.normalizedPhoneNumber ?: phoneNumber
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun performTests(normalizedPhoneNumber: String?, phoneNumber: String?, testingPurposes: Boolean): Reason {
        val isHiddenNumber = isHiddenNumber(normalizedPhoneNumber)

        // First we check if phone is hidden

        if (isHiddenNumber) {
            // For hidden numbers I reject if hidden or unknown numbers are disallowed

            val blockHidden = settingsManager.blockHidden
            val blockUnknown = settingsManager.blockUnknown

            if (blockHidden || blockUnknown) {
                return Reason.HIDDEN_NUMBER
            }

            return Reason.NONE
        }

        requireNotNull(normalizedPhoneNumber)
        requireNotNull(phoneNumber)

        // "Find my phone" has higher priority (even more than white listed)

        if (!testingPurposes) {
            val blockReason = findMyPhoneRule.evaluate(normalizedPhoneNumber, phoneNumber, ::normalizeToE164, ::isHiddenNumber)

            if (blockReason != Reason.NONE) {
                return blockReason
            }
        }

        // We evaluate allow rules

        val allowReason = allowRules.evaluateFirst(normalizedPhoneNumber, phoneNumber, ::normalizeToE164, ::isHiddenNumber)
        if (allowReason != Reason.NONE) {
            return allowReason
        }

        // We evaulate block rules

        return blockRules.evaluateFirst(normalizedPhoneNumber, phoneNumber, ::normalizeToE164, ::isHiddenNumber)
    }

    private fun normalizePhoneNumber(phoneNumber: String?, subscriptionId: Int? = null): String? {
        val isHiddenNumber = isHiddenNumber(phoneNumber)

        val normalizedPhoneNumber = when {
            isHiddenNumber -> ""
            else -> {
                initializePossibleCountries()
                normalizeToE164(phoneNumber, subscriptionId)
            }
        }

        return normalizedPhoneNumber
    }

    fun normalizePhoneNumber(callDetails: Call.Details): String? =
        normalizePhoneNumber(callDetails.handle?.schemeSpecificPart ?: "", callDetails.accountHandle?.id?.toIntOrNull())

    suspend fun isCallBlocked(phoneNumber: String?, subscriptionId: Int? = null, testingPurposes: Boolean = false): Pair<String?, Reason> {
        val startTime = SystemClock.elapsedRealtime()

        initializePossibleCountries()
        val normalizedPhoneNumber = normalizePhoneNumber(phoneNumber, subscriptionId)
        val reason = performTests(normalizedPhoneNumber, phoneNumber, testingPurposes)

        val processTime = SystemClock.elapsedRealtime() - startTime
        logger.log("Call filtering process finished in ${processTime}ms; post call screening worker will be initialized")

        return normalizedPhoneNumber to reason
     }

    suspend fun isCallBlocked(callDetails: Call.Details): Pair<String?, Reason> =
        isCallBlocked(callDetails.handle?.schemeSpecificPart ?: "", callDetails.accountHandle?.id?.toIntOrNull(), false)
}