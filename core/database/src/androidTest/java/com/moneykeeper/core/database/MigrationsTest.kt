package com.moneykeeper.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneykeeper.core.database.migration.MIGRATION_2_3
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests using MigrationTestHelper (standard SQLite, not SQLCipher —
 * encryption is orthogonal to migration SQL logic).
 *
 * Covered migrations:
 *   2 → 3 (manual): deposits.endDate nullable + rateTiersJson column added
 *   3 → 4 (auto):   budgets.accountIds column added
 *   2 → 4 (chain):  full migration path in one pass
 */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // ── 2 → 3 ─────────────────────────────────────────────────────────────────

    @Test
    fun migrate_2_to_3_preservesDepositDataAndAddsColumns() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(INSERT_ACCOUNT)
            execSQL("""
                INSERT INTO deposits
                    (accountId, initialAmount, interestRate, startDate, endDate,
                     isCapitalized, capitalizationPeriod, notifyDaysBefore, autoRenew, payoutAccountId, isActive)
                VALUES (1, '100000.00', '12.00', '2026-01-01', '2026-12-31',
                        0, NULL, '7', 0, NULL, 1)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // ── Original row preserved ────────────────────────────────────────────
        db.query("SELECT initialAmount, interestRate, endDate, rateTiersJson FROM deposits").use { c ->
            assertTrue("Deposit row must survive migration 2→3", c.moveToFirst())
            assertEquals("100000.00", c.getString(0))
            assertEquals("12.00", c.getString(1))
            assertEquals("2026-12-31", c.getString(2))
            assertNull("rateTiersJson must be NULL for migrated rows", c.getString(3))
            assertFalse("Only one row expected", c.moveToNext())
        }

        // ── NULL endDate now allowed (was NOT NULL in v2) ─────────────────────
        // Need a second account since deposits.accountId has a UNIQUE index
        db.execSQL(INSERT_ACCOUNT2)
        db.execSQL("""
            INSERT INTO deposits
                (accountId, initialAmount, interestRate, startDate, endDate,
                 isCapitalized, capitalizationPeriod, notifyDaysBefore, autoRenew, payoutAccountId, isActive, rateTiersJson)
            VALUES (2, '50000.00', '10.00', '2026-06-01', NULL,
                    0, NULL, '7', 0, NULL, 1, NULL)
        """.trimIndent())
        db.query("SELECT COUNT(*) FROM deposits WHERE endDate IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Null-endDate insert must succeed", 1, c.getInt(0))
        }

        // ── rateTiersJson can hold a JSON value ───────────────────────────────
        db.execSQL(INSERT_ACCOUNT3)
        db.execSQL("""
            INSERT INTO deposits
                (accountId, initialAmount, interestRate, startDate, endDate,
                 isCapitalized, capitalizationPeriod, notifyDaysBefore, autoRenew, payoutAccountId, isActive, rateTiersJson)
            VALUES (3, '30000.00', '8.00', '2026-09-01', '2027-09-01',
                    0, NULL, '7', 0, NULL, 1, '[{"fromDate":"2026-09-01","ratePercent":"8.00"}]')
        """.trimIndent())
        db.query("SELECT rateTiersJson FROM deposits WHERE rateTiersJson IS NOT NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertNotNull(c.getString(0))
        }
    }

    @Test
    fun migrate_2_to_3_doesNotDropOtherTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(INSERT_ACCOUNT)
            execSQL(INSERT_CATEGORY)
            execSQL("""
                INSERT INTO budgets (categoryId, amount, period, currency)
                VALUES (1, '3000.00', 'MONTHLY', 'RUB')
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM accounts").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM budgets").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }

    // ── 3 → 4 (auto-migration) ────────────────────────────────────────────────

    @Test
    fun migrate_3_to_4_addsAccountIdsColumnToBudgets() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(INSERT_CATEGORY)
            execSQL("""
                INSERT INTO budgets (categoryId, amount, period, currency)
                VALUES (1, '5000.00', 'MONTHLY', 'RUB')
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.query("SELECT id, amount, currency, accountIds FROM budgets").use { c ->
            assertTrue("Budget row must survive migration 3→4", c.moveToFirst())
            assertEquals("5000.00", c.getString(1))
            assertEquals("RUB", c.getString(2))
            assertNull("accountIds must default to NULL after auto-migration", c.getString(3))
        }
    }

    @Test
    fun migrate_3_to_4_accountIds_acceptsValueAfterMigration() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(INSERT_CATEGORY)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        // accountIds column added — can now store comma-separated account IDs
        db.execSQL("""
            INSERT INTO budgets (categoryId, amount, period, currency, accountIds)
            VALUES (1, '2000.00', 'WEEKLY', 'USD', '1,2,3')
        """.trimIndent())
        db.query("SELECT accountIds FROM budgets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("1,2,3", c.getString(0))
        }
    }

    // ── Full 2 → 4 chain ──────────────────────────────────────────────────────

    @Test
    fun migrate_2_to_4_fullChain_preservesAllData() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(INSERT_ACCOUNT)
            execSQL("""
                INSERT INTO deposits
                    (accountId, initialAmount, interestRate, startDate, endDate,
                     isCapitalized, capitalizationPeriod, notifyDaysBefore, autoRenew, payoutAccountId, isActive)
                VALUES (1, '200000.00', '15.00', '2026-01-01', '2027-01-01',
                        0, NULL, '7', 0, NULL, 1)
            """.trimIndent())
            execSQL(INSERT_CATEGORY)
            execSQL("""
                INSERT INTO budgets (categoryId, amount, period, currency)
                VALUES (1, '3000.00', 'MONTHLY', 'RUB')
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_2_3)

        // Deposit preserved + new columns present
        db.query("SELECT initialAmount, rateTiersJson FROM deposits").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("200000.00", c.getString(0))
            assertNull(c.getString(1)) // rateTiersJson NULL for migrated rows
        }
        // Budget preserved + accountIds column added as NULL
        db.query("SELECT amount, accountIds FROM budgets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("3000.00", c.getString(0))
            assertNull(c.getString(1))
        }
        // Account preserved intact
        db.query("SELECT COUNT(*) FROM accounts").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"

        private const val INSERT_ACCOUNT = """
            INSERT INTO accounts
                (name, type, currency, colorHex, iconName, balance, isArchived, createdAt, sortOrder)
            VALUES ('Card', 'CARD', 'RUB', '#000', 'CreditCard', '10000.00', 0, '2026-01-01', 0)
        """

        private const val INSERT_ACCOUNT2 = """
            INSERT INTO accounts
                (name, type, currency, colorHex, iconName, balance, isArchived, createdAt, sortOrder)
            VALUES ('Savings', 'DEPOSIT', 'RUB', '#111', 'AccountBalance', '50000.00', 0, '2026-01-01', 1)
        """

        private const val INSERT_ACCOUNT3 = """
            INSERT INTO accounts
                (name, type, currency, colorHex, iconName, balance, isArchived, createdAt, sortOrder)
            VALUES ('Investment', 'DEPOSIT', 'RUB', '#222', 'AccountBalance', '30000.00', 0, '2026-01-01', 2)
        """

        private const val INSERT_CATEGORY = """
            INSERT INTO categories
                (name, type, colorHex, iconName, parentCategoryId, isDefault, sortOrder)
            VALUES ('Food', 'EXPENSE', '#FF7043', 'Restaurant', NULL, 0, 0)
        """
    }
}
