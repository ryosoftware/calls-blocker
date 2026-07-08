package com.ryosoftware.calls_blocker.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.Main.Companion.hasPostNotificationsPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadPhoneStatePermission
import com.ryosoftware.calls_blocker.Main.Companion.isIgnoringBatteryOptimizations
import com.ryosoftware.calls_blocker.Main.Companion.requestIgnoreBatteryOptimizationsPermission
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.countries
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.ui.screens.settings.CallScreeningStatusCard
import com.ryosoftware.calls_blocker.ui.screens.settings.FindMyPhoneSection
import com.ryosoftware.calls_blocker.viewmodel.BackupEvent
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import com.ryosoftware.calls_blocker.viewmodel.SettingsViewModel
import com.ryosoftware.calls_blocker.service.BlockAllTileService
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDebugLog: () -> Unit = {},
    onNavigateToCallBlockingRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var skipCallLog by remember { mutableStateOf(viewModel.skipCallLog) }
    var defaultCountryIso by remember { mutableStateOf(viewModel.defaultCountryIso) }
    val screeningEnabled by viewModel.isScreeningEnabled.collectAsState()
    var showDefaultCountryDialog by remember { mutableStateOf(false) }
    var isLoggingToFile by remember { mutableStateOf(viewModel.isLoggingToFile) }
    var findMyPhoneEnabled by remember { mutableStateOf(viewModel.findMyPhoneEnabled) }
    var findMyPhonePhoneNumbers by remember { mutableStateOf(viewModel.findMyPhonePhoneNumbers) }
    var findMyPhoneCallCount by remember { mutableIntStateOf(viewModel.findMyPhoneCallCount) }
    var findMyPhoneWindowMinutes by remember { mutableIntStateOf(viewModel.findMyPhoneWindowMinutes) }
    var findMyPhoneRingtoneUri by remember { mutableStateOf(viewModel.findMyPhoneRingtoneUri) }
    var findMyPhoneVibrationPattern by remember { mutableStateOf(viewModel.findMyPhoneVibrationPattern) }
    var hasRequestedContacts by remember { mutableStateOf(viewModel.contactsPermissionRequested) }
    var showReadContactsPermissionRationale by remember { mutableStateOf(false) }
    var hasRequestedCallsLog by remember { mutableStateOf(viewModel.callsLogPermissionRequested) }
    var showReadCallLogRationale by remember { mutableStateOf(false) }
    var hasRequestedNotifications by remember { mutableStateOf(false) }
    var showPostNotificationsRationale by remember { mutableStateOf(false) }
    var hasRequestedPhoneState by remember { mutableStateOf(false) }
    var showReadPhoneStateRationale by remember { mutableStateOf(false) }
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    var pendingFindMyPhoneToggle by remember { mutableStateOf<Boolean?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showTestScreeningDialog by remember { mutableStateOf(false) }
    var testScreeningResult by remember { mutableStateOf<Pair<String, Reason?>?>(null) }
    val scheduleRules by viewModel.scheduleRules.collectAsStateWithLifecycle()

    // Listen for blockAll changes from Quick Settings Tile
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BlockAllTileService.ACTION_BLOCK_ALL_CHANGED) {
                    val newValue = intent.getBooleanExtra(BlockAllTileService.EXTRA_VALUE, false)
                    viewModel.blockAll = newValue
                }
            }
        }

        val filter = IntentFilter(BlockAllTileService.ACTION_BLOCK_ALL_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.checkScreeningStatus() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionCheckTrigger++
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissionCheckTrigger++ }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingFindMyPhoneToggle != null) {
            findMyPhoneEnabled = pendingFindMyPhoneToggle!!
            viewModel.findMyPhoneEnabled = pendingFindMyPhoneToggle!!
        }
        pendingFindMyPhoneToggle = null
        permissionCheckTrigger++
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCheckTrigger++
        }
    }

    val contactsPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadContactsPermission()
    }

    val notificationPermissionGranted = remember(permissionCheckTrigger) {
        context.hasPostNotificationsPermission()
    }

    val callLogPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadCallLogPermission()
    }

    val phoneStatePermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadPhoneStatePermission()
    }

    val batteryOptimizationGranted = remember(permissionCheckTrigger) {
        context.isIgnoringBatteryOptimizations()
    }

    LaunchedEffect(Unit) {
        viewModel.checkScreeningStatus()
    }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { event ->
            when (event) {
                is BackupEvent.Success -> {
                    Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show()
                }
                is BackupEvent.RestoreSuccess -> {
                    defaultCountryIso = viewModel.defaultCountryIso
                    skipCallLog = viewModel.skipCallLog
                    findMyPhoneEnabled = viewModel.findMyPhoneEnabled
                    findMyPhonePhoneNumbers = viewModel.findMyPhonePhoneNumbers
                    findMyPhoneCallCount = viewModel.findMyPhoneCallCount
                    findMyPhoneWindowMinutes = viewModel.findMyPhoneWindowMinutes
                    findMyPhoneRingtoneUri = viewModel.findMyPhoneRingtoneUri
                    findMyPhoneVibrationPattern = viewModel.findMyPhoneVibrationPattern
                    isLoggingToFile = viewModel.isLoggingToFile
                    hasRequestedContacts = viewModel.contactsPermissionRequested
                    hasRequestedCallsLog = viewModel.callsLogPermissionRequested
                    hasRequestedNotifications = viewModel.notificationsPermissionRequested
                    Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT).show()
                }
                is BackupEvent.Error -> {
                    errorDialogMessage = event.message
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTrigger++
                val callLogGranted = context.hasReadCallLogPermission()
                if (callLogGranted && pendingFindMyPhoneToggle != null) {
                    findMyPhoneEnabled = pendingFindMyPhoneToggle!!
                    viewModel.findMyPhoneEnabled = pendingFindMyPhoneToggle!!
                    pendingFindMyPhoneToggle = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CallScreeningStatusCard(
            enabled = screeningEnabled,
            onEnable = {
                val intent = viewModel.createRequestRoleIntent()
                if (intent != null) {
                    roleLauncher.launch(intent)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            },
            contactsPermissionGranted = contactsPermissionGranted,
            phoneStatePermissionGranted = phoneStatePermissionGranted,
            onRequestContactsPermission = {
                showReadContactsPermissionRationale = true
            },
            onRequestPhoneStatePermission = {
                showReadPhoneStateRationale = true
            }
        )

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.default_country_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.default_country_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                val defaultCountry = defaultCountryIso.let { iso ->
                    if (iso.isNotEmpty()) countries.firstOrNull { it.iso == iso }
                    else null
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.height(32.dp),
                    onClick = { showDefaultCountryDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDefaultCountryDialog = true }) {
                            Text(
                                text = defaultCountry?.let { stringResource(R.string.country_name_and_flag, viewModel.getCountryName(it), it.flag) } ?: stringResource(R.string.default_country_not_set),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.incoming_call_blocking_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.incoming_call_blocking_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onNavigateToCallBlockingRules() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.incoming_call_bloquing_button))
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.test_screening_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(12.dp))

                val needsContactsPermission = (viewModel.blockUnknown || viewModel.blockGroups) && !contactsPermissionGranted
                val needsCallLogPermission = (viewModel.blockNotCalled || viewModel.blockRejected || viewModel.blockRepeated) && !callLogPermissionGranted
                val lines = mutableListOf(stringResource(R.string.test_screening_description))
                if (scheduleRules.isNotEmpty()) lines.add(stringResource(R.string.test_screening_description_addon_scheduler))
                if (needsContactsPermission || needsCallLogPermission) lines.add(stringResource(R.string.test_screening_description_addon_permissions))
                if (findMyPhoneEnabled) lines.add(stringResource(R.string.test_screening_description_addon_find_my_phone))

                Text(
                    text = lines.joinToString("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showTestScreeningDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.test_screening_button))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            skipCallLog = !skipCallLog
                            viewModel.skipCallLog = skipCallLog
                        },
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.skip_call_log_title),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = stringResource(R.string.skip_call_log_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(24.dp))

                    Switch(
                        checked = skipCallLog,
                        onCheckedChange = null
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        FindMyPhoneSection(
            findMyPhoneEnabled = findMyPhoneEnabled,
            onFindMyPhoneEnabledChange = { enabled ->
                if (enabled && !callLogPermissionGranted) {
                    pendingFindMyPhoneToggle = true
                    showReadCallLogRationale = true
                } else {
                    pendingFindMyPhoneToggle = null
                    findMyPhoneEnabled = enabled
                    viewModel.findMyPhoneEnabled = enabled
                }
            },
            findMyPhonePhoneNumbers = findMyPhonePhoneNumbers,
            onFindMyPhonePhoneNumbersChange = { findMyPhonePhoneNumbers = it; viewModel.findMyPhonePhoneNumbers = it },
            findMyPhoneCallCount = findMyPhoneCallCount,
            onFindMyPhoneCallCountChange = { findMyPhoneCallCount = it },
            onFindMyPhoneCallCountChangeFinished = { viewModel.findMyPhoneCallCount = findMyPhoneCallCount },
            findMyPhoneWindowMinutes = findMyPhoneWindowMinutes,
            onFindMyPhoneWindowMinutesChange = { findMyPhoneWindowMinutes = it },
            onFindMyPhoneWindowMinutesChangeFinished = { viewModel.findMyPhoneWindowMinutes = findMyPhoneWindowMinutes },
            findMyPhoneRingtoneUri = findMyPhoneRingtoneUri,
            onFindMyPhoneRingtoneUriChange = { findMyPhoneRingtoneUri = it; viewModel.findMyPhoneRingtoneUri = it },
            findMyPhoneVibrationPattern = findMyPhoneVibrationPattern,
            onFindMyPhoneVibrationPatternChange = { findMyPhoneVibrationPattern = it; viewModel.findMyPhoneVibrationPattern = it },
            callLogPermissionGranted = callLogPermissionGranted,
            onTestFindMyPhone = { viewModel.testFindMyPhone() },
            batteryOptimizationGranted = batteryOptimizationGranted,
            onRequestCallLogPermission = { showReadCallLogRationale = true },
            onRequestBatteryOptimization = { context.requestIgnoreBatteryOptimizationsPermission() },
            getCountryName = viewModel::getCountryName,
            defaultCountryIso = defaultCountryIso,
        )

        if ((!notificationPermissionGranted) || (!batteryOptimizationGranted)) {
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.permissions_title),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = stringResource(R.string.permissions_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showPostNotificationsRationale = true },
                        enabled = !notificationPermissionGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permission_notifications))
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { context.requestIgnoreBatteryOptimizationsPermission() },
                        enabled = !batteryOptimizationGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permission_battery))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToDebugLog() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.debug_information),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = if (isLoggingToFile) stringResource(R.string.debug_information_enabled) else stringResource(R.string.debug_information_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLoggingToFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    @SuppressLint("LocalContextGetResourceValueCall")
                    val url = context.getString(R.string.github_repo)

                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.github_repo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        HorizontalDivider()

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.backup_import))
            }

            OutlinedButton(
                onClick = {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss"))
                    exportLauncher.launch("calls_blocker_backup-$timestamp.json")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.backup_export))
            }
        }

        if (showTestScreeningDialog) {
            AddNumberDialog(
                title = stringResource(R.string.test_screening_title),
                getCountryName = viewModel::getCountryName,
                defaultCountryIso = defaultCountryIso,
                showBlockType = false,
                showDescription = false,
                showActionSelector = false,
                confirmButtonText = stringResource(R.string.test_screening_button),
                onDismiss = { showTestScreeningDialog = false },
                onConfirm = { number, _, _, _ ->
                    scope.launch {
                        val reason = viewModel.getPhoneNumberBlockReason(number)
                        testScreeningResult = number to reason
                        showTestScreeningDialog = false
                    }
                }
            )
        }

        testScreeningResult?.let { (number, reason) ->
            val isBlocked = reason != null
            AlertDialog(
                onDismissRequest = { testScreeningResult = null },
                title = { Text(stringResource(R.string.test_screening_title)) },
                text = {
                    Column {
                        Text(
                            text = if (isBlocked) stringResource(if (reason == Reason.FIND_MY_PHONE) R.string.test_screening_result_blocked_find_by_phone else R.string.test_screening_result_blocked, number, HistoryViewModel.getReasonString(context, reason))
                                   else stringResource(R.string.test_screening_result_not_blocked, number),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { testScreeningResult = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

    }

    if (showReadContactsPermissionRationale) {
        AlertDialog(
            onDismissRequest = {
                showReadContactsPermissionRationale = false
            },
            title = { Text(stringResource(R.string.permission_read_contacts_rationale_title)) },
            text = { Text(stringResource(R.string.permission_read_contacts_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadContactsPermissionRationale = false
                    val alreadyRequested = hasRequestedContacts
                    hasRequestedContacts = true
                    viewModel.contactsPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReadContactsPermissionRationale = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showReadCallLogRationale) {
        AlertDialog(
            onDismissRequest = {
                showReadCallLogRationale = false
                pendingFindMyPhoneToggle = null
            },
            title = { Text(stringResource(R.string.permission_call_log_rationale_title)) },
            text = { Text(stringResource(R.string.permission_call_log_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadCallLogRationale = false
                    val alreadyRequested = hasRequestedCallsLog
                    hasRequestedCallsLog = true
                    viewModel.callsLogPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReadCallLogRationale = false
                    pendingFindMyPhoneToggle = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPostNotificationsRationale) {
        AlertDialog(
            onDismissRequest = { showPostNotificationsRationale = false },
            title = { Text(stringResource(R.string.permission_notifications_rationale_title)) },
            text = { Text(stringResource(R.string.permission_notifications_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPostNotificationsRationale = false
                    val alreadyRequested = hasRequestedNotifications
                    hasRequestedNotifications = true
                    viewModel.notificationsPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostNotificationsRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showReadPhoneStateRationale) {
        AlertDialog(
            onDismissRequest = { showReadPhoneStateRationale = false },
            title = { Text(stringResource(R.string.permission_read_phone_state_rationale_title)) },
            text = { Text(stringResource(R.string.permission_read_phone_state_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadPhoneStateRationale = false
                    val alreadyRequested = hasRequestedPhoneState
                    hasRequestedPhoneState = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReadPhoneStateRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDefaultCountryDialog) {
        CountryPickerDialog(
            title = stringResource(R.string.default_country_title),
            getCountryName = viewModel::getCountryName,
            mode = CountryPickerMode.Single(
                onSelect = { country ->
                    defaultCountryIso = country.iso
                    viewModel.defaultCountryIso = country.iso
                    showDefaultCountryDialog = false
                }
            ),
            onDismiss = { showDefaultCountryDialog = false },
        )
    }

    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri?.let { uri -> viewModel.restore(uri) }
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    errorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorDialogMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
