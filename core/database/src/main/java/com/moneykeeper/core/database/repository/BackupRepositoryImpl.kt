package com.moneykeeper.core.database.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.annotation.Keep
import com.moneykeeper.core.database.AppDatabase
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.aesGcmDecrypt
import com.moneykeeper.core.database.security.aesGcmEncrypt
import com.moneykeeper.core.domain.repository.BackupInfo
import com.moneykeeper.core.domain.repository.BackupInfoResult
import com.moneykeeper.core.domain.repository.BackupRepository
import com.moneykeeper.core.domain.repository.BackupResult
import com.moneykeeper.core.domain.repository.KeyDerivation
import com.moneykeeper.core.domain.repository.MasterKeyProvider
import com.moneykeeper.core.domain.repository.RestoreResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
@Keep
private data class BackupManifest(
    val appVersionCode: Int,
    val databaseVersion: Int,
    val createdAt: String,
    val kdf: KdfSpec,
    @SerialName("dbEncIv") val dbEncIv: String,
    val backupVersion: Int = 1,
) {
    @Serializable
    @Keep
    data class KdfSpec(
        val salt: String,
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

    companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val DB_ENTRY = "database.enc"
    }
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: DatabaseProvider,
    private val masterKeyProvider: MasterKeyProvider,
    private val keyDerivation: KeyDerivation,
    private val keyStorage: DatabaseKeyStorage,
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createBackup(uri: Uri, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        val tempPlain = File(context.cacheDir, "backup_plain_${System.currentTimeMillis()}.db")
        try {
            exportPlainDump(tempPlain)

            val kdfSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val backupKey = keyDerivation.derive(
                password = password,
                salt = kdfSalt,
                iterations = 3,
                memoryKb = 32768,
                parallelism = 2,
            )
            try {
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val cipherText = aesGcmEncrypt(tempPlain.readBytes(), backupKey, iv)

                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                } catch (_: Exception) { 1 }

                val manifest = BackupManifest(
                    appVersionCode = appVersion,
                    databaseVersion = AppDatabase.VERSION,
                    createdAt = Instant.now().toString(),
                    kdf = BackupManifest.KdfSpec(
                        salt = Base64.encodeToString(kdfSalt, Base64.NO_WRAP),
                        iterations = 3,
                        memoryKb = 32768,
                        parallelism = 2,
                    ),
                    dbEncIv = Base64.encodeToString(iv, Base64.NO_WRAP),
                    backupVersion = 2,
                )

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_ENTRY))
                        zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry(BackupManifest.DB_ENTRY))
                        zip.write(cipherText)
                        zip.closeEntry()
                    }
                }
                BackupResult.Success
            } finally {
                backupKey.fill(0)
                password.fill(0.toChar())
            }
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Ошибка создания резервной копии")
        } finally {
            shredAndDelete(tempPlain)
        }
    }

    override suspend fun getBackupInfo(uri: Uri): BackupInfoResult = withContext(Dispatchers.IO) {
        try {
            val manifest = readManifest(uri)
                ?: return@withContext BackupInfoResult.Error("Файл повреждён: manifest.json не найден")
            if (manifest.databaseVersion > AppDatabase.VERSION) {
                return@withContext BackupInfoResult.IncompatibleVersion(
                    "Резервная копия создана более новой версией приложения (БД v${manifest.databaseVersion}). Обновите приложение."
                )
            }
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } catch (_: Exception) { 1 }
            BackupInfoResult.Ready(
                BackupInfo(
                    createdAt = manifest.createdAt,
                    databaseVersion = manifest.databaseVersion,
                    appVersionCode = appVersion,
                    backupVersion = manifest.backupVersion,
                )
            )
        } catch (e: Exception) {
            BackupInfoResult.Error(e.message ?: "Ошибка чтения резервной копии")
        }
    }

    override suspend fun restoreBackup(uri: Uri, password: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        val tempPlain = File(context.cacheDir, "restore_plain_${System.currentTimeMillis()}.db")
        try {
            val manifest = readManifest(uri)
                ?: return@withContext RestoreResult.Error("Файл повреждён: manifest.json не найден")

            val salt = Base64.decode(manifest.kdf.salt, Base64.NO_WRAP)
            val backupMasterKey = keyDerivation.derive(
                password = password,
                salt = salt,
                iterations = manifest.kdf.iterations,
                memoryKb = manifest.kdf.memoryKb,
                parallelism = manifest.kdf.parallelism,
            )
            try {
                val cipherText = readDbEnc(uri)
                    ?: return@withContext RestoreResult.Error("Файл повреждён: database.enc не найден")
                val iv = Base64.decode(manifest.dbEncIv, Base64.NO_WRAP)
                val plain = try {
                    aesGcmDecrypt(cipherText, backupMasterKey, iv)
                } catch (_: AEADBadTagException) {
                    return@withContext RestoreResult.WrongPassword
                }
                tempPlain.writeBytes(plain)
            } finally {
                backupMasterKey.fill(0)
                password.fill(0.toChar())
            }

            databaseProvider.close()
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            val dbParent = dbFile.parentFile ?: error("DB parent dir is null")
            val pending = File(dbParent, "${dbFile.name}.restore-pending")
            pending.delete()

            val currentDbKey = getCurrentDbKey()
            try {
                importPlainIntoEncrypted(tempPlain, pending, currentDbKey)
            } catch (e: Exception) {
                pending.delete()
                // Re-open the original DB so the app stays functional after import failure
                databaseProvider.initialize(currentDbKey)
                return@withContext RestoreResult.Error("Ошибка импорта: ${e.message.orEmpty()}")
            } finally {
                currentDbKey.fill(0)
            }

            File(dbParent, "${dbFile.name}-wal").delete()
            File(dbParent, "${dbFile.name}-shm").delete()
            Files.move(pending.toPath(), dbFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

            RestoreResult.Success
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: "Ошибка восстановления")
        } finally {
            shredAndDelete(tempPlain)
        }
    }

    private fun shredAndDelete(file: File) {
        if (!file.exists()) return
        try {
            val size = file.length()
            if (size > 0) {
                file.outputStream().use { out ->
                    val chunk = ByteArray(minOf(size, 4096L).toInt())
                    SecureRandom().nextBytes(chunk)
                    var remaining = size
                    while (remaining > 0) {
                        val write = minOf(remaining, chunk.size.toLong()).toInt()
                        out.write(chunk, 0, write)
                        remaining -= write
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
        } catch (_: Exception) {
        } finally {
            file.delete()
        }
    }

    override fun restartProcess(activity: Activity) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(launch)
        activity.finishAffinity()
        kotlin.system.exitProcess(0)
    }

    private fun exportPlainDump(target: File) {
        // Use a dedicated OPEN_READWRITE connection rather than Room's live connection.
        //
        // Using Room's write connection (db.openHelper.writableDatabase) for ATTACH +
        // sqlcipher_export disrupts TriggerBasedInvalidationTracker in two ways:
        //   1. If the connection has a concurrent BEGIN TRANSACTION (from syncTriggers),
        //      ATTACH triggers "cannot change into wal mode from within a transaction".
        //   2. The SQLite backup API (used by sqlcipher_export) acquires/releases read locks
        //      that perturb Room's InvalidationTracker, causing it to drop/recreate
        //      room_table_modification_log. The resulting gap leaves syncTriggers unable
        //      to INSERT into the table → "no such table" crash after backup.
        //
        // The dedicated connection's attempt to switch journal_mode to DELETE (Android default)
        // fails with a logged warning but proceeds in WAL mode — this is harmless.
        // The ATTACH on this connection creates a fresh file with no competing transactions,
        // so WAL initialisation of the new file succeeds without interference.

        target.delete()
        // Pre-create the target as an empty unencrypted SQLite file so ATTACH opens an
        // existing file rather than creating one. SQLCipher with ByteArray(0) writes a
        // standard (no-encryption) SQLite file with no Android-specific tables —
        // unlike android.database.sqlite.SQLiteDatabase which adds android_metadata,
        // causing sqlcipher_export to fail with "table android_metadata already exists".
        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            target.absolutePath, ByteArray(0), null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
            net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
            null, null,
        ).close()

        val dbKey = getCurrentDbKey()
        try {
            val dbPath = context.getDatabasePath(AppDatabase.DB_NAME).absolutePath
            val roConn = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbPath, dbKey, null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null, null,
            )
            try {
                val safePath = target.absolutePath.replace("'", "''")
                roConn.execSQL("ATTACH DATABASE '$safePath' AS plaintext KEY ''")
                // sqlcipher_export returns a value — use rawQuery, not execSQL
                roConn.rawQuery("SELECT sqlcipher_export('plaintext')", null).use { it.moveToFirst() }
                roConn.execSQL("DETACH DATABASE plaintext")
            } finally {
                roConn.close()
            }
        } finally {
            dbKey.fill(0)
        }
    }

    private fun importPlainIntoEncrypted(plainDb: File, target: File, dbKey: ByteArray) {
        // Open the encrypted target as MAIN using the SAME key path as Room:
        //   openDatabase(path, ByteArray) → nativeKey(rawBytes) → sqlite3_key_v2 → PBKDF2
        // ATTACH … KEY "x'hex'" bypasses PBKDF2 (raw key) — that is a DIFFERENT derived key
        // than what sqlite3_key_v2 with raw bytes produces, causing HMAC mismatch on open.
        // Keeping the encrypted side as MAIN avoids ATTACH KEY entirely.
        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            target.absolutePath, dbKey, null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
            net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
            null, null,
        )
        try {
            val safePlainPath = plainDb.absolutePath.replace("'", "''")
            db.execSQL("ATTACH DATABASE '$safePlainPath' AS src KEY ''")
            db.execSQL("PRAGMA foreign_keys = OFF")

            // Replay DDL from src (rowid order ensures deps are created before dependents).
            // Exclude sqlite_* objects — SQLite owns those internally and rejects explicit CREATE.
            db.rawQuery(
                "SELECT sql FROM src.sqlite_schema WHERE sql NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid",
                null,
            ).use { c ->
                while (c.moveToNext()) db.execSQL(c.getString(0))
            }

            // Copy table data — validate names to guard against future schema regressions
            val tableNameRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
            db.rawQuery(
                "SELECT name FROM src.sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0)
                    require(t.matches(tableNameRegex)) { "Unexpected table name in backup: $t" }
                    db.execSQL("INSERT INTO \"$t\" SELECT * FROM src.\"$t\"")
                }
            }

            // sqlite_sequence tracks AUTOINCREMENT counters — copy if present
            runCatching {
                db.execSQL("INSERT INTO sqlite_sequence SELECT * FROM src.sqlite_sequence")
            }

            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DETACH DATABASE src")
        } finally {
            db.close()
        }
    }

    private fun getCurrentDbKey(): ByteArray {
        val masterKey = masterKeyProvider.requireKey()
        try {
            val enc = keyStorage.readEncryptedDbKey()
                ?: error("Encrypted db_key отсутствует")
            return aesGcmDecrypt(enc.ciphertext, masterKey, enc.iv)
        } finally {
            masterKey.fill(0)
        }
    }

    private fun readManifest(uri: Uri): BackupManifest? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == BackupManifest.MANIFEST_ENTRY) {
                        return json.decodeFromString(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun readDbEnc(uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == BackupManifest.DB_ENTRY) return zip.readBytes()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

}
