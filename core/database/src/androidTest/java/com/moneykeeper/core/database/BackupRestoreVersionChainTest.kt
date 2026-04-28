package com.moneykeeper.core.database

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneykeeper.core.database.repository.BackupRepositoryImpl
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.domain.repository.BackupResult
import com.moneykeeper.core.domain.repository.KeyDerivation
import com.moneykeeper.core.domain.repository.MasterKeyProvider
import com.moneykeeper.core.domain.repository.RestoreResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end backup/restore regression suite spanning every historical DB version.
 *
 * For each version in [FIRST_MIGRATABLE_VERSION..AppDatabase.VERSION]:
 *   1. Build a plain SQLite DB at that version (MigrationTestHelper reads the exported schema JSON).
 *   2. Wrap in a .mkbak backup zip — same format as BackupRepositoryImpl.createBackup().
 *   3. Restore via BackupRepository: Room must call onUpgrade(N→current), not onCreate().
 *   4. Verify the account row survived and the migrated schema is valid.
 *   5. Create a new backup from the now-upgraded DB (version = AppDatabase.VERSION).
 *   6. Restore that second backup — no further migrations needed.
 *   7. Verify data survived the full round-trip.
 *
 * ── How to extend ─────────────────────────────────────────────────────────────────────
 * When bumping AppDatabase.VERSION from N to N+1 and adding MIGRATION_N_N+1:
 *   • The loop ends at AppDatabase.VERSION automatically — no code change is needed here
 *     as long as the new migration does not alter the structure of the accounts table.
 *   • If it does, update [INSERT_ACCOUNT_FOR_ALL_VERSIONS] or add a version-specific branch.
 *   • See RELEASE_CHECKLIST.md → section "Database migration".
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreVersionChainTest {

    private lateinit var context: Context
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var keyStorage: DatabaseKeyStorage
    private lateinit var backupRepo: BackupRepositoryImpl

    // Fixed deterministic key material — only used inside this test.
    private val masterKey = ByteArray(32) { (it + 1).toByte() }
    private val dbKey     = ByteArray(32) { (it + 50).toByte() }

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyStorage = DatabaseKeyStorage(context)
        keyStorage.wipe()

        val iv = ByteArray(12) { 77 }
        keyStorage.writeEncryptedDbKey(aesGcmEncrypt(dbKey, masterKey, iv), iv)
        keyStorage.writeKdfParams(
            DatabaseKeyStorage.KdfParams(iterations = 1, memoryKb = 64, parallelism = 1)
        )

        deleteDbFiles()
        databaseProvider = DatabaseProvider(context, keyStorage)
        databaseProvider.initialize(dbKey.copyOf())

        backupRepo = BackupRepositoryImpl(
            context = context,
            databaseProvider = databaseProvider,
            masterKeyProvider = object : MasterKeyProvider {
                override fun requireKey(): ByteArray = masterKey.copyOf()
            },
            keyDerivation = object : KeyDerivation {
                override fun derive(
                    password: CharArray, salt: ByteArray,
                    iterations: Int, memoryKb: Int, parallelism: Int,
                ): ByteArray = masterKey.copyOf()
            },
            keyStorage = keyStorage,
        )
    }

    @After
    fun tearDown() {
        runCatching { databaseProvider.close() }
        deleteDbFiles()
        keyStorage.wipe()
        for (v in FIRST_MIGRATABLE_VERSION..AppDatabase.VERSION) {
            context.getDatabasePath("chain_src_v$v").let { f ->
                f.delete()
                File(f.parent!!, "${f.name}-wal").delete()
                File(f.parent!!, "${f.name}-shm").delete()
            }
            File(context.cacheDir, "chain_zip_v${v}_1.mkbak").delete()
            File(context.cacheDir, "chain_zip_v${v}_2.mkbak").delete()
        }
    }

    // ── Main test ──────────────────────────────────────────────────────────────────────

    @Test
    fun backupRestoreChain_allHistoricalVersions() = runTest {
        for (fromVersion in FIRST_MIGRATABLE_VERSION..AppDatabase.VERSION) {
            runCatching { databaseProvider.close() }
            deleteDbFiles()
            databaseProvider.initialize(dbKey.copyOf())

            runChainForVersion(fromVersion)
        }
    }

    // ── Per-version chain ──────────────────────────────────────────────────────────────

    private suspend fun runChainForVersion(fromVersion: Int) {
        val srcDbName = "chain_src_v$fromVersion"
        val zip1 = File(context.cacheDir, "chain_zip_v${fromVersion}_1.mkbak")
        val zip2 = File(context.cacheDir, "chain_zip_v${fromVersion}_2.mkbak")

        // ── Step 1: plain SQLite DB at fromVersion ────────────────────────────────────
        // MigrationTestHelper reads core/database/schemas/.../N.json to create the exact schema.
        migrationHelper.createDatabase(srcDbName, fromVersion).apply {
            execSQL(INSERT_ACCOUNT_FOR_ALL_VERSIONS)
            close()
        }
        val srcFile = context.getDatabasePath(srcDbName)

        // Checkpoint WAL into the main file before reading raw bytes.
        // We use Android's standard SQLiteDatabase (the same engine MigrationTestHelper uses)
        // to guarantee WAL format compatibility.
        android.database.sqlite.SQLiteDatabase.openDatabase(
            srcFile.absolutePath, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }

        // ── Step 2: wrap in a .mkbak backup zip ──────────────────────────────────────
        buildFakeBackupZip(srcFile, zip1, dbVersion = fromVersion)
        srcFile.delete()

        // ── Step 3: restore from the legacy backup ────────────────────────────────────
        // importPlainIntoEncrypted must stamp user_version = fromVersion so Room calls
        // onUpgrade(fromVersion → AppDatabase.VERSION) instead of onCreate().
        val r1 = backupRepo.restoreBackup(Uri.fromFile(zip1), "ignored".toCharArray())
        assertTrue("v$fromVersion restore-1 failed: $r1", r1 is RestoreResult.Success)

        // ── Step 4: simulate restartProcess() ────────────────────────────────────────
        atomicSwapAndReopen()

        val accounts1 = databaseProvider.require().accountDao().observeActive().first()
        assertTrue("Account must survive restore from v$fromVersion",
            accounts1.any { it.name == ACCOUNT_NAME })

        // ── Step 5: backup from the now-migrated (current-version) DB ────────────────
        val bkpResult = backupRepo.createBackup(Uri.fromFile(zip2), "pw".toCharArray())
        assertTrue(
            "createBackup after v$fromVersion→${AppDatabase.VERSION} migration failed: $bkpResult",
            bkpResult is BackupResult.Success,
        )

        // ── Step 6: restore the second backup (no new migrations needed) ─────────────
        runCatching { databaseProvider.close() }
        deleteDbFiles()
        databaseProvider.initialize(dbKey.copyOf())

        val r2 = backupRepo.restoreBackup(Uri.fromFile(zip2), "pw".toCharArray())
        assertTrue("v$fromVersion restore-2 failed: $r2", r2 is RestoreResult.Success)

        // ── Step 7: verify data survived the complete round-trip ─────────────────────
        atomicSwapAndReopen()

        val accounts2 = databaseProvider.require().accountDao().observeActive().first()
        assertTrue(
            "Account must survive round-trip v$fromVersion → v${AppDatabase.VERSION} → v${AppDatabase.VERSION}",
            accounts2.any { it.name == ACCOUNT_NAME },
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────

    private fun atomicSwapAndReopen() {
        databaseProvider.close()
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val pending = File(dbFile.parentFile!!, "${dbFile.name}.restore-pending")
        File(dbFile.parentFile!!, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile!!, "${dbFile.name}-shm").delete()
        java.nio.file.Files.move(
            pending.toPath(), dbFile.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        databaseProvider.initialize(dbKey.copyOf())
    }

    private fun deleteDbFiles() {
        context.getDatabasePath(AppDatabase.DB_NAME).let { f ->
            f.delete()
            File(f.parent!!, "${f.name}-wal").delete()
            File(f.parent!!, "${f.name}-shm").delete()
            File(f.parent!!, "${f.name}.restore-pending").delete()
        }
    }

    private fun buildFakeBackupZip(plainDb: File, output: File, dbVersion: Int) {
        val iv = ByteArray(12) { 99.toByte() }
        val encDb = aesGcmEncrypt(plainDb.readBytes(), masterKey, iv)
        val kdfSalt = Base64.encodeToString(ByteArray(16) { 1 }, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val manifest = """{"appVersionCode":100,"databaseVersion":$dbVersion,"createdAt":"2024-01-01T00:00:00Z","kdf":{"salt":"$kdfSalt","iterations":1,"memoryKb":64,"parallelism":1},"dbEncIv":"$ivB64","backupVersion":2}"""
        output.delete()
        ZipOutputStream(output.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database.enc"))
            zip.write(encDb)
            zip.closeEntry()
        }
    }

    private fun aesGcmEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plain)
    }

    companion object {
        // v1 is excluded: MIGRATION_1_2 is not defined, so Room would throw
        // "A migration from 1 to 2 was required but not found."
        // v2 is the earliest version with a complete migration path to AppDatabase.VERSION.
        const val FIRST_MIGRATABLE_VERSION = 2

        private const val ACCOUNT_NAME = "ChainTest"

        // accounts schema is unchanged since v1 (name/type/currency/colorHex/iconName/
        // balance/isArchived/createdAt/sortOrder all exist from the beginning).
        private const val INSERT_ACCOUNT_FOR_ALL_VERSIONS = """
            INSERT INTO accounts
                (name, type, currency, colorHex, iconName, balance, isArchived, createdAt, sortOrder)
            VALUES ('ChainTest', 'CARD', 'RUB', '#00FF00', 'CreditCard', '5000.00', 0, '2024-01-01', 0)
        """
    }
}
