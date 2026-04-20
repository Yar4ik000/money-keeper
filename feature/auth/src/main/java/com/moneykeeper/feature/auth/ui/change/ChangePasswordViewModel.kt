package com.moneykeeper.feature.auth.ui.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.BiometricEnrollment
import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import com.moneykeeper.feature.auth.domain.aesGcmDecrypt
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
class ChangePasswordViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val derivation: MasterKeyDerivation,
    private val masterKeyHolder: MasterKeyHolder,
    private val biometricEnrollment: BiometricEnrollment,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeUiState())
    val uiState = _uiState.asStateFlow()

    fun onSubmit(oldPw: CharArray, newPw: CharArray, confirm: CharArray) = viewModelScope.launch(Dispatchers.Default) {
        try {
            if (!newPw.contentEquals(confirm)) {
                _uiState.update { it.copy(error = ChangeError.Mismatch) }; return@launch
            }
            if (newPw.size < 6) {
                _uiState.update { it.copy(error = ChangeError.TooShort) }; return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            val salt = keyStorage.readOrCreateKdfSalt()
            val params = keyStorage.readKdfParams()
            val oldDerived = derivation.derive(oldPw, salt, params.iterations, params.memoryKb, params.parallelism)

            if (!oldDerived.contentEquals(masterKeyHolder.require())) {
                oldDerived.fill(0)
                _uiState.update { it.copy(isLoading = false, error = ChangeError.WrongOldPassword) }
                return@launch
            }

            val enc = keyStorage.readEncryptedDbKey()
            if (enc == null) {
                oldDerived.fill(0)
                _uiState.update { it.copy(isLoading = false, error = ChangeError.DataCorrupted) }
                return@launch
            }
            val dbKey = aesGcmDecrypt(enc.ciphertext, oldDerived, enc.iv)
            oldDerived.fill(0)

            val newDerived = derivation.derive(newPw, salt, params.iterations, params.memoryKb, params.parallelism)
            val newIv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val newEncDbKey = aesGcmEncrypt(dbKey, newDerived, newIv)
            keyStorage.writeEncryptedDbKey(newEncDbKey, newIv)
            dbKey.fill(0)

            masterKeyHolder.set(newDerived)
            newDerived.fill(0)

            if (biometricEnrollment.isEnrolled()) {
                biometricEnrollment.disable()
            }

            _uiState.update { it.copy(isLoading = false, showOldBackupWarning = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = ChangeError.Unknown(e.message)) }
        } finally {
            oldPw.fill(0.toChar()); newPw.fill(0.toChar()); confirm.fill(0.toChar())
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun dismissOldBackupDialog() = _uiState.update { it.copy(showOldBackupWarning = false, done = true) }
}

data class ChangeUiState(
    val isLoading: Boolean = false,
    val error: ChangeError? = null,
    val done: Boolean = false,
    val showOldBackupWarning: Boolean = false,
)

sealed interface ChangeError {
    data object Mismatch : ChangeError
    data object TooShort : ChangeError
    data object WrongOldPassword : ChangeError
    data object DataCorrupted : ChangeError
    data class Unknown(val message: String?) : ChangeError
}
