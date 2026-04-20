package com.moneykeeper.core.domain.repository

import android.app.Activity
import android.net.Uri

interface BackupRepository {
    suspend fun createBackup(uri: Uri): BackupResult
    suspend fun getBackupInfo(uri: Uri): BackupInfoResult
    suspend fun restoreBackup(uri: Uri, password: CharArray): RestoreResult
    fun restartProcess(activity: Activity)
}

data class BackupInfo(
    val createdAt: String,
    val databaseVersion: Int,
    val appVersionCode: Int,
)

sealed interface BackupResult {
    data object Success : BackupResult
    data class Error(val message: String) : BackupResult
}

sealed interface BackupInfoResult {
    data class Ready(val info: BackupInfo) : BackupInfoResult
    data class IncompatibleVersion(val message: String) : BackupInfoResult
    data class Error(val message: String) : BackupInfoResult
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object WrongPassword : RestoreResult
    data class Error(val message: String) : RestoreResult
}
