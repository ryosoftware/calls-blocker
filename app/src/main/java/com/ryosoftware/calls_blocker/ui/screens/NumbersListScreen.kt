package com.ryosoftware.calls_blocker.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
import com.ryosoftware.calls_blocker.data.formatPhoneNumber
import com.ryosoftware.calls_blocker.data.importexport.CommaSeparatedImporter
import com.ryosoftware.calls_blocker.data.importexport.ImportOptions
import com.ryosoftware.calls_blocker.data.importexport.ImportResult
import com.ryosoftware.calls_blocker.data.importexport.ImportStatus
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.viewmodel.NumbersViewModel
import kotlinx.coroutines.launch

@Composable
fun NumbersListScreen(
    viewModel: NumbersViewModel = hiltViewModel(),
    defaultCountryIso: String = "",
    onAddNumber: () -> Unit = {},
    onMultiSelect: (Int, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onImportReady: (ImportResult) -> Unit = {},
) {
    val logger = viewModel.logger
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockedNumbersCount by viewModel.blockedNumbersCount.collectAsState()
    val allowedNumbersCount by viewModel.allowedNumbersCount.collectAsState()
    val manualBlocks by viewModel.manualBlocks.collectAsState()
    val prefixBlocks by viewModel.prefixBlocks.collectAsState()
    val allowedExact by viewModel.allowedExact.collectAsState()
    val allowedPrefix by viewModel.allowedPrefix.collectAsState()
    val addNumberError by viewModel.addNumberError.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var blockedExactExpanded by remember { mutableStateOf(true) }
    var blockedPrefixExpanded by remember { mutableStateOf(true) }
    var allowedExactExpanded by remember { mutableStateOf(true) }
    var allowedPrefixExpanded by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<Number?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }
    var requirePossibleNumber by remember { mutableStateOf(true) }
    var requireValidNumber by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(false) }
    val isDeleting by viewModel.isDeleting.collectAsState()

    LaunchedEffect(isDeleting) {
        if (!isDeleting && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }

    val filteredManual = remember(searchQuery, manualBlocks) {
        if (searchQuery.isBlank()) manualBlocks
        else manualBlocks.filter { it.phoneNumber.contains(searchQuery) || it.description.contains(searchQuery, ignoreCase = true) }
    }
    val filteredPrefix = remember(searchQuery, prefixBlocks) {
        if (searchQuery.isBlank()) prefixBlocks
        else prefixBlocks.filter { it.phoneNumber.contains(searchQuery) || it.description.contains(searchQuery, ignoreCase = true) }
    }
    val filteredAllowedExact = remember(searchQuery, allowedExact) {
        if (searchQuery.isBlank()) allowedExact
        else allowedExact.filter { it.phoneNumber.contains(searchQuery) || it.description.contains(searchQuery, ignoreCase = true) }
    }
    val filteredAllowedPrefix = remember(searchQuery, allowedPrefix) {
        if (searchQuery.isBlank()) allowedPrefix
        else allowedPrefix.filter { it.phoneNumber.contains(searchQuery) || it.description.contains(searchQuery, ignoreCase = true) }
    }

    val allFiltered = remember(searchQuery, filteredManual, filteredPrefix, filteredAllowedExact, filteredAllowedPrefix) {
        filteredManual + filteredPrefix + filteredAllowedExact + filteredAllowedPrefix
    }
    val multiSelect = selectedIds.isNotEmpty()
    val allSelected = multiSelect && selectedIds.size == allFiltered.size

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    LaunchedEffect(selectedIds, allFiltered.size) {
        if (multiSelect) {
            onMultiSelect(
                selectedIds.size,
                allSelected,
                { selectedIds = emptySet() },
                { selectedIds = allFiltered.map { it.id }.toSet() },
                { showDeleteConfirm = true }
            )
        } else {
            onMultiSelect(0, false, {}, {}, {})
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                pendingImportUri = uri
                pendingImportCount = CommaSeparatedImporter(logger).countEntries(context, uri)
                requirePossibleNumber = true
                requireValidNumber = true
                isImporting = false
                showImportOptionsDialog = true
            }
        }
    }

    val fabRotation by animateFloatAsState(
        targetValue = if (fabExpanded) 135f else 0f,
        animationSpec = tween(400),
        label = "fabRotation"
    )

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it },
                    exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it }
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            importLauncher.launch(arrayOf("text/*", "*/*"))
                        },
                        icon = { Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.import_title)) },
                        text = { Text(stringResource(R.string.fab_import_label)) }
                    )
                }
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it },
                    exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it }
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            showAddDialog = true
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_number)) },
                        text = { Text(stringResource(R.string.fab_add_label)) }
                    )
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_number),
                        modifier = Modifier.rotate(fabRotation)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchVisible) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_numbers_hint)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (blockedNumbersCount + allowedNumbersCount == 0) {
                    item {
                        Text(
                            text = stringResource(R.string.numbers_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else if (searchVisible && searchQuery.isNotBlank() && filteredManual.isEmpty() && filteredPrefix.isEmpty() && filteredAllowedExact.isEmpty() && filteredAllowedPrefix.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    if (filteredManual.isNotEmpty()) {
                        item {
                            @SuppressLint("LocalContextResourcesRead")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { blockedExactExpanded = !blockedExactExpanded }
                            ) {
                                Icon(
                                    imageVector = if (blockedExactExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = pluralStringResource(R.plurals.section_blocked_exact_numbers, filteredManual.size, filteredManual.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (blockedExactExpanded) {
                            items(filteredManual, key = { it.id }) { number ->
                                NumberItem(
                                    number = number,
                                    viewModel = viewModel,
                                    isSelected = number.id in selectedIds,
                                    multiSelect = multiSelect,
                                    onLongClick = { toggleSelection(number.id) },
                                    onClick = {
                                        if (multiSelect) toggleSelection(number.id)
                                    },
                                    onDelete = { pendingDeleteEntry = number }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    if (filteredPrefix.isNotEmpty()) {
                        item {
                            @SuppressLint("LocalContextResourcesRead")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { blockedPrefixExpanded = !blockedPrefixExpanded }
                            ) {
                                Icon(
                                    imageVector = if (blockedPrefixExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = pluralStringResource(R.plurals.section_blocked_prefixes, filteredPrefix.size, filteredPrefix.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (blockedPrefixExpanded) {
                            items(filteredPrefix, key = { it.id }) { number ->
                                NumberItem(
                                    number = number,
                                    viewModel = viewModel,
                                    isSelected = number.id in selectedIds,
                                    multiSelect = multiSelect,
                                    onLongClick = { toggleSelection(number.id) },
                                    onClick = {
                                        if (multiSelect) toggleSelection(number.id)
                                    },
                                    onDelete = { pendingDeleteEntry = number }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    if (filteredAllowedExact.isNotEmpty()) {
                        item {
                            @SuppressLint("LocalContextResourcesRead")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { allowedExactExpanded = !allowedExactExpanded }
                            ) {
                                Icon(
                                    imageVector = if (allowedExactExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = pluralStringResource(R.plurals.section_allowed_exact_numbers, filteredAllowedExact.size, filteredAllowedExact.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (allowedExactExpanded) {
                            items(filteredAllowedExact, key = { it.id }) { number ->
                                NumberItem(
                                    number = number,
                                    viewModel = viewModel,
                                    isSelected = number.id in selectedIds,
                                    multiSelect = multiSelect,
                                    onLongClick = { toggleSelection(number.id) },
                                    onClick = {
                                        if (multiSelect) toggleSelection(number.id)
                                    },
                                    onDelete = { pendingDeleteEntry = number }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    if (filteredAllowedPrefix.isNotEmpty()) {
                        item {
                            @SuppressLint("LocalContextResourcesRead")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { allowedPrefixExpanded = !allowedPrefixExpanded }
                            ) {
                                Icon(
                                    imageVector = if (allowedPrefixExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = pluralStringResource(R.plurals.section_allowed_prefixes, filteredAllowedPrefix.size, filteredAllowedPrefix.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (allowedPrefixExpanded) {
                            items(filteredAllowedPrefix, key = { it.id }) { number ->
                                NumberItem(
                                    number = number,
                                    viewModel = viewModel,
                                    isSelected = number.id in selectedIds,
                                    multiSelect = multiSelect,
                                    onLongClick = { toggleSelection(number.id) },
                                    onClick = {
                                        if (multiSelect) toggleSelection(number.id)
                                    },
                                    onDelete = { pendingDeleteEntry = number }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }

            if (isImporting) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(stringResource(R.string.processing_data_title))
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.processing_data_message))
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_entries_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_entries_message, selectedIds.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    showDeleteConfirm = false
                    viewModel.removeEntries(ids)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEntry = null
                    viewModel.removeNumber(entry)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isDeleting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.deleting_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.deleting_message))
                }
            },
            confirmButton = {}
        )
    }

    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_file_summary,
                        pluralStringResource(R.plurals.numbers, pendingImportCount, pendingImportCount)))

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { requirePossibleNumber = !requirePossibleNumber }
                    ) {
                        Checkbox(
                            checked = requirePossibleNumber,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.force_possible_number))
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { requireValidNumber = !requireValidNumber }
                    ) {
                        Checkbox(
                            checked = requireValidNumber,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.force_valid_number))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportOptionsDialog = false
                    scope.launch {
                        isImporting = true

                        val uri = pendingImportUri ?: return@launch
                        val options = ImportOptions(
                            requirePossibleNumber = requirePossibleNumber,
                            requireValidNumber = requireValidNumber,
                        )
                        val result = CommaSeparatedImporter(logger).import(context, uri, defaultCountryIso, options)
                        val existingBlockedExact = manualBlocks.map { it.phoneNumber }.toSet()
                        val existingBlockedPrefix = prefixBlocks.map { it.phoneNumber }.toSet()
                        val existingAllowedExact = allowedExact.map { it.phoneNumber }.toSet()
                        val existingAllowedPrefix = allowedPrefix.map { it.phoneNumber }.toSet()

                        val updated = result.copy(
                            entries = result.entries.map { entry ->
                                if (entry.status == ImportStatus.New) {
                                    val isPrefix = entry.type == Type.PREFIX
                                    val inBlocked = if (isPrefix) entry.number in existingBlockedPrefix else entry.number in existingBlockedExact
                                    val inAllowed = if (isPrefix) entry.number in existingAllowedPrefix else entry.number in existingAllowedExact
                                    when {
                                        inBlocked -> entry.copy(status = ImportStatus.AlreadyBlocked)
                                        inAllowed -> entry.copy(status = ImportStatus.AlreadyAllowed)
                                        else -> entry
                                    }
                                } else entry
                            }
                        )

                        onImportReady(updated)
                        isImporting = false
                    }
                }) {
                    Text(stringResource(R.string.import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportOptionsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddDialog) {
        AddNumberDialog(
            getCountryName = viewModel::getCountryName,
            defaultCountryIso = defaultCountryIso,
            showBlockType = true,
            showActionSelector = true,
            addNumberError = addNumberError,
            onDismiss = {
                viewModel.clearAddNumberError()
                showAddDialog = false
            },
            onConfirm = { number, description, type, action ->
                if (viewModel.addNumber(number, description, type, action)) {
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberItem(
    viewModel: NumbersViewModel,
    number: Number,
    isSelected: Boolean,
    multiSelect: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val countryInfo = remember(number.phoneNumber) {
        findCountryByPhoneNumber(number.phoneNumber)
    }

    val isAllow = number.action == Action.ACTION_ALLOW

    Card(
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            val icon = when {
                isAllow -> Icons.Default.CheckCircleOutline
                else -> Icons.Default.Block
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = formatPhoneNumber(number.phoneNumber),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                if (countryInfo != null) {
                    val (country, _) = countryInfo
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.number_from_country_name_and_flag, viewModel.getCountryName(country), country.flag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (number.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = number.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when {
                            isAllow -> stringResource(R.string.label_allowed)
                            else -> stringResource(R.string.label_blocked)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isAllow -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )

                    Text(
                        text = when (number.type) {
                            Type.EXACT_COINCIDENCE -> stringResource(R.string.label_exact)
                            Type.PREFIX -> stringResource(R.string.label_prefix)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!multiSelect) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }
    }
}
