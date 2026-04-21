package com.moneykeeper.feature.auth.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R
import com.moneykeeper.feature.auth.ui.components.PinDots
import com.moneykeeper.feature.auth.ui.components.PinKeypad

@Composable
fun SetupPinScreen(
    onPinSet: () -> Unit,
    viewModel: SetupPinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onPinSet()
    }

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

        PinDots(count = pin.length, maxLength = 6)

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (error) {
                    SetupPinError.Mismatch   -> stringResource(R.string.setup_pin_error_mismatch)
                    SetupPinError.TooShort   -> stringResource(R.string.setup_pin_error_too_short)
                    is SetupPinError.Unknown -> error.message ?: stringResource(R.string.setup_error_unknown)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))

        PinKeypad(
            onDigit = { digit ->
                if (pin.length < 6) {
                    pin += digit
                    viewModel.clearError()
                }
            },
            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            onConfirm = {
                if (pin.length >= 4) {
                    if (!confirmMode) {
                        firstPin = pin
                        pin = ""
                        confirmMode = true
                    } else {
                        viewModel.onSubmit(firstPin.toCharArray(), pin.toCharArray())
                        firstPin = ""
                        pin = ""
                    }
                }
            },
            confirmEnabled = pin.length >= 4 && !uiState.isLoading,
        )

        if (uiState.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}
