package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.ContactGroup
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.countries

@Composable
fun BlockingRulesSection(
    blockUnknown: Boolean,
    onBlockUnknownChange: (Boolean) -> Unit,
    blockHidden: Boolean,
    onBlockHiddenChange: (Boolean) -> Unit,
    blockGroups: Boolean,
    onBlockGroupsChange: (Boolean) -> Unit,
    blockedGroupIds: String,
    onBlockedGroupIdsChange: (String) -> Unit,
    blockInternational: Boolean,
    onBlockInternationalChange: (Boolean) -> Unit,
    allowedCountryIsos: String,
    onAllowedCountryIsosChange: (String) -> Unit,
    defaultCountryIso: String,
    contactGroups: List<ContactGroup>,
    contactsPermissionGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    getCountryName: (Country) -> String,
    onShowGroupDialog: () -> Unit,
    onShowCountryDialog: () -> Unit,
) {
    AllowPermissionCard(
        canShowPermissionNotAllowed = blockUnknown,
        permission = Manifest.permission.READ_CONTACTS,
        isPermissionAllowed = contactsPermissionGranted,
        onShowRationaleRequested = onRequestContactsPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockUnknownChange(!blockUnknown) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.block_unknown_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.block_unknown_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = blockUnknown,
                onCheckedChange = null
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlockHiddenChange(!blockHidden) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.block_hidden_title),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = stringResource(R.string.block_hidden_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(24.dp))

                Switch(
                    checked = blockHidden,
                    onCheckedChange = null
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    AllowPermissionCard(
        canShowPermissionNotAllowed = blockGroups,
        permission = Manifest.permission.READ_CONTACTS,
        isPermissionAllowed = contactsPermissionGranted,
        onShowRationaleRequested = onRequestContactsPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockGroupsChange(!blockGroups) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.block_groups_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.block_groups_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = blockGroups,
                onCheckedChange = null
            )
        }
        if (blockGroups) {
            val selectedIds = blockedGroupIds.split(",")
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
                .toSet()
            val selectedGroups = contactGroups.filter { it.groupId in selectedIds }

            Spacer(Modifier.height(8.dp))

            if (selectedGroups.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_groups_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.selected_groups),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = selectedGroups.joinToString(", ") { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onShowGroupDialog) {
                Text(stringResource(R.string.select_groups))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlockInternationalChange(!blockInternational) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.block_international_title),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = stringResource(R.string.block_international_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(24.dp))

                Switch(
                    checked = blockInternational,
                    onCheckedChange = null
                )
            }
            if (blockInternational) {
                val selectedIsos = allowedCountryIsos.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val selectedCountries = countries
                    .filter { it.iso in selectedIsos }
                    .sortedBy { getCountryName(it) }

                Spacer(Modifier.height(8.dp))

                if (selectedCountries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_allowed_countries_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = stringResource(R.string.select_allowed_countries),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = selectedCountries.joinToString(", ") {
                            "${it.flag} ${getCountryName(it)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onShowCountryDialog) {
                    Text(stringResource(R.string.select_countries))
                }
            }
        }
    }
}
