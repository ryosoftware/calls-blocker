package com.ryosoftware.calls_blocker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.data.importexport.ImportEntry
import com.ryosoftware.calls_blocker.data.importexport.ImportResult
import com.ryosoftware.calls_blocker.data.importexport.ImportStatus
import com.ryosoftware.calls_blocker.viewmodel.NumbersViewModel
import kotlinx.coroutines.launch

@Composable
fun ImportReviewScreen(
    importResult: ImportResult,
    viewModel: NumbersViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isImporting by remember { mutableStateOf(false) }

    ImportPreviewContent(
        result = importResult,
        isImporting = isImporting,
        onCancel = {
            if (!isImporting) onDismiss()
        },
        onImport = {
            scope.launch {
                isImporting = true
                val entries = importResult.newEntries.mapNotNull { entry ->
                    entry.number?.let { it to entry.type }
                }
                val actualImported = if (entries.isNotEmpty()) {
                    viewModel.addAll(entries)
                } else 0
                isImporting = false
                if (actualImported > 0) {
                    Toast.makeText(context, R.string.import_success_toast, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.import_error_toast, Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
        }
    )
}

@Composable
private fun ImportPreviewContent(
    result: ImportResult,
    isImporting: Boolean = false,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    var expandedNew by remember { mutableStateOf(true) }
    var expandedBlocked by remember { mutableStateOf(false) }
    var expandedErrors by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (result.newEntries.isNotEmpty()) {
                item(key = "header_new") {
                    SectionHeader(
                        title = pluralStringResource(R.plurals.import_preview_message, result.newEntriesCount, result.newEntriesCount),
                        expanded = expandedNew,
                        onToggle = { expandedNew = !expandedNew }
                    )
                }
                if (expandedNew) {
                    items(result.newEntries, key = { it.number ?: it.rawInput }) { entry ->
                        ImportEntryCard(entry)
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            if (result.alreadyAddedCount > 0) {
                item(key = "header_blocked") {
                    SectionHeader(
                        title = pluralStringResource(R.plurals.import_preview_already_blocked_or_allowed, result.alreadyAddedCount, result.alreadyAddedCount),
                        expanded = expandedBlocked,
                        onToggle = { expandedBlocked = !expandedBlocked }
                    )
                }
                if (expandedBlocked) {
                    items(result.alreadyAddedEntries, key = { it.number ?: it.rawInput }) { entry ->
                        ImportEntryCard(entry)
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            if (result.errorCount > 0) {
                item(key = "header_errors") {
                    SectionHeader(
                        title = pluralStringResource(R.plurals.import_preview_errors, result.errorCount, result.errorCount),
                        expanded = expandedErrors,
                        onToggle = { expandedErrors = !expandedErrors }
                    )
                }
                if (expandedErrors) {
                    items(result.errorEntries, key = { it.rawInput }) { entry ->
                        ImportErrorEntryCard(entry)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        Spacer(Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            Button(onClick = onCancel, enabled = !isImporting) {
                Text(stringResource(R.string.cancel))
            }

            if (result.newEntriesCount > 0) {
                Button(onClick = onImport, enabled = !isImporting) {
                    Text(stringResource(R.string.import_confirm))
                }
            }
        }
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.importing_data_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.importing_data_message))
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ImportEntryCard(entry: ImportEntry) {
    val isAlreadyAdded = entry.status == ImportStatus.AlreadyBlocked || entry.status == ImportStatus.AlreadyAllowed
    val icon = when (entry.status) {
        ImportStatus.AlreadyBlocked -> Icons.Default.Block
        ImportStatus.AlreadyAllowed -> Icons.Default.CheckCircleOutline
        else -> Icons.Default.Block
    }
    val color = when (entry.status) {
        ImportStatus.AlreadyBlocked -> MaterialTheme.colorScheme.error
        ImportStatus.AlreadyAllowed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val typeLabel = when (entry.type) {
        Type.EXACT_COINCIDENCE -> stringResource(R.string.label_exact)
        Type.PREFIX -> stringResource(R.string.label_prefix)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = entry.number ?: entry.rawInput,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAlreadyAdded) {
                        Text(
                            text = when (entry.status) {
                                ImportStatus.AlreadyBlocked -> stringResource(R.string.label_blocked)
                                else -> stringResource(R.string.label_allowed)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }

                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportErrorEntryCard(entry: ImportEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (entry.rawInput.isNotBlank()) {
                Text(
                    text = entry.rawInput,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
