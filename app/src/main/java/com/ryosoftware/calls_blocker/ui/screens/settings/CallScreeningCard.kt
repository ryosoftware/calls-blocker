package com.ryosoftware.calls_blocker.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R

@Composable
fun CallScreeningStatusCard(
    enabled: Boolean,
    onEnable: () -> Unit,
    contactsPermissionGranted: Boolean,
    phoneStatePermissionGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit
) {
    val containerColor = if (enabled) {
        colorResource(R.color.status_active_container)
    } else {
        colorResource(R.color.status_inactive_container)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.VerifiedUser else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (enabled) R.string.screening_active_title
                            else R.string.screening_inactive_title
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = if (enabled) stringResource(R.string.screening_active_description, stringResource(R.string.app_name))
                               else stringResource(R.string.screening_inactive_description, stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!enabled) {
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onEnable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.screening_enable_button))
                }
            }

            if (enabled && (!contactsPermissionGranted || !phoneStatePermissionGranted)) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.screening_permissions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onRequestPhoneStatePermission,
                    enabled = !phoneStatePermissionGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_read_phone_state_required))
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onRequestContactsPermission,
                    enabled = !contactsPermissionGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_read_contacts_required))
                }
            }
        }
    }
}
