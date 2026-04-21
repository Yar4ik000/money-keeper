package com.moneykeeper.feature.auth.ui.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MigrationToPin
import com.moneykeeper.feature.auth.domain.PinVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val migrationToPin: MigrationToPin,
    private val pinVerifier: PinVerifier,
    private val keyStorage: DatabaseKeyStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrationUiState())
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    fun submitPassword(password: CharArray) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = migrationToPin.migrate(password)) {
            MigrationToPin.Result.ReadyForPinSetup ->
                _uiState.update { it.copy(isLoading = false, showPinSetup = true) }
            MigrationToPin.Result.WrongPassword ->
                _uiState.update { it.copy(isLoading = false, error = MigrationError.WrongPassword) }
            is MigrationToPin.Result.DataCorrupted ->
                _uiState.update { it.copy(isLoading = false, error = MigrationError.DataCorrupted(result.message)) }
            is MigrationToPin.Result.Error ->
                _uiState.update { it.copy(isLoading = false, error = MigrationError.Unknown(result.message)) }
        }
    }

    fun setupPin(pin: CharArray, confirmation: CharArray) = viewModelScope.launch {
        if (!pin.contentEquals(confirmation)) {
            _uiState.update { it.copy(error = MigrationError.PinMismatch) }
            pin.fill(0.toChar()); confirmation.fill(0.toChar())
            return@launch
        }
        if (pin.size < PinVerifier.MIN_LENGTH) {
            _uiState.update { it.copy(error = MigrationError.PinTooShort) }
            pin.fill(0.toChar()); confirmation.fill(0.toChar())
            return@launch
        }
        pinVerifier.setPin(pin)
        pin.fill(0.toChar()); confirmation.fill(0.toChar())
        _uiState.update { it.copy(done = true) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class MigrationUiState(
    val isLoading: Boolean = false,
    val error: MigrationError? = null,
    val showPinSetup: Boolean = false,
    val done: Boolean = false,
)

sealed interface MigrationError {
    data object WrongPassword : MigrationError
    data object PinMismatch : MigrationError
    data object PinTooShort : MigrationError
    data class DataCorrupted(val message: String) : MigrationError
    data class Unknown(val message: String) : MigrationError
}
