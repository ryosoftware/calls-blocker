package com.ryosoftware.calls_blocker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
import com.ryosoftware.calls_blocker.data.importexport.CommaSeparatedImporter
import com.ryosoftware.calls_blocker.data.importexport.ImportOptions
import com.ryosoftware.calls_blocker.data.importexport.ImportResult
import com.ryosoftware.calls_blocker.data.importexport.ImportStatus
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.viewmodel.NumbersViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumbersListScreen(
    viewModel: NumbersViewModel = hiltViewModel(),
    defaultCountryIso: String = "",
    onMultiSelect: (Int, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onImportReady: (ImportResult) -> Unit = {},
) {
    val logger = viewModel.logger
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockedNumbersCount by viewModel.blockedNumbersCount.collectAsState()
    val allowedNumbersCount by viewModel.allowedNumbersCount.collectAsState()
    val manualBlocks by viewModel.incomingExactBlocks.collectAsState()
    val prefixBlocks by viewModel.incomingPrefixBlocks.collectAsState()
    val allowedExact by viewModel.incomingExactAllows.collectAsState()
    val allowedPrefix by viewModel.incomingPrefixAllows.collectAsState()
    val addNumberError by viewModel.addNumberError.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var pendingBottomSheetEntry by remember { mutableStateOf<Number?>(null) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var pendingEditDescription by remember { mutableStateOf<Number?>(null) }
    var pendingRemoveEntry by remember { mutableStateOf<Number?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportCount by remember { mutableIntStateOf(0) }
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
        (filteredManual + filteredPrefix + filteredAllowedExact + filteredAllowedPrefix)
            .sortedBy { it.phoneNumber }
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
                } else if (searchVisible && searchQuery.isNotBlank() && allFiltered.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    items(allFiltered, key = { it.id }) { number ->
                        NumberItem(
                            number = number,
                            isSelected = number.id in selectedIds,
                            multiSelect = multiSelect,
                            getCountryName = viewModel::getCountryName,
                            onLongClick = { toggleSelection(number.id) },
                            onClick = {
                                if (multiSelect) toggleSelection(number.id)
                            },
                            onMoreVert = {
                                pendingBottomSheetEntry = number
                                showBottomSheet = true
                            }
                        )
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
        val selectedNumbers = remember(selectedIds, allFiltered) {
            allFiltered.filter { it.id in selectedIds }
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_entries_title)) },
            text = {
                val displayNumbers = selectedNumbers.take(10)
                val remaining = selectedNumbers.size - displayNumbers.size

                Text(
                    stringResource(
                        if (remaining > 0) R.string.delete_numbers_and_x_more else R.string.delete_numbers,
                        displayNumbers.joinToString("\n") { PhoneUtils.formatPhoneNumber(it.phoneNumber) },
                        remaining
                    )
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

    pendingBottomSheetEntry?.let { entry ->
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = PhoneUtils.formatPhoneNumber(entry.phoneNumber),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                pendingEditDescription = entry
                                showEditDescriptionDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(16.dp))

                        Text(
                            text = stringResource(R.string.edit_description),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBottomSheet = false
                                pendingRemoveEntry = entry
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(16.dp))

                        Text(
                            text = stringResource(
                                when (entry.action) {
                                    Action.BLOCK -> R.string.stop_blocking
                                    Action.ALLOW -> R.string.stop_allowing
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    pendingRemoveEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoveEntry = null },
            title = { Text(PhoneUtils.formatPhoneNumber(entry.phoneNumber)) },
            text = {
                Text(
                    when (entry.action) {
                        Action.BLOCK -> stringResource(R.string.confirm_stop_blocking)
                        Action.ALLOW -> stringResource(R.string.confirm_stop_allowing)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoveEntry = null
                    viewModel.removeNumber(entry)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveEntry = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingEditDescription?.let { entry ->
        if (showEditDescriptionDialog) {
            var newDescription by remember(entry.id) { mutableStateOf(entry.description) }

            AlertDialog(
                onDismissRequest = {
                    showEditDescriptionDialog = false
                    pendingEditDescription = null
                },
                title = { Text(PhoneUtils.formatPhoneNumber(entry.phoneNumber)) },
                text = {
                    OutlinedTextField(
                        value = newDescription,
                        onValueChange = { newDescription = it },
                        label = { Text(stringResource(R.string.description_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateDescription(entry.copy(description = newDescription))
                        showEditDescriptionDialog = false
                        pendingEditDescription = null
                    }) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showEditDescriptionDialog = false
                        pendingEditDescription = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
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
            onConfirm = { number, description, action, type ->
                if (viewModel.addNumber(number, description, action, type)) {
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberItem(
    number: Number,
    isSelected: Boolean,
    multiSelect: Boolean,
    getCountryName: (Country) -> String,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onMoreVert: () -> Unit,
) {
    val countryInfo = remember(number.phoneNumber) {
        findCountryByPhoneNumber(number.phoneNumber)
    }

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
            verticalAlignment = Alignment.Top
        ) {
            if (multiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (multiSelect) 0.dp else 12.dp)
            ) {
                Text(
                    text = PhoneUtils.formatPhoneNumber(number.phoneNumber),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colorResource(when (number.action) {
                        Action.BLOCK -> R.color.status_inactive_text
                        Action.ALLOW -> R.color.status_active_text
                    })
                )

                if (countryInfo != null) {
                    val (country, _) = countryInfo

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.country_name_and_flag, getCountryName(country), country.flag),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(when (number.type) {
                        Type.EXACT_COINCIDENCE -> R.string.label_exact
                        Type.PREFIX -> R.string.label_prefix
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (number.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = number.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!multiSelect) {
                IconButton(
                    onClick = onMoreVert,
                    modifier = Modifier.align(Alignment.Bottom)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            }
        }
    }
}
