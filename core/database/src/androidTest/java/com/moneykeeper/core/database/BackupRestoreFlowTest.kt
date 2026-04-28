package com.moneykeeper.core.database

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.repository.BackupRepositoryImpl
import com.moneykeeper.core.database.repository.importPlainIntoEncrypted
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        // After restore, the DB stays OPEN — only the .restore-pending file is written.
        // The live DB is untouched until restartProcess() does the atomic swap.
        val restoreResult = backupRepo.restoreBackup(backupUri, "ignored".toCharArray())
        assertTrue("restoreBackup must succeed, got: $restoreResult",
            restoreResult is RestoreResult.Success)

        // ── Step 6: simulate restartProcess() — close + atomic file swap + re-open ──
        // In production this is done on the Main thread just before exitProcess(0).
        databaseProvider.close()
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val pending = java.io.File(dbFile.parentFile!!, "${dbFile.name}.restore-pending")
        assertTrue("restore-pending file must exist", pending.exists())
        java.io.File(dbFile.parentFile!!, "${dbFile.name}-wal").delete()
        java.io.File(dbFile.parentFile!!, "${dbFile.name}-shm").delete()
        java.nio.file.Files.move(
            pending.toPath(), dbFile.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        databaseProvider.initialize(dbKey.copyOf())

        // ── Step 7: assertions ────────────────────────────────────────────────
        val afterRestore = databaseProvider.require().accountDao().observeActive().first()
        assertTrue("Alpha must survive the restore",
            afterRestore.any { it.name == "Alpha" })
        assertFalse("Beta must be absent — it was created after the backup",
            afterRestore.any { it.name == "Beta" })
    }

    @Test
    fun crashAfterRestoreWrite_coldStart_appliesPendingRestore() = runTest {
        val accountDao = databaseProvider.require().accountDao()

        // ── Step 1: baseline account present before backup ────────────────────
        accountDao.upsert(testAccount("Alpha", "#FF0000"))
        val backupResult = backupRepo.createBackup(Uri.fromFile(backupFile), "test-backup-password".toCharArray())
        assertTrue("createBackup must succeed", backupResult is BackupResult.Success)

        // ── Step 2: insert post-backup account ────────────────────────────────
        accountDao.upsert(testAccount("Beta", "#0000FF"))

        // ── Step 3: restore backup — writes .restore-pending, does NOT restart ─
        val restoreResult = backupRepo.restoreBackup(Uri.fromFile(backupFile), "ignored".toCharArray())
        assertTrue("restoreBackup must succeed", restoreResult is RestoreResult.Success)

        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val pending = java.io.File(dbFile.parentFile!!, "${dbFile.name}.restore-pending")
        assertTrue(".restore-pending must exist", pending.exists())

        // ── Step 4: simulate crash — close DB without calling restartProcess() ─
        databaseProvider.close()

        // ── Step 5: cold start — initialize() must detect and apply the pending ─
        databaseProvider.initialize(dbKey.copyOf())

        // ── Step 6: verify restore was applied ────────────────────────────────
        val accounts = databaseProvider.require().accountDao().observeActive().first()
        assertTrue("Alpha must survive cold-start restore", accounts.any { it.name == "Alpha" })
        assertFalse("Beta must be absent — created after backup", accounts.any { it.name == "Beta" })
        assertFalse(".restore-pending must be cleaned up", pending.exists())
    }

    /**
     * Regression test: restoring a backup created with an older DB version must trigger
     * Room migrations (onUpgrade), not onCreate().
     *
     * Root cause of the original bug: importPlainIntoEncrypted replayed DDL but left
     * user_version = 0. Room saw 0, assumed a brand-new database, called onCreate() instead
     * of onUpgrade(), and crashed with "table already exists" (or schema mismatch).
     *
     * This test builds a fake v6 backup (exact schema from the exported schema JSON),
     * restores it via the full BackupRepository flow, simulates restartProcess(), and
     * re-opens with DatabaseProvider. A crash inside initialize() is the failure signal;
     * successful migration is verified by checking columns/tables added in migrations 6→9.
     */
    @Test
    fun restoreFromLegacyBackup_v6_roomRunsMigrationsNotOnCreate() = runTest {
        val v6Plain = File(context.cacheDir, "rrt_v6_src.db")
        val fakeZip = File(context.cacheDir, "rrt_v6_backup.mkbak")
        try {
            buildV6PlainDb(v6Plain, accountName = "Gamma")

            buildFakeBackupZip(plainDb = v6Plain, output = fakeZip, dbVersion = 6)
            v6Plain.delete()

            // ── Restore ───────────────────────────────────────────────────────
            val result = backupRepo.restoreBackup(Uri.fromFile(fakeZip), "ignored".toCharArray())
            assertTrue("restoreBackup must succeed, got: $result", result is RestoreResult.Success)

            // ── Simulate restartProcess() ─────────────────────────────────────
            databaseProvider.close()
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            val pending = File(dbFile.parentFile!!, "${dbFile.name}.restore-pending")
            assertTrue(".restore-pending must exist after restore", pending.exists())
            File(dbFile.parentFile!!, "${dbFile.name}-wal").delete()
            File(dbFile.parentFile!!, "${dbFile.name}-shm").delete()
            java.nio.file.Files.move(
                pending.toPath(), dbFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )

            // ── Re-open: Room must call onUpgrade(6→9), not onCreate() ────────
            // If user_version was left at 0, Room calls onCreate() and crashes with
            // "table already exists". Success here proves the fix is working.
            databaseProvider.initialize(dbKey.copyOf())

            // ── Assert migrations ran and data survived ────────────────────────
            val db = databaseProvider.require()
            val accounts = db.accountDao().observeActive().first()
            assertTrue("Gamma account must survive v6→v9 migration",
                accounts.any { it.name == "Gamma" })

            val sdb = db.openHelper.writableDatabase
            // deposit_events table — created by migration 6→7
            sdb.query("SELECT id FROM deposit_events LIMIT 0").close()
            // accrualBasis column — added by migration 7→8
            sdb.query("SELECT accrualBasis FROM deposits LIMIT 0").close()
            // time column — added by migration 8→9
            sdb.query("SELECT time FROM transactions LIMIT 0").close()
        } finally {
            v6Plain.delete()
            fakeZip.delete()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates an unencrypted SQLite file with the exact v6 schema (from exported schema JSON).
     * Uses SQLCipher with ByteArray(0) key so the output is a standard SQLite file that
     * importPlainIntoEncrypted can ATTACH with KEY ''.
     * journal_mode = DELETE ensures no WAL artefacts — all writes are in the main file.
     */
    private fun buildV6PlainDb(target: File, accountName: String) {
        target.delete()
        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            target.absolutePath, ByteArray(0), null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
            net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
            null, null,
        ).use { db ->
            db.rawQuery("PRAGMA journal_mode = DELETE", null).close()
            db.execSQL("PRAGMA foreign_keys = OFF")
            // Tables ordered so that FK references are satisfied (recurring_rules before transactions).
            db.execSQL("""CREATE TABLE IF NOT EXISTS recurring_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                frequency TEXT NOT NULL, `interval` INTEGER NOT NULL,
                startDate TEXT NOT NULL, endDate TEXT, lastGeneratedDate TEXT)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, type TEXT NOT NULL, currency TEXT NOT NULL,
                colorHex TEXT NOT NULL, iconName TEXT NOT NULL, balance TEXT NOT NULL,
                isArchived INTEGER NOT NULL, createdAt TEXT NOT NULL, sortOrder INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS deposits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                accountId INTEGER NOT NULL, initialAmount TEXT NOT NULL,
                interestRate TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT,
                isCapitalized INTEGER NOT NULL, capitalizationPeriod TEXT,
                notifyDaysBefore TEXT NOT NULL, autoRenew INTEGER NOT NULL,
                payoutAccountId INTEGER, isActive INTEGER NOT NULL, rateTiersJson TEXT,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(payoutAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_deposits_accountId ON deposits (accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_deposits_payoutAccountId ON deposits (payoutAccountId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, type TEXT NOT NULL, colorHex TEXT NOT NULL,
                iconName TEXT NOT NULL, parentCategoryId INTEGER,
                isDefault INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                FOREIGN KEY(parentCategoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentCategoryId ON categories (parentCategoryId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                accountId INTEGER NOT NULL, toAccountId INTEGER,
                amount TEXT NOT NULL, type TEXT NOT NULL, categoryId INTEGER,
                date TEXT NOT NULL, note TEXT NOT NULL, recurringRuleId INTEGER,
                createdAt TEXT NOT NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(recurringRuleId) REFERENCES recurring_rules(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions (accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_toAccountId ON transactions (toAccountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions (categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions (date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_recurringRuleId ON transactions (recurringRuleId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryIds TEXT,
                amount TEXT NOT NULL, period TEXT NOT NULL, currency TEXT NOT NULL,
                accountIds TEXT, warningThreshold INTEGER, criticalThreshold INTEGER)""")

            db.execSQL("""INSERT INTO accounts
                (name, type, currency, colorHex, iconName, balance, isArchived, createdAt, sortOrder)
                VALUES ('$accountName', 'CARD', 'RUB', '#00FF00', 'CreditCard', '5000.00', 0, '2024-01-01', 0)""")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * Wraps [plainDb] bytes into a .mkbak ZIP identical in format to BackupRepositoryImpl.createBackup().
     * Uses [masterKey] as the backup encryption key — matches the stub KeyDerivation in setUp().
     */
    private fun buildFakeBackupZip(plainDb: File, output: File, dbVersion: Int) {
        val iv = ByteArray(12) { 99.toByte() }
        val encDb = aesGcmEncrypt(plainDb.readBytes(), masterKey, iv)
        val kdfSalt = Base64.encodeToString(ByteArray(16) { 1 }, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val manifest = """{"appVersionCode":106,"databaseVersion":$dbVersion,"createdAt":"2024-01-01T00:00:00Z","kdf":{"salt":"$kdfSalt","iterations":1,"memoryKb":64,"parallelism":1},"dbEncIv":"$ivB64","backupVersion":2}"""

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
