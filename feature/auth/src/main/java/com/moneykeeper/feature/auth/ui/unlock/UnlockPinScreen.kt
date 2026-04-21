package com.moneykeeper.feature.auth.ui.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R
import com.moneykeeper.feature.auth.ui.components.PinDots
import com.moneykeeper.feature.auth.ui.components.PinKeypad

@Composable
fun UnlockPinScreen(
    onUnlocked: () -> Unit,
    onCorrupted: (String) -> Unit,
    viewModel: UnlockPinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) onUnlocked()
    }
    LaunchedEffect(uiState.corruptedMessage) {
        uiState.corruptedMessage?.let { onCorrupted(it) }
    }

    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.unlock_pin_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(32.dp))

        PinDots(count = pin.length, maxLength = 6)

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (error) {
                    is UnlockPinError.WrongPin      -> stringResource(R.string.unlock_pin_error_wrong)
                    is UnlockPinError.BiometricStale -> stringResource(R.string.unlock_error_biometric_stale)
                    is UnlockPinError.LockedOut     -> stringResource(R.string.unlock_error_locked_out, error.secondsRemaining)
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
                    // auto-submit at MAX_LENGTH
                    if (pin.length == 6) {
                        viewModel.onPinSubmit(pin.toCharArray())
                        pin = ""
                    }
                }
            },
            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            onConfirm = {
                if (pin.length >= 4) {
                    viewModel.onPinSubmit(pin.toCharArray())
                    pin = ""
                }
            },
            confirmEnabled = pin.length >= 4 && !uiState.isLoading,
        )

        if (viewModel.isBiometricAvailable) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.onBiometricClick(activity) },
                enabled = !uiState.isLoading,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.unlock_button_biometric))
            }
        }

        if (uiState.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}
