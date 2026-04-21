package com.moneykeeper.feature.auth.ui.migration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R
import com.moneykeeper.feature.auth.ui.components.PinDots
import com.moneykeeper.feature.auth.ui.components.PinKeypad

@Composable
fun MigrationScreen(
    onMigrated: () -> Unit,
    onCorrupted: (String) -> Unit,
    viewModel: MigrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onMigrated()
    }
    LaunchedEffect(uiState.error) {
        if (uiState.error is MigrationError.DataCorrupted) {
            onCorrupted((uiState.error as MigrationError.DataCorrupted).message)
        }
    }

    if (uiState.showPinSetup) {
        PinSetupStep(
            error = uiState.error,
            isLoading = uiState.isLoading,
            onSubmit = { pin, confirm -> viewModel.setupPin(pin, confirm) },
            onClearError = { viewModel.clearError() },
        )
    } else {
        PasswordStep(
            error = uiState.error,
            isLoading = uiState.isLoading,
            onSubmit = { viewModel.submitPassword(it) },
            onClearError = { viewModel.clearError() },
        )
    }
}

@Composable
private fun PasswordStep(
    error: MigrationError?,
    isLoading: Boolean,
    onSubmit: (CharArray) -> Unit,
    onClearError: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.migration_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.migration_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text(stringResource(R.string.migration_password_label)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (!isLoading && password.isNotEmpty()) onSubmit(password.toCharArray())
            }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                }
            },
            isError = error is MigrationError.WrongPassword,
            supportingText = if (error is MigrationError.WrongPassword) {
                { Text(stringResource(R.string.migration_error_wrong)) }
            } else null,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(password.toCharArray()) },
            enabled = password.isNotEmpty() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.migration_button))
        }
    }
}

@Composable
private fun PinSetupStep(
    error: MigrationError?,
    isLoading: Boolean,
    onSubmit: (CharArray, CharArray) -> Unit,
    onClearError: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmMode by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (confirmMode) stringResource(R.string.setup_pin_confirm_title)
                   else stringResource(R.string.setup_pin_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (confirmMode) stringResource(R.string.setup_pin_confirm_subtitle)
                   else stringResource(R.string.setup_pin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        PinDots(count = pin.length)

        (error as? MigrationError.PinMismatch)?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_pin_error_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        (error as? MigrationError.PinTooShort)?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_pin_error_too_short), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        PinKeypad(
            onDigit = { digit ->
                if (pin.length < 4) {
                    pin += digit
                    onClearError()
                    if (pin.length == 4) {
                        if (!confirmMode) { firstPin = pin; pin = ""; confirmMode = true }
                        else { onSubmit(firstPin.toCharArray(), pin.toCharArray()); firstPin = ""; pin = "" }
                    }
                }
            },
            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
    }
}
