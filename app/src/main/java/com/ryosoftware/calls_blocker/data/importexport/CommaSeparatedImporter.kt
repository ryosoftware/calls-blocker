package com.ryosoftware.calls_blocker.data.importexport

import android.content.Context
import android.net.Uri
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.NormalizeError
import com.ryosoftware.calls_blocker.PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommaSeparatedImporter(private val logger: Logger) : Importer {
    private fun String.splitEntries(): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < length) {
            when {
                this[i] == '"' && inQuotes && i + 1 < length && this[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }

                this[i] == '"' -> {
                    inQuotes = !inQuotes
                }

                this[i] == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }

                else -> current.append(this[i])
            }
            i++
        }

        result += current.toString()

        return result
    }

    override suspend fun countEntries(
        context: Context,
        uri: Uri,
    ): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val text = inputStream.bufferedReader().use { it.readText() }
            text.splitEntries().count { it.trim().isNotBlank() }
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

                    text.splitEntries().forEach { rawEntry ->
                        val trimmed = rawEntry.trim()

                        if (trimmed.isBlank()) return@forEach

                        try {
                            val separator = trimmed.indexOf(':')

                            val rawNumber = if (separator >= 0) {
                                trimmed.substring(0, separator).trim()
                            } else {
                                trimmed
                            }

                            val description = if (separator >= 0) {
                                trimmed
                                    .substring(separator + 1)
                                    .trim()
                                    .removeSurrounding("\"")
                                    .replace("\"\"", "\"")
                            } else {
                                null
                            }

                            val isPrefix = rawNumber.endsWith("*")

                            val rawPhoneNumber = rawNumber
                                .dropLast(if (isPrefix) 1 else 0)
                                .let {
                                    val hasPlus = it.startsWith("+")
                                    val digits = it.replace(Regex("[^\\d]"), "")
                                    if (hasPlus) "+$digits" else digits
                                }

                            if (rawPhoneNumber.isBlank()) {
                                logger.log(
                                    "Error importing $PHONE_NUMBER_REF: blank after removing non-digits",
                                    phoneNumber = trimmed
                                )

                                add(
                                    ImportEntry(
                                        trimmed,
                                        description = description,
                                        status = ImportStatus.Error,
                                        reason = blankAfterRemovingNonDigitsError
                                    )
                                )

                                return@forEach
                            }

                            val normalizeResult = PhoneUtils.normalizeToE164OrNull(
                                phoneNumber = rawPhoneNumber,
                                networkCountryIso = defaultCountryIso,
                                requirePossibleNumber = options.requirePossibleNumber,
                                requireValidNumber = options.requireValidNumber,
                            )

                            if (normalizeResult.error != null) {
                                val prefix =
                                    if (defaultCountryIso.isEmpty()) "" else "$defaultCountryIso-"

                                logger.log(
                                    "Error normalizing $prefix$PHONE_NUMBER_REF: ${normalizeResult.error.description}",
                                    phoneNumber = trimmed
                                )

                                val error = when (normalizeResult.error) {
                                    NormalizeError.PARSE_ERROR -> parseError
                                    NormalizeError.NOT_POSSIBLE_NUMBER -> notPossibleNumber
                                    NormalizeError.NOT_VALID_NUMBER -> notValidNumber
                                    NormalizeError.FORMAT_ERROR -> formatError
                                }

                                add(
                                    ImportEntry(
                                        trimmed,
                                        description = description,
                                        status = ImportStatus.Error,
                                        reason = error
                                    )
                                )

                                return@forEach
                            }

                            add(
                                ImportEntry(
                                    rawInput = trimmed,
                                    number = normalizeResult.normalizedPhoneNumber,
                                    description = description,
                                    type = if (isPrefix) {
                                        Type.PREFIX
                                    } else {
                                        Type.EXACT_COINCIDENCE
                                    },
                                    status = ImportStatus.New
                                )
                            )
                        } catch (exception: Exception) {
                            logger.log(
                                "Error importing $PHONE_NUMBER_REF: ${exception.toString()}",
                                phoneNumber = trimmed
                            )

                            add(
                                ImportEntry(
                                    trimmed,
                                    status = ImportStatus.Error,
                                    reason = exception.toString()
                                )
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                add(
                    ImportEntry(
                        "",
                        status = ImportStatus.Error,
                        reason = exception.toString()
                    )
                )
            }
        })
    }

    private fun String.escapeDescription() =
        replace("\"", "\"\"")

    override suspend fun export(
        context: Context,
        uri: Uri,
        numbers: List<Number>,
    ): Boolean = withContext(Dispatchers.IO) {
        val text = numbers
            .filter { it.action == Action.BLOCK }
            .joinToString(",") { number ->
                buildString {
                    append(number.phoneNumber)

                    if (number.type == Type.PREFIX) {
                        append("*")
                    }

                    number.description
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            append(":\"")
                            append(it.escapeDescription())
                            append('"')
                        }
                }
            }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(text.encodeToByteArray())
        }
        true
    }
}
