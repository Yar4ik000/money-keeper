package com.moneykeeper.core.database.backup

import kotlinx.serialization.Serializable

/**
 * Манифест ZIP-бэкапа (money_keeper_backup_YYYY-MM-DD.mkbak).
 * Лежит в архиве открытым — чтобы RestoreUseCase мог проверить версии и KDF-параметры
 * до запроса пароля у пользователя (детали — §2.12 и §10.5).
 *
 * Структура архива:
 *   manifest.json  — этот класс, JSON
 *   database.enc   — AES-GCM(master_key, sqlite_dump)
 */
@Serializable
data class BackupManifest(
    val appVersionCode: Int,
    val databaseVersion: Int,
    val createdAt: String,      // ISO-8601, например "2026-04-19T10:30:00Z"
    val kdf: KdfSpec,
    val dbEncIv: String,        // base64 IV для расшифровки database.enc
) {
    @Serializable
    data class KdfSpec(
        val salt: String,       // base64, 16 байт
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

    companion object {
        const val FILE_NAME    = "manifest.json"
        const val DB_FILE_NAME = "database.enc"
    }
}
