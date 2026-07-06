package com.ryosoftware.calls_blocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil

data class NormalizeResult(
    val normalizedPhoneNumber: String? = null,
    val error: NormalizeError? = null
)

enum class NormalizeError(val description: String) {
    PARSE_ERROR("parse error"),
    NOT_POSSIBLE_NUMBER("not possible number"),
    NOT_VALID_NUMBER("not valid number"),
    FORMAT_ERROR("format error"),
}

class PhoneUtils {
    companion object {
        fun formatPhoneNumber(phoneNumber: String): String {
            if (!phoneNumber.startsWith("+")) return phoneNumber
            return try {
                val phoneUtil = PhoneNumberUtil.getInstance()
                val parsed = phoneUtil.parse(phoneNumber, null)
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            } catch (_: Exception) {
                phoneNumber
            }
        }

        fun getNetworkCountriesIso(context: Context): Set<String>? {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                return runCatching {
                    val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                    val telephonyManager = context.getSystemService(TelephonyManager::class.java)

                    subscriptionManager.activeSubscriptionInfoList
                        ?.mapNotNull {
                            telephonyManager
                                .createForSubscriptionId(it.subscriptionId)
                                .networkCountryIso
                                .takeIf(String::isNotBlank)
                                ?.uppercase()
                        }
                        ?.toSet()
                        ?: emptySet()

                }.getOrNull()
            }
            return null
        }

        fun normalizeToE164OrNull(phoneNumber: String, networkCountryIso: String?, requirePossibleNumber: Boolean = false, requireValidNumber: Boolean = false, logger: Logger? = null): NormalizeResult {
            val phoneUtil = PhoneNumberUtil.getInstance()

            val parsedPhoneNumber = try {
                phoneUtil.parse(phoneNumber, networkCountryIso)
            } catch (exception: Exception) {
                logger?.log("A exception has been triggered while trying to parse $PHONE_NUMBER_REF: ${exception.toString()}", phoneNumber = phoneNumber)

                return NormalizeResult(null, NormalizeError.PARSE_ERROR)
            }

            if (requirePossibleNumber && (!phoneUtil.isPossibleNumber(parsedPhoneNumber))) {
                logger?.log("$PHONE_NUMBER_REF is not possible", phoneNumber = phoneNumber)

                return NormalizeResult(null, NormalizeError.NOT_POSSIBLE_NUMBER)
            }

            if (requireValidNumber && (! phoneUtil.isValidNumber(parsedPhoneNumber))) {
                logger?.log("$PHONE_NUMBER_REF is not valid", phoneNumber = phoneNumber)

                return NormalizeResult(null, NormalizeError.NOT_VALID_NUMBER)
            }

            val normalizedPhoneNumber = try {
                phoneUtil.format(parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
            } catch (exception: Exception) {
                logger?.log("A exception has been triggered while trying to format $PHONE_NUMBER_REF: ${exception.toString()}", phoneNumber = phoneNumber)

                return NormalizeResult(null, NormalizeError.FORMAT_ERROR)
            }

            logger?.log("$PHONE_NUMBER_REF has been normalized to $NORMALIZED_PHONE_NUMBER_REF", phoneNumber = phoneNumber, normalizedPhoneNumber = normalizedPhoneNumber)

            return NormalizeResult(normalizedPhoneNumber, null)
        }
    }
}