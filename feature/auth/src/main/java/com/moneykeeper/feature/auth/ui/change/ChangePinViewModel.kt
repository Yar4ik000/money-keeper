package com.moneykeeper.feature.auth.ui.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.feature.auth.domain.PinVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val pinVerifier: PinVerifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePinUiState())
    val uiState = _uiState.asStateFlow()

    fun verifyCurrentPin(pin: CharArray) = viewModelScope.launch(Dispatchers.Default) {
        if (pinVerifier.verify(pin)) {
            _uiState.update { it.copy(step = ChangePinStep.NEW, error = null) }
        } else {
            _uiState.update { it.copy(error = ChangePinError.WrongCurrentPin) }
        }
        pin.fill(0.toChar())
    }

    fun submitNewPin(pin: CharArray, confirm: CharArray) = viewModelScope.launch(Dispatchers.Default) {
        try {
            if (pin.size < PinVerifier.MIN_LENGTH) {
                _uiState.update { it.copy(error = ChangePinError.TooShort) }
                return@launch
            }
            if (!pin.contentEquals(confirm)) {
                _uiState.update { it.copy(error = ChangePinError.Mismatch) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            pinVerifier.setPin(pin)
            _uiState.update { it.copy(isLoading = false, done = true) }
        } finally {
            pin.fill(0.toChar()); confirm.fill(0.toChar())
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

enum class ChangePinStep { CURRENT, NEW }

data class ChangePinUiState(
    val step: ChangePinStep = ChangePinStep.CURRENT,
    val isLoading: Boolean = false,
    val error: ChangePinError? = null,
    val done: Boolean = false,
)

sealed interface ChangePinError {
    data object WrongCurrentPin : ChangePinError
    data object TooShort : ChangePinError
    data object Mismatch : ChangePinError
}
