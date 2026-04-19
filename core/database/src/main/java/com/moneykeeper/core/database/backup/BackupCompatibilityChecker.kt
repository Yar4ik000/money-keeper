package com.moneykeeper.core.database.backup

import javax.inject.Inject

/**
 * Проверяет совместимость бэкапа с текущей версией схемы.
 * Вызывается из RestoreUseCase (§10.5) до подмены файла БД.
 */
class BackupCompatibilityChecker @Inject constructor() {

    fun check(manifest: BackupManifest, currentDbVersion: Int): Compatibility = when {
        manifest.databaseVersion > currentDbVersion -> Compatibility.NewerBackup(
            "Резервная копия создана более новой версией приложения " +
            "(схема v${manifest.databaseVersion}, текущая v$currentDbVersion). " +
            "Обновите приложение перед восстановлением."
        )
        manifest.databaseVersion < currentDbVersion -> Compatibility.OlderBackup
        else -> Compatibility.Exact
    }

    sealed interface Compatibility {
        data object Exact : Compatibility
        data object OlderBackup : Compatibility              // Room применит миграции сам
        data class NewerBackup(val userMessage: String) : Compatibility
    }
}
