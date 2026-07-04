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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.countries

sealed interface CountryPickerMode {
    data class Single(val onSelect: (Country) -> Unit) : CountryPickerMode
    data class Multi(
        val selectedIsos: Set<String>,
        val onSave: (Set<String>) -> Unit,
        val lockedIsos: Set<String> = emptySet(),
    ) : CountryPickerMode
}

@Composable
fun CountryPickerDialog(
    title: String,
    getCountryName: (Country) -> String,
    mode: CountryPickerMode,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var multiSelection by remember {
        mutableStateOf(
            when (mode) {
                is CountryPickerMode.Multi -> mode.selectedIsos
                else -> emptySet()
            }
        )
    }

    val sortedCountries = remember {
        countries.sortedBy { getCountryName(it) }
    }

    val filtered = remember(search, multiSelection) {
        val base = if (search.isBlank()) sortedCountries
        else {
            val q = search.lowercase()
            sortedCountries.filter {
                getCountryName(it).lowercase().contains(q) ||
                    it.code.contains(q) ||
                    it.iso.lowercase().contains(q)
            }
        }
        if (mode is CountryPickerMode.Multi && search.isBlank()) {
            base.sortedByDescending { it.iso in multiSelection }
        } else {
            base
        }
    }

    val multiCoveredCodes = remember(multiSelection, mode) {
        if (mode is CountryPickerMode.Multi) {
            val covered = multiSelection + mode.lockedIsos
            countries.filter { it.iso in covered }.map { it.code }.toSet()
        } else emptySet()
    }

    val hasTopBar = mode is CountryPickerMode.Multi

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (hasTopBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (multiSelection.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_selected,
                                multiSelection.size,
                                multiSelection.size
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
                        onClick = { mode.onSave(multiSelection) },
                        enabled = multiSelection.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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
                itemsIndexed(filtered) { _, country ->
                    when (mode) {
                        is CountryPickerMode.Single -> {
                            SingleInstantRow(
                                country = country,
                                getCountryName = getCountryName,
                                onClick = {
                                    mode.onSelect(country)
                                    onDismiss()
                                }
                            )
                        }

                        is CountryPickerMode.Multi -> {
                            val checked = multiSelection.contains(country.iso)
                            val coveredByOther =
                                !checked && country.code in multiCoveredCodes
                            val locked = country.iso in mode.lockedIsos
                            MultiSelectRow(
                                country = country,
                                checked = checked || coveredByOther || locked,
                                enabled = !coveredByOther && !locked,
                                getCountryName = getCountryName,
                                onClick = {
                                    multiSelection = if (checked)
                                        multiSelection - country.iso
                                    else
                                        multiSelection + country.iso
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleInstantRow(
    country: Country,
    getCountryName: (Country) -> String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(country.flag, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.width(12.dp))

        Text(
            text = getCountryName(country),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "+${country.code}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MultiSelectRow(
    country: Country,
    checked: Boolean,
    enabled: Boolean,
    getCountryName: (Country) -> String,
    onClick: () -> Unit,
) {
    val alpha = if (!enabled) 0.38f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onClick() }
        )

        Text(country.flag, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.width(12.dp))

        Text(
            text = getCountryName(country),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "+${country.code}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
