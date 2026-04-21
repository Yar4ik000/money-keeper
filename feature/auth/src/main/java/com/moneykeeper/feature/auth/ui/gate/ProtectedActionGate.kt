package com.moneykeeper.feature.auth.ui.gate

import androidx.compose.foundation.layout.*
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

/**
 * Returns a lambda that, when invoked, requires the user to authenticate (biometric or PIN)
 * before calling [onGranted]. A PIN fallback dialog is rendered inline.
 *
 * Each call site must pass a unique [key] so that concurrent protected actions maintain
 * independent ViewModel state.
 */
@Composable
fun rememberProtectedAction(
    key: String,
    onGranted: () -> Unit,
    viewModel: ProtectedActionViewModel = hiltViewModel(key = key),
): () -> Unit {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    val currentOnGranted by rememberUpdatedState(onGranted)

    if (state is ProtectedActionState.AwaitingPin) {
        PinConfirmDialog(
            wrongPin = (state as ProtectedActionState.AwaitingPin).wrongPin,
            onPinEntered = viewModel::verifyPin,
            onDismiss = viewModel::cancel,
        )
    }

    LaunchedEffect(state) {
        if (state is ProtectedActionState.Granted) {
            currentOnGranted()
            viewModel.reset()
        }
    }

    return remember(viewModel, activity) { { viewModel.start(activity) } }
}

@Composable
private fun PinConfirmDialog(
    wrongPin: Boolean,
    onPinEntered: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDismiss(); pin = "" },
        title = { Text(stringResource(R.string.protected_action_pin_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.protected_action_pin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PinDots(count = pin.length)
                if (wrongPin) {
                    Text(
                        stringResource(R.string.unlock_pin_error_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PinKeypad(
                    onDigit = { if (pin.length < 6) pin += it },
                    onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onConfirm = {
                        if (pin.length >= 4) {
                            onPinEntered(pin.toCharArray())
                            pin = ""
                        }
                    },
                    confirmEnabled = pin.length >= 4,
                )
            }
        },
        confirmButton = {},
    )
}
