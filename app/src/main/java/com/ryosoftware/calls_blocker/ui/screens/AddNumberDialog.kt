package com.ryosoftware.calls_blocker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.countries
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.viewmodel.NumbersViewModel.AddNumberError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNumberDialog(
    title: String = stringResource(R.string.add_number_title),
    getCountryName: (Country) -> String,
    defaultCountryIso: String = "",
    showBlockType: Boolean = true,
    showDescription: Boolean = true,
    showActionSelector: Boolean = false,
    addNumberError: AddNumberError? = null,
    confirmButtonText: String = stringResource(R.string.save),
    onDismiss: () -> Unit,
    onConfirm: (String, String, Action, Type) -> Unit
) {
    val initialCountry = remember(defaultCountryIso) {
        defaultCountryIso.let { iso ->
            if (iso.isNotEmpty()) countries.firstOrNull { it.iso == iso }
            else null
        }
    }
    var selectedCountry by remember { mutableStateOf(initialCountry) }
    var countrySearchText by remember { mutableStateOf(initialCountry?.let { "${it.flag}  ${getCountryName(it)}" } ?: "") }
    var countryCode by remember { mutableStateOf(initialCountry?.code ?: "") }
    var phoneNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var blockType by remember { mutableStateOf(Type.EXACT_COINCIDENCE) }
    var action by remember { mutableStateOf(Action.BLOCK) }
    var showCountryPicker by remember { mutableStateOf(false) }

    val prefixIsValid = countryCode.isNotBlank() && countries.any { it.code == countryCode }
    val canSave = selectedCountry?.let { countryCode == it.code && phoneNumber.isNotBlank() } ?: false
    var focusPhoneTrigger by remember { mutableIntStateOf(0) }

    fun selectCountry(country: Country) {
        selectedCountry = country
        countrySearchText = "${country.flag}  ${getCountryName(country)}"
        countryCode = country.code
    }

    LaunchedEffect(countryCode) {
        if (countryCode.isNotBlank()) {
            val match = countries.firstOrNull { it.code == countryCode }
            if (match != null) {
                if (match.iso != selectedCountry?.iso) {
                    selectCountry(match)
                    focusPhoneTrigger++
                }
                return@LaunchedEffect
            }
        }
        selectedCountry = null
        countrySearchText = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = countrySearchText,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.country_label)) },
                    trailingIcon = {
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCountryPicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(Modifier.height(12.dp))

                CombinedPhoneInput(
                    countryCode = countryCode,
                    phoneNumber = phoneNumber,
                    onCountryCodeChange = { countryCode = it.filter { c -> c.isDigit() } },
                    onPhoneNumberChange = { phoneNumber = it },
                    isError = countryCode.isNotBlank() && !prefixIsValid,
                    focusPhoneTrigger = focusPhoneTrigger,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showDescription) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.description_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (showActionSelector) {
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.action_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action = Action.BLOCK }
                    ) {
                        RadioButton(
                            selected = action == Action.BLOCK,
                            onClick = { action = Action.BLOCK }
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(stringResource(R.string.action_block))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action = Action.ALLOW }
                    ) {
                        RadioButton(
                            selected = action == Action.ALLOW,
                            onClick = { action = Action.ALLOW }
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(stringResource(R.string.action_allow))
                    }
                }

                if (showBlockType) {
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.number_type_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { blockType = Type.EXACT_COINCIDENCE }
                    ) {
                        RadioButton(
                            selected = blockType == Type.EXACT_COINCIDENCE,
                            onClick = { blockType = Type.EXACT_COINCIDENCE }
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(stringResource(R.string.number_type_exact))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { blockType = Type.PREFIX }
                    ) {
                        RadioButton(
                            selected = blockType == Type.PREFIX,
                            onClick = { blockType = Type.PREFIX }
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(stringResource(R.string.number_type_prefix))
                    }
                }

                val errorText = when (addNumberError) {
                    is AddNumberError.DuplicateExact -> stringResource(R.string.error_duplicate_exact)
                    is AddNumberError.DuplicatePrefix -> stringResource(R.string.error_duplicate_prefix)
                    null -> null
                }
                if (errorText != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm("+$countryCode$phoneNumber", description, action, blockType) },
                enabled = canSave
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showCountryPicker) {
        CountryPickerDialog(
            title = stringResource(R.string.country_label),
            getCountryName = getCountryName,
            mode = CountryPickerMode.Single(
                onSelect = { country ->
                    selectCountry(country)
                    showCountryPicker = false
                }
            ),
            onDismiss = { showCountryPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinedPhoneInput(
    countryCode: String,
    phoneNumber: String,
    onCountryCodeChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    focusPhoneTrigger: Int = 0
) {
    val shape = MaterialTheme.shapes.small
    var isFocused by remember { mutableStateOf(false) }
    val phoneFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }

    var codeValue by remember {
        mutableStateOf(TextFieldValue(countryCode, TextRange(countryCode.length)))
    }
    var phoneValue by remember {
        mutableStateOf(TextFieldValue(phoneNumber, TextRange(phoneNumber.length)))
    }

    LaunchedEffect(codeValue.text) {
        if (codeValue.text != countryCode) onCountryCodeChange(codeValue.text)
    }
    LaunchedEffect(phoneValue.text) {
        if (phoneValue.text != phoneNumber) onPhoneNumberChange(phoneValue.text)
    }

    LaunchedEffect(countryCode) {
        if (codeValue.text != countryCode) {
            codeValue = TextFieldValue(countryCode, TextRange(countryCode.length))
        }
    }
    LaunchedEffect(phoneNumber) {
        if (phoneValue.text != phoneNumber) {
            phoneValue = TextFieldValue(phoneNumber, TextRange(phoneNumber.length))
        }
    }

    LaunchedEffect(focusPhoneTrigger) {
        if (focusPhoneTrigger > 0) {
            phoneFocusRequester.requestFocus()
        }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "borderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        label = "borderWidth"
    )

    val showNumberHint = !isFocused && countryCode.isEmpty() && phoneNumber.isEmpty()

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(shape)
                .border(BorderStroke(borderWidth, borderColor), shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    if (!showNumberHint) phoneFocusRequester.requestFocus()
                    else codeFocusRequester.requestFocus()
                }
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (!showNumberHint) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BasicTextField(
                    value = codeValue,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (showNumberHint && countryCode.isEmpty() && phoneNumber.isEmpty()) {
                                Text(
                                    stringResource(R.string.phone_number_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    onValueChange = {
                        codeValue = it
                    },
                    modifier = Modifier
                        .focusRequester(codeFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionRight
                            ) {
                                val sel = codeValue.selection

                                if (sel.collapsed && sel.start == codeValue.text.length) {
                                    phoneFocusRequester.requestFocus(); true
                                } else { false }
                            } else {
                                false
                            }
                        }
                        .then(
                            if (showNumberHint) Modifier.weight(1f)
                            else Modifier.width(48.dp)
                        )
                )

                if (!showNumberHint) {
                    Text(
                        "|",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    BasicTextField(
                        value = phoneValue,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        onValueChange = { phoneValue = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(phoneFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                    val sel = phoneValue.selection

                                    if (sel.collapsed && sel.start == 0) { codeFocusRequester.requestFocus(); true
                                    } else { false }
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }
    }
}
