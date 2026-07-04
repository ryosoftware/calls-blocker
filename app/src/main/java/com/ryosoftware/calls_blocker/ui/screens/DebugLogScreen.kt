package com.ryosoftware.calls_blocker.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.viewmodel.DebugInfoViewModel
import kotlinx.coroutines.launch

@Composable
fun DebugLogScreen(
    viewModel: DebugInfoViewModel = hiltViewModel(),
    onSaveReady: (() -> Unit) -> Unit = {},
    onShareReady: (() -> Unit) -> Unit = {}
) {
    val logger = viewModel.logger
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoggingEnabled by remember { mutableStateOf(viewModel.isLoggingToFile) }
    var logContents by remember { mutableStateOf("") }
    var showStartLoggingDialog by remember { mutableStateOf(false) }
    var showStopLoggingDialog by remember { mutableStateOf(false) }
    var canShare by remember { mutableStateOf(false) }
    var showObfuscationDialog by remember { mutableStateOf(false) }
    var pendingObfuscateCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val logFileTime by logger.logFileTime.collectAsState()
    var hidePhoneForSave by remember { mutableStateOf(true) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isSaving = true
                try {
                    val content = logger.getLogFileContents(hidePhoneForSave)
                    if (content != null) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(content.joinToString("\n").toByteArray())
                        }
                        Toast.makeText(context, R.string.log_file_has_been_saved, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, R.string.cant_read_log_file, Toast.LENGTH_LONG).show()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    LaunchedEffect(logFileTime, isLoggingEnabled) {
        if (isLoggingEnabled) {
            val atBottom = verticalScroll.value >= verticalScroll.maxValue
            val contents = logger.getLogFileContents(false)

            logContents = contents?.joinToString("\n") ?: ""
            canShare = contents?.isNotEmpty() == true

            if (atBottom) {
                withFrameNanos { }
                verticalScroll.animateScrollTo(verticalScroll.maxValue)
            }
        }
    }

    LaunchedEffect(Unit) {
        val saveHandler: () -> Unit = {
            if (canShare) {
                showObfuscationDialog = true
                pendingObfuscateCallback = { hide ->
                    hidePhoneForSave = hide
                    saveLauncher.launch("debug_log.txt")
                }
            }
        }
        val shareHandler: () -> Unit = {
            showObfuscationDialog = true
            pendingObfuscateCallback = { hide ->
                scope.launch {
                    isSaving = true
                    try {
                        val shareContent = logger.getLogFileContents(hide)
                        if (shareContent == null) {
                            Toast.makeText(context, R.string.cant_read_log_file, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val tempFile = java.io.File(context.cacheDir, "debug_log.txt")
                        tempFile.writeText(shareContent.joinToString("\n"))
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${BuildConfig.APPLICATION_ID}.file_provider", tempFile
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, null))
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.cant_read_log_file, Toast.LENGTH_LONG).show()
                    } finally {
                        isSaving = false
                    }
                }
            }
        }
        onSaveReady(saveHandler)
        onShareReady(shareHandler)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isLoggingEnabled = !isLoggingEnabled
                    viewModel.isLoggingToFile = isLoggingEnabled

                    val logFile = logger.getLogFile()
                    if (isLoggingEnabled && logFile.length() > 0) {
                        showStartLoggingDialog = true
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.log_to_file),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(4.dp))

                val linesCount = if (logContents.isEmpty()) 0 else logContents.lines().size

                @SuppressLint("LocalContextResourcesRead")
                Text(
                    text = pluralStringResource(R.plurals.log_file_messages_count, linesCount, linesCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isLoggingEnabled,
                onCheckedChange = null
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
                    .padding(8.dp)
            ) {
                Text(
                    text = logContents.ifEmpty { stringResource(R.string.no_log_data) },
                    softWrap = false,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }        }
    }

    if (showStartLoggingDialog) {
        AlertDialog(
            onDismissRequest = { showStartLoggingDialog = false },
            title = { Text(stringResource(R.string.logging_dialog_title)) },
            text = { Text(stringResource(R.string.start_logging_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    logger.getLogFile().delete()
                    logger.log("App version is ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) and OS version is ${android.os.Build.VERSION.SDK_INT}")
                    showStartLoggingDialog = false
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartLoggingDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (showStopLoggingDialog) {
        AlertDialog(
            onDismissRequest = { showStopLoggingDialog = false },
            title = { Text(stringResource(R.string.logging_dialog_title)) },
            text = { Text(stringResource(R.string.stop_logging_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    logger.getLogFile().delete()
                    isLoggingEnabled = false
                    viewModel.isLoggingToFile = false
                    showStopLoggingDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopLoggingDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showObfuscationDialog) {
        AlertDialog(
            onDismissRequest = { showObfuscationDialog = false },
            title = { Text(stringResource(R.string.obfuscate_phone_numbers_title)) },
            text = { Text(stringResource(R.string.obfuscate_phone_numbers_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingObfuscateCallback?.invoke(true)
                    showObfuscationDialog = false
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingObfuscateCallback?.invoke(false)
                    showObfuscationDialog = false
                }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (isSaving) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.saving_log_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.saving_log_message))
                }
            },
            confirmButton = {}
        )
    }
}
