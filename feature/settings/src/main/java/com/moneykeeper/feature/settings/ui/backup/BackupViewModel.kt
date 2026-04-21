package com.moneykeeper.feature.settings.ui.backup

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.BackupInfoResult
import com.moneykeeper.core.domain.repository.BackupRepository
import com.moneykeeper.core.domain.repository.BackupResult
import com.moneykeeper.core.domain.repository.RestoreResult
import com.moneykeeper.feature.settings.domain.ExportCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val pendingBackupUri: Uri? = null,
    val pendingRestoreUri: Uri? = null,
    val pendingRestoreCreatedAt: String? = null,
    val pendingRestoreBackupVersion: Int = 1,
    val wrongPassword: Boolean = false,
    val restoreCompleted: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepo: BackupRepository,
    private val exportCsvUseCase: ExportCsvUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, message = null, error = null) }
        runCatching { exportCsvUseCase.export(uri) }
            .onSuccess { _state.update { it.copy(isLoading = false, message = "CSV экспортирован") } }
            .onFailure { t -> _state.update { it.copy(isLoading = false, error = t.message ?: "Ошибка экспорта") } }
    }

    fun onBackupUriPicked(uri: Uri) {
        _state.update { it.copy(pendingBackupUri = uri) }
    }

    fun submitBackupPassword(password: CharArray) = viewModelScope.launch {
        val uri = _state.value.pendingBackupUri ?: return@launch
        _state.update { it.copy(isLoading = true, pendingBackupUri = null, message = null, error = null) }
        when (val result = backupRepo.createBackup(uri, password)) {
            is BackupResult.Success -> _state.update { it.copy(isLoading = false, message = "Резервная копия создана") }
            is BackupResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
        }
    }

    fun cancelBackup() {
        _state.update { it.copy(pendingBackupUri = null) }
    }

    fun onBackupFilePicked(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, message = null, error = null) }
        when (val info = backupRepo.getBackupInfo(uri)) {
            is BackupInfoResult.Ready ->
                _state.update { it.copy(isLoading = false, pendingRestoreUri = uri, pendingRestoreCreatedAt = info.info.createdAt, pendingRestoreBackupVersion = info.info.backupVersion) }
            is BackupInfoResult.IncompatibleVersion ->
                _state.update { it.copy(isLoading = false, error = info.message) }
            is BackupInfoResult.Error ->
                _state.update { it.copy(isLoading = false, error = info.message) }
        }
    }

    fun submitRestorePassword(password: CharArray) = viewModelScope.launch {
        val uri = _state.value.pendingRestoreUri ?: return@launch
        _state.update { it.copy(isLoading = true, wrongPassword = false) }
        when (val result = backupRepo.restoreBackup(uri, password)) {
            is RestoreResult.Success ->
                _state.update { it.copy(isLoading = false, pendingRestoreUri = null, restoreCompleted = true) }
            is RestoreResult.WrongPassword ->
                _state.update { it.copy(isLoading = false, wrongPassword = true) }
            is RestoreResult.Error ->
                _state.update { it.copy(isLoading = false, error = result.message, pendingRestoreUri = null) }
        }
    }

    fun cancelRestore() {
        _state.update { it.copy(pendingRestoreUri = null, pendingRestoreCreatedAt = null, wrongPassword = false) }
    }

    fun restartApp(activity: Activity) = backupRepo.restartProcess(activity)

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
