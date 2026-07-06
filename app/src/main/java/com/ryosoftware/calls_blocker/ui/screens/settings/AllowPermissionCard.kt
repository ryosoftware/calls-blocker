package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R

data class PermissionUiState(
    val permission: String,
    val isPermissionAllowed: Boolean,
    val onShowRationaleRequested: () -> Unit
)

@Composable
fun AllowPermissionCard(
    canShowPermissionNotAllowed: Boolean,
    permissions: List<PermissionUiState>,
    content: @Composable ColumnScope.() -> Unit
) {
    val hasMissingPermission = permissions.any { !it.isPermissionAllowed }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (canShowPermissionNotAllowed && hasMissingPermission) {
            CardDefaults.cardColors(
                containerColor = colorResource(R.color.status_inactive_container)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        if (canShowPermissionNotAllowed) {
            permissions
                .filter { !it.isPermissionAllowed }
                .forEach { permissionState ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorResource(R.color.status_inactive_text)
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = when (permissionState.permission) {
                                Manifest.permission.READ_CALL_LOG ->
                                    stringResource(R.string.permission_call_log_required)

                                Manifest.permission.READ_CONTACTS ->
                                    stringResource(R.string.permission_read_contacts_required)

                                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                                    stringResource(R.string.permission_ignore_battery_optimizations_required)

                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(R.color.status_inactive_text)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = permissionState.onShowRationaleRequested,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }

                    Spacer(Modifier.height(8.dp))
                }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun AllowPermissionCard(
    canShowPermissionNotAllowed: Boolean,
    permission: String,
    isPermissionAllowed: Boolean,
    onShowRationaleRequested: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {

    val permissionUiState = PermissionUiState(
        permission = permission,
        isPermissionAllowed = isPermissionAllowed,
        onShowRationaleRequested = onShowRationaleRequested
    )

    AllowPermissionCard(canShowPermissionNotAllowed, listOf(permissionUiState), content)
}
