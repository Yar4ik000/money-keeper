package com.moneykeeper.feature.auth.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
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
class SetupPasswordViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val derivation: MasterKeyDerivation,
    private val masterKeyHolder: MasterKeyHolder,
    private val databaseProvider: DatabaseProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    fun onSubmit(password: CharArray, confirmation: CharArray) = viewModelScope.launch(Dispatchers.Default) {
        var dbKey: ByteArray? = null
        var derivedKey: ByteArray? = null
        try {
            if (!password.contentEquals(confirmation)) {
                _uiState.update { it.copy(error = SetupError.Mismatch) }; return@launch
            }
            if (password.size < 6) {
                _uiState.update { it.copy(error = SetupError.TooShort) }; return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            val salt = keyStorage.readOrCreateKdfSalt()
            val kdfParams = DatabaseKeyStorage.KdfParams(
                iterations  = DatabaseKeyStorage.KdfParams.DEFAULT_ITERATIONS,
                memoryKb    = DatabaseKeyStorage.KdfParams.DEFAULT_MEMORY_KB,
                parallelism = DatabaseKeyStorage.KdfParams.DEFAULT_PARALLELISM,
            )
            keyStorage.writeKdfParams(kdfParams)
            derivedKey = derivation.derive(password, salt, kdfParams.iterations, kdfParams.memoryKb, kdfParams.parallelism)

            dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val encryptedDbKey = aesGcmEncrypt(dbKey, derivedKey, iv)
            keyStorage.writeEncryptedDbKey(encryptedDbKey, iv)

            masterKeyHolder.set(derivedKey)
            databaseProvider.initialize(dbKey)

            _uiState.update { it.copy(isLoading = false, done = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = SetupError.Unknown(e.message)) }
        } finally {
            password.fill(0.toChar())
            confirmation.fill(0.toChar())
            dbKey?.fill(0)
            // derivedKey не затираем — он живёт в masterKeyHolder
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class SetupUiState(
    val isLoading: Boolean = false,
    val error: SetupError? = null,
    val done: Boolean = false,
)

sealed interface SetupError {
    data object Mismatch : SetupError
    data object TooShort : SetupError
    data class Unknown(val message: String?) : SetupError
}
