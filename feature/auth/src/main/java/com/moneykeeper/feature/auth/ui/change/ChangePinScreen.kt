package com.moneykeeper.feature.auth.ui.change

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onChanged()
    }

    var pin by remember { mutableStateOf("") }
    var confirmMode by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }

    LaunchedEffect(uiState.step) {
        pin = ""; confirmMode = false; firstPin = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_pin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val stepTitle = when {
                uiState.step == ChangePinStep.CURRENT -> stringResource(R.string.change_pin_current_step_title)
                confirmMode -> stringResource(R.string.change_pin_confirm_step_title)
                else -> stringResource(R.string.change_pin_new_step_title)
            }
            Text(stepTitle, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))

            PinDots(count = pin.length, maxLength = 6)

            uiState.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (error) {
                        ChangePinError.WrongCurrentPin -> stringResource(R.string.change_pin_error_wrong_current)
                        ChangePinError.TooShort        -> stringResource(R.string.setup_pin_error_too_short)
                        ChangePinError.Mismatch        -> stringResource(R.string.setup_pin_error_mismatch)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))

            PinKeypad(
                onDigit = { digit ->
                    if (pin.length < 6) { pin += digit; viewModel.clearError() }
                },
                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = {
                    if (pin.length >= 4) {
                        when {
                            uiState.step == ChangePinStep.CURRENT -> {
                                viewModel.verifyCurrentPin(pin.toCharArray())
                                pin = ""
                            }
                            !confirmMode -> { firstPin = pin; pin = ""; confirmMode = true }
                            else -> {
                                viewModel.submitNewPin(firstPin.toCharArray(), pin.toCharArray())
                                firstPin = ""; pin = ""
                            }
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
}
