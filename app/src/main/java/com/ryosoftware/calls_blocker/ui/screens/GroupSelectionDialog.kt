package com.ryosoftware.calls_blocker.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.ContactGroup

@Composable
fun GroupSelectionDialog(
    groups: List<ContactGroup>,
    selectedIds: Set<Long>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(selectedIds) }

    val filtered = remember(search) {
        if (search.isBlank()) groups
        else {
            val q = search.lowercase()
            groups.filter { it.title.lowercase().contains(q) }
        }
    }

    val sorted = remember(selection, search) {
        if (search.isBlank()) {
            filtered.sortedByDescending { it.groupId in selection }
        } else {
            filtered
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selection.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.n_selected,
                            selection.size,
                            selection.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }

                TextButton(
                    onClick = { onSave(selection) },
                    enabled = selection.isNotEmpty()
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(sorted) { _, group ->
                    GroupRow(
                        group = group,
                        checked = group.groupId in selection,
                        onClick = {
                            selection = if (group.groupId in selection)
                                selection - group.groupId
                            else
                                selection + group.groupId
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: ContactGroup,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() }
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = if (group.memberCount == 0) stringResource(R.string.group_contacts_count_zero)
                       else pluralStringResource(R.plurals.group_contacts_count, group.memberCount, group.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
