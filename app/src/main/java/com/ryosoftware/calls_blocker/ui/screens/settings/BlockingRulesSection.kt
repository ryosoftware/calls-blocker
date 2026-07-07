package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.ContactGroup
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.countries

@Composable
fun BlockingRulesSection(
    blockAll: Boolean,
    onBlockAllChange: (Boolean) -> Unit,
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
    contactGroups: List<ContactGroup>,
    contactsPermissionGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    getCountryName: (Country) -> String,
    onShowGroupDialog: () -> Unit,
    onShowCountryDialog: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlockAllChange(!blockAll) },
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.block_all_title),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = stringResource(R.string.block_all_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(24.dp))

                Switch(
                    checked = blockAll,
                    onCheckedChange = null
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlockHiddenChange(!blockHidden) },
                verticalAlignment = Alignment.Top
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
        canShowPermissionNotAllowed = blockUnknown,
        permission = Manifest.permission.READ_CONTACTS,
        isPermissionAllowed = contactsPermissionGranted,
        onShowRationaleRequested = onRequestContactsPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockUnknownChange(!blockUnknown) },
            verticalAlignment = Alignment.Top
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
            verticalAlignment = Alignment.Top
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
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.no_groups_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedGroups.forEach { group ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    val newIds = selectedIds - group.groupId
                                    onBlockedGroupIdsChange(newIds.joinToString(","))
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove_element_description, group.title),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { onShowGroupDialog() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_description),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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
                verticalAlignment = Alignment.Top
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
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(R.color.status_inactive_text)
                    )

                    Spacer(Modifier.height(8.dp))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedCountries.forEach { country ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.country_name_and_flag, getCountryName(country), country.flag),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(end = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        val newIsos = selectedIsos - country.iso
                                        onAllowedCountryIsosChange(newIsos.joinToString(","))
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove_element_description, getCountryName(country)),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { onShowCountryDialog() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_description),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
