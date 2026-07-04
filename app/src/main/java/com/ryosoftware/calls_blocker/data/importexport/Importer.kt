package com.ryosoftware.calls_blocker.data.importexport

import android.content.Context
import android.net.Uri
import com.ryosoftware.calls_blocker.data.db.Type

enum class ImportStatus {
    New,
    AlreadyBlocked,
    AlreadyAllowed,
    Error
}

data class ImportEntry(
    val rawInput: String,
    val number: String? = null,
    val type: Type = Type.EXACT_COINCIDENCE,
    val status: ImportStatus = ImportStatus.New,
    val reason: String = ""
)

data class ImportResult(
    val entries: List<ImportEntry> = emptyList()
) {
    val newEntries: List<ImportEntry> get() = entries.filter { it.status == ImportStatus.New }
    val alreadyBlockedEntries: List<ImportEntry> get() = entries.filter { it.status == ImportStatus.AlreadyBlocked }
    val alreadyAllowedEntries: List<ImportEntry> get() = entries.filter { it.status == ImportStatus.AlreadyAllowed }
    val alreadyAddedEntries: List<ImportEntry> get() = alreadyBlockedEntries + alreadyAllowedEntries
    val errorEntries: List<ImportEntry> get() = entries.filter { it.status == ImportStatus.Error }
    val newEntriesCount: Int get() = newEntries.size
    val alreadyBlockedCount: Int get() = alreadyBlockedEntries.size
    val alreadyAllowedCount: Int get() = alreadyAllowedEntries.size
    val alreadyAddedCount: Int get() = alreadyAddedEntries.size
    val errorCount: Int get() = errorEntries.size
}

data class ImportOptions(
    val requirePossibleNumber: Boolean = true,
    val requireValidNumber: Boolean = true,
)

interface Importer {
    suspend fun countEntries(context: Context, uri: Uri): Int

    suspend fun import(
        context: Context,
        uri: Uri,
        defaultCountryIso: String,
        options: ImportOptions = ImportOptions(),
    ): ImportResult
}
