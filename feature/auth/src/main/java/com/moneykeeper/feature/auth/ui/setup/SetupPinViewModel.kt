package com.moneykeeper.feature.auth.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.KeystoreMasterKeyWrapper
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import com.moneykeeper.feature.auth.domain.PinVerifier
import com.moneykeeper.feature.auth.domain.PostUnlockCallback
import com.moneykeeper.feature.auth.domain.aesGcmEncrypt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class SetupPinViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val masterKeyHolder: MasterKeyHolder,
    private val keystoreWrapper: KeystoreMasterKeyWrapper,
    private val pinVerifier: PinVerifier,
    private val databaseProvider: DatabaseProvider,
    private val postUnlockCallbacks: @JvmSuppressWildcards Set<PostUnlockCallback>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupPinUiState())
    val uiState = _uiState.asStateFlow()

    fun onSubmit(pin: CharArray, confirmation: CharArray) = viewModelScope.launch(Dispatchers.Default) {
        var masterKey: ByteArray? = null
        var dbKey: ByteArray? = null
        try {
            if (!pin.contentEquals(confirmation)) {
                _uiState.update { it.copy(error = SetupPinError.Mismatch) }; return@launch
            }
            if (pin.size != PinVerifier.PIN_LENGTH) {
                _uiState.update { it.copy(error = SetupPinError.TooShort) }; return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }

            // Store db_key wrapped by master_key (v1 format, reused by backup + workers)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            keyStorage.writeEncryptedDbKey(aesGcmEncrypt(dbKey, masterKey, iv), iv)

            // Wrap master_key in Keystore (v2: PIN path)
            keystoreWrapper.wrap(masterKey)

            // Store PIN hash
            pinVerifier.setPin(pin)

            masterKeyHolder.set(masterKey)
            databaseProvider.initialize(dbKey.copyOf())

            postUnlockCallbacks.forEach { it.onUnlocked() }
            _uiState.update { it.copy(isLoading = false, done = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = SetupPinError.Unknown(e.message)) }
        } finally {
            pin.fill(0.toChar())
            confirmation.fill(0.toChar())
            masterKey?.fill(0)
            dbKey?.fill(0)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class SetupPinUiState(
    val isLoading: Boolean = false,
    val error: SetupPinError? = null,
    val done: Boolean = false,
)

sealed interface SetupPinError {
    data object Mismatch : SetupPinError
    data object TooShort : SetupPinError
    data class Unknown(val message: String?) : SetupPinError
}
