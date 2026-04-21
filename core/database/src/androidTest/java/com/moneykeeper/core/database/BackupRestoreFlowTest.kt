package com.moneykeeper.core.database

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.repository.BackupRepositoryImpl
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.repository.BackupResult
import com.moneykeeper.core.domain.repository.KeyDerivation
import com.moneykeeper.core.domain.repository.MasterKeyProvider
import com.moneykeeper.core.domain.repository.RestoreResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Integration test for the backup → restore flow. Requires a running emulator (SQLCipher
 * native library + Android Keystore). Verifies:
 *   1. createBackup() completes without crashing.
 *   2. Reading a Flow immediately after backup does not crash
 *      (regression for the WAL-checkpoint / room_table_modification_log bug).
 *   3. restoreBackup() rolls the database back to the backup snapshot: an account created
 *      after the backup is absent after restore.
 *
 * All crypto is real (AES-GCM, SQLCipher). Argon2 KDF is stubbed out — the fake
 * KeyDerivation returns the same fixed key regardless of parameters, which makes the
 * backup/restore key round-trip deterministic without the ~300 ms Argon2 cost.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreFlowTest {

    private lateinit var context: Context
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var keyStorage: DatabaseKeyStorage
    private lateinit var backupRepo: BackupRepositoryImpl
    private lateinit var backupFile: File

    // Fixed test key material — deterministic values, only used inside this test.
    // masterKey wraps dbKey (AES-GCM); dbKey is the SQLCipher passphrase.
    private val masterKey = ByteArray(32) { (it + 1).toByte() }
    private val dbKey     = ByteArray(32) { (it + 50).toByte() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        keyStorage = DatabaseKeyStorage(context)
        // Clear any state left by previous runs before writing our test credentials.
        keyStorage.wipe()

        // Pre-store AES-GCM-wrapped dbKey so BackupRepositoryImpl.getCurrentDbKey()
        // can decrypt it during restoreBackup().
        val iv = ByteArray(12) { 77 }
        val encDbKey = aesGcmEncrypt(dbKey, masterKey, iv)
        keyStorage.writeEncryptedDbKey(encDbKey, iv)
        keyStorage.writeKdfParams(
            DatabaseKeyStorage.KdfParams(iterations = 1, memoryKb = 64, parallelism = 1)
        )
        // createBackup() now generates a fresh random salt per backup (no longer reuses the
        // app-unlock salt). The KdfParams above are only used by restoreBackup to fetch db_key.

        // Remove any leftover DB files so the first initialize() creates a fresh database.
        deleteDbFiles()

        databaseProvider = DatabaseProvider(context, keyStorage)
        // DatabaseProvider.initialize() loads the sqlcipher native lib internally.
        databaseProvider.initialize(dbKey.copyOf())

        backupRepo = BackupRepositoryImpl(
            context = context,
            databaseProvider = databaseProvider,
            masterKeyProvider = object : MasterKeyProvider {
                // Returns the same fixed masterKey that was used to wrap dbKey above.
                override fun requireKey(): ByteArray = masterKey.copyOf()
            },
            keyDerivation = object : KeyDerivation {
                // Stub: returns masterKey unconditionally so that the backup password
                // round-trip works without running the real Argon2 KDF.
                override fun derive(
                    password: CharArray, salt: ByteArray,
                    iterations: Int, memoryKb: Int, parallelism: Int,
                ): ByteArray = masterKey.copyOf()
            },
            keyStorage = keyStorage,
        )

        backupFile = File(context.cacheDir, "backup_restore_flow_test.mkbak")
        backupFile.delete()
    }

    @After
    fun tearDown() {
        runCatching { databaseProvider.close() }
        backupFile.delete()
        deleteDbFiles()
        keyStorage.wipe()
    }

    @Test
    fun createBackup_thenInsertAccount_thenRestore_accountIsAbsent() = runTest {
        val accountDao = databaseProvider.require().accountDao()

        // ── Step 1: baseline account present before backup ────────────────────
        accountDao.upsert(testAccount("Alpha", "#FF0000"))

        // ── Step 2: create backup ─────────────────────────────────────────────
        val backupUri = Uri.fromFile(backupFile)
        val backupResult = backupRepo.createBackup(backupUri, "test-backup-password".toCharArray())
        assertTrue("createBackup must succeed, got: $backupResult",
            backupResult is BackupResult.Success)
        assertTrue("Backup file must be written to disk", backupFile.exists())

        // ── Step 3: collect Flow immediately after backup ─────────────────────
        // Regression guard: this triggered "no such table: room_table_modification_log"
        // before the PRAGMA wal_checkpoint(FULL) call was removed from exportPlainDump.
        val afterBackup = accountDao.observeActive().first()
        assertTrue("Alpha must be present after backup",
            afterBackup.any { it.name == "Alpha" })

        // ── Step 4: insert post-backup account ────────────────────────────────
        accountDao.upsert(testAccount("Beta", "#0000FF"))
        assertTrue("Beta must exist before restore",
            accountDao.observeActive().first().any { it.name == "Beta" })

        // ── Step 5: restore from backup ───────────────────────────────────────
        // Password value is irrelevant — the fake KeyDerivation always returns masterKey.
        val restoreResult = backupRepo.restoreBackup(backupUri, "ignored".toCharArray())
        assertTrue("restoreBackup must succeed, got: $restoreResult",
            restoreResult is RestoreResult.Success)

        // ── Step 6: re-open DB (mirrors the process restart the app normally does) ──
        databaseProvider.initialize(dbKey.copyOf())

        // ── Step 7: assertions ────────────────────────────────────────────────
        val afterRestore = databaseProvider.require().accountDao().observeActive().first()
        assertTrue("Alpha must survive the restore",
            afterRestore.any { it.name == "Alpha" })
        assertFalse("Beta must be absent — it was created after the backup",
            afterRestore.any { it.name == "Beta" })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun testAccount(name: String, colorHex: String) = AccountEntity(
        name = name,
        type = AccountType.CARD,
        currency = "RUB",
        colorHex = colorHex,
        iconName = "CreditCard",
        balance = BigDecimal("100.00"),
        createdAt = LocalDate.now(),
    )

    private fun deleteDbFiles() {
        context.getDatabasePath(AppDatabase.DB_NAME).let { f ->
            f.delete()
            File(f.parent!!, "${f.name}-wal").delete()
            File(f.parent!!, "${f.name}-shm").delete()
            File(f.parent!!, "${f.name}.restore-pending").delete()
        }
    }

    private fun aesGcmEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plain)
    }
}
