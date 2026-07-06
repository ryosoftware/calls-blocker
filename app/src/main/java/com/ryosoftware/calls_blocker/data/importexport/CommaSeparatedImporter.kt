package com.ryosoftware.calls_blocker.data.importexport

import android.content.Context
import android.net.Uri
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.NormalizeError
import com.ryosoftware.calls_blocker.PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommaSeparatedImporter(private val logger: Logger) : Importer {
    override suspend fun countEntries(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val text = inputStream.bufferedReader().use { it.readText() }
            text.split(",").count { it.trim().isNotBlank() }
        } ?: 0
    }

    override suspend fun import(
        context: Context,
        uri: Uri,
        defaultCountryIso: String,
        options: ImportOptions,
    ): ImportResult = withContext(Dispatchers.IO) {
        ImportResult(entries = buildList {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val blankAfterRemovingNonDigitsError = context.getString(R.string.blank_after_removing_non_digits_error)
                    val notPossibleNumber = context.getString(R.string.not_possible_number)
                    val notValidNumber = context.getString(R.string.not_valid_number)
                    val parseError = context.getString(R.string.parse_error)
                    val formatError = context.getString(R.string.format_error)

                    val text = inputStream.bufferedReader().use { it.readText() }

                    text.split(",").forEach { rawEntry ->
                        val trimmed = rawEntry.trim()

                        if (trimmed.isBlank()) return@forEach

                        try {
                            val isPrefix = trimmed.endsWith("*")

                            val rawPhoneNumber = trimmed
                                .dropLast(if (isPrefix) 1 else 0)
                                .let {
                                    val hasPlus = it.startsWith("+")
                                    val digits = it.replace(Regex("[^\\d]"), "")
                                    if (hasPlus) "+$digits" else digits
                                }

                            if (rawPhoneNumber.isBlank()) {
                                logger.log("Error importing $PHONE_NUMBER_REF: blank after removing non-digits", phoneNumber = trimmed)

                                add(ImportEntry(trimmed, status = ImportStatus.Error, reason = blankAfterRemovingNonDigitsError))

                                return@forEach
                            }

                            val normalizeResult = PhoneUtils.normalizeToE164OrNull(
                                phoneNumber = rawPhoneNumber,
                                networkCountryIso = defaultCountryIso,
                                requirePossibleNumber = options.requirePossibleNumber,
                                requireValidNumber = options.requireValidNumber,
                            )

                            if (normalizeResult.error != null) {
                                val prefix = if (defaultCountryIso.isEmpty()) "" else "$defaultCountryIso-"
                                logger.log("Error normalizing $prefix$PHONE_NUMBER_REF: ${normalizeResult.error.description}", phoneNumber = trimmed)

                                val error = when (normalizeResult.error) {
                                    NormalizeError.PARSE_ERROR -> parseError
                                    NormalizeError.NOT_POSSIBLE_NUMBER -> notPossibleNumber
                                    NormalizeError.NOT_VALID_NUMBER -> notValidNumber
                                    NormalizeError.FORMAT_ERROR -> formatError
                                }

                                add(ImportEntry(trimmed, status = ImportStatus.Error, reason = error))

                                return@forEach
                            }

                            add(
                                ImportEntry(
                                    rawInput = trimmed,
                                    number = normalizeResult.normalizedPhoneNumber,
                                    type = if (isPrefix) Type.PREFIX else Type.EXACT_COINCIDENCE,
                                    status = ImportStatus.New
                                )
                            )
                        }
                        catch (exception: Exception) {
                            logger.log("Error importing $PHONE_NUMBER_REF: ${exception.toString()}", phoneNumber = trimmed)

                            add(ImportEntry(trimmed, status = ImportStatus.Error, reason = exception.toString()))
                        }
                    }
                }
            } catch (exception: Exception) {
                add(ImportEntry("", status = ImportStatus.Error, reason = exception.toString()))
            }
        })
    }
}
