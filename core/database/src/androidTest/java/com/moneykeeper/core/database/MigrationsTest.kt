package com.moneykeeper.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Шаблон миграционных тестов — заполняется при первом bump версии схемы (v1→v2).
 * @RunWith убран намеренно: JUnit4 падает на классе без @Test методов.
 * При добавлении migrate_1_to_2() вернуть @RunWith(AndroidJUnit4::class).
 *
 * Важно: MigrationTestHelper использует стандартный openHelper (НЕ SQLCipher) —
 * шифрование не влияет на логику SQL-миграций.
 */
class MigrationsTest {

    @Suppress("unused")
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // @Test fun migrate_1_to_2() { ... }  — добавить при bump VERSION = 2
}
