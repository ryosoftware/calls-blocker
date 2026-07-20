package com.ryosoftware.calls_blocker.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.ui.rememberContactInfo
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.viewmodel.NumbersViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.core.net.toUri
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumbersListScreen(
    viewModel: NumbersViewModel = hiltViewModel(),
    settingsManager: SettingsManager,
    countryNameProvider: CountryNameProvider,
    defaultCountryIso: String = "",
    onMultiSelect: (Int, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit = { _, _, _, _, _ -> },
) {
    val context = LocalContext.current
    val logger = viewModel.logger
    val blockedNumbersCount by viewModel.blockedNumbersCount.collectAsState()
    val allowedNumbersCount by viewModel.allowedNumbersCount.collectAsState()
    val manualBlocks by viewModel.incomingExactBlocks.collectAsState()
    val prefixBlocks by viewModel.incomingPrefixBlocks.collectAsState()
    val allowedExact by viewModel.incomingExactAllows.collectAsState()
    val allowedPrefix by viewModel.incomingPrefixAllows.collectAsState()
    val addNumberError by viewModel.addNumberError.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showCallLogPicker by remember { mutableStateOf(false) }
    var showCallLogPickerActionDialog by remember { mutableStateOf(false) }
    var pendingPhoneNumber by remember { mutableStateOf("") }
    var speedDialExpanded by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expandedCardId by remember { mutableStateOf<Long?>(null) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var pendingEditDescription by remember { mutableStateOf<Number?>(null) }
    var pendingRemoveEntry by remember { mutableStateOf<Number?>(null) }
    val isDeleting by viewModel.isDeleting.collectAsState()

    val blockedExactNumbers = remember(manualBlocks) {
        manualBlocks.filter { it.type == Type.EXACT_COINCIDENCE }.map { it.phoneNumber }.toSet()
    }
    val allowedExactNumbers = remember(allowedExact) {
        allowedExact.filter { it.type == Type.EXACT_COINCIDENCE }.map { it.phoneNumber }.toSet()
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCallLogPicker = true
        }
    }

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

    val findMyPhoneNumbers = remember(settingsManager.findMyPhonePhoneNumbers) {
        settingsManager.findMyPhonePhoneNumbers
            .split(",")
            .map { it.trim() }
            .toSet()
    }

    BackHandler(enabled = expandedCardId != null) {
        expandedCardId = null
    }

    BackHandler(enabled = speedDialExpanded) {
        speedDialExpanded = false
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

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                speedDialExpanded = false
                                pendingPhoneNumber = ""
                                showAddDialog = true
                            },
                            text = {
                                Text(stringResource(R.string.add_manually))
                            },
                            icon = {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = stringResource(R.string.add_manually)
                                )
                            }
                        )

                        ExtendedFloatingActionButton(
                            onClick = {
                                speedDialExpanded = false
                                if (context.hasReadCallLogPermission()) {
                                    showCallLogPicker = true
                                } else {
                                    callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                }
                            },
                            text = {
                                Text(stringResource(R.string.add_from_call_log))
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = stringResource(R.string.add_from_call_log)
                                )
                            }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { speedDialExpanded = !speedDialExpanded }
                ) {
                    Icon(
                        if (speedDialExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_number)
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
                            context = LocalContext.current,
                            number = number,
                            isFindMyPhoneNumber = number.phoneNumber in findMyPhoneNumbers,
                            isSelected = number.id in selectedIds,
                            multiSelect = multiSelect,
                            expanded = expandedCardId == number.id,
                            getCountryName = viewModel::getCountryName,
                            onLongClick = { toggleSelection(number.id) },
                            onClick = {
                                if (multiSelect) {
                                    toggleSelection(number.id)
                                } else {
                                    expandedCardId = if (expandedCardId == number.id) null else number.id
                                }
                            },
                            onEditDescription = {
                                pendingEditDescription = number
                                showEditDescriptionDialog = true
                            },
                            onRemove = {
                                pendingRemoveEntry = number
                            }
                        )
                    }
                }
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

    if (showCallLogPicker) {
        CallLogPickerDialog(
            countryNameProvider = countryNameProvider,
            blockedNumbers = blockedExactNumbers,
            allowedNumbers = allowedExactNumbers,
            onSelect = { entry ->
                pendingPhoneNumber = entry.phoneNumber
                showCallLogPickerActionDialog = true
            },
            onDismiss = { showCallLogPicker = false },
            onRequestCallLogPermission = {
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        )
    }

    if (showCallLogPickerActionDialog && pendingPhoneNumber.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showCallLogPickerActionDialog = false
                pendingPhoneNumber = ""
            },
            title = { Text(stringResource(R.string.block_or_allow_title)) },
            text = { Text(PhoneUtils.formatPhoneNumber(pendingPhoneNumber)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addNumber(pendingPhoneNumber, "", Action.BLOCK, Type.EXACT_COINCIDENCE)
                    showCallLogPickerActionDialog = false
                    showCallLogPicker = false
                    pendingPhoneNumber = ""
                }) {
                    Text(stringResource(R.string.action_block))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.addNumber(pendingPhoneNumber, "", Action.ALLOW, Type.EXACT_COINCIDENCE)
                    showCallLogPickerActionDialog = false
                    showCallLogPicker = false
                    pendingPhoneNumber = ""
                }) {
                    Text(stringResource(R.string.action_allow))
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
    context: Context,
    number: Number,
    isFindMyPhoneNumber: Boolean,
    isSelected: Boolean,
    multiSelect: Boolean,
    expanded: Boolean,
    getCountryName: (Country) -> String,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onEditDescription: () -> Unit,
    onRemove: () -> Unit,
) {
    val countryInfo = remember(number.phoneNumber) {
        findCountryByPhoneNumber(number.phoneNumber)
    }

    val contactInfo = rememberContactInfo(number.phoneNumber, context)

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
        Column {
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
                    if (!contactInfo.name.isNullOrBlank()) {
                        Text(
                            text = contactInfo.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(when (number.action) {
                                Action.BLOCK -> R.color.blocked_call
                                Action.ALLOW -> R.color.allowed_call
                            })
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = PhoneUtils.formatPhoneNumber(number.phoneNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorResource(when (number.action) {
                            Action.BLOCK -> R.color.blocked_call
                            Action.ALLOW -> R.color.allowed_call
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

                    if (isFindMyPhoneNumber) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = context.getString(R.string.find_my_phone_contact),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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

                contactInfo.photo?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (!multiSelect && expanded) {
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (number.type == Type.EXACT_COINCIDENCE) {
                        val callContext = LocalContext.current
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = "tel:${number.phoneNumber}".toUri()
                            }
                            callContext.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = stringResource(R.string.call),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("phone", number.phoneNumber)
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onEditDescription) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_description),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(when (number.action) {
                                Action.BLOCK -> R.string.stop_blocking
                                Action.ALLOW -> R.string.stop_allowing
                            }),
                            modifier = Modifier.size(20.dp),
                            tint = colorResource(when (number.action) {
                                Action.BLOCK -> R.color.unblock
                                Action.ALLOW -> R.color.unallow
                            })
                        )
                    }
                }
            }
        }
    }
}
