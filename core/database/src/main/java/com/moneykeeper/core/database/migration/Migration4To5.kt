package com.moneykeeper.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Replace single categoryId (with FK) with comma-separated categoryIds (no FK),
        // matching the pattern already used by accountIds.
        db.execSQL("""
            CREATE TABLE `budgets_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryIds` TEXT,
                `amount` TEXT NOT NULL,
                `period` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `accountIds` TEXT
            )
        """.trimIndent())
        // Preserve existing rows — old single categoryId becomes a one-element string "N"
        db.execSQL("""
            INSERT INTO `budgets_new`
                (`id`, `categoryIds`, `amount`, `period`, `currency`, `accountIds`)
            SELECT `id`, CAST(`categoryId` AS TEXT), `amount`, `period`, `currency`, `accountIds`
            FROM `budgets`
        """.trimIndent())
        db.execSQL("DROP TABLE `budgets`")
        db.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")
    }
}
