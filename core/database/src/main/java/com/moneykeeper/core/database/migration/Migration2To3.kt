package com.moneykeeper.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // endDate: NOT NULL → nullable; add rateTiersJson column.
        // SQLite doesn't support ALTER COLUMN, so we recreate the table.
        db.execSQL("""
            CREATE TABLE `deposits_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `initialAmount` TEXT NOT NULL,
                `interestRate` TEXT NOT NULL,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT,
                `isCapitalized` INTEGER NOT NULL,
                `capitalizationPeriod` TEXT,
                `notifyDaysBefore` TEXT NOT NULL,
                `autoRenew` INTEGER NOT NULL,
                `payoutAccountId` INTEGER,
                `isActive` INTEGER NOT NULL,
                `rateTiersJson` TEXT,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`payoutAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `deposits_new`
                (`id`,`accountId`,`initialAmount`,`interestRate`,`startDate`,`endDate`,
                 `isCapitalized`,`capitalizationPeriod`,`notifyDaysBefore`,`autoRenew`,
                 `payoutAccountId`,`isActive`,`rateTiersJson`)
            SELECT `id`,`accountId`,`initialAmount`,`interestRate`,`startDate`,`endDate`,
                   `isCapitalized`,`capitalizationPeriod`,`notifyDaysBefore`,`autoRenew`,
                   `payoutAccountId`,`isActive`, NULL
            FROM `deposits`
        """.trimIndent())
        db.execSQL("DROP TABLE `deposits`")
        db.execSQL("ALTER TABLE `deposits_new` RENAME TO `deposits`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deposits_accountId` ON `deposits` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deposits_payoutAccountId` ON `deposits` (`payoutAccountId`)")
    }
}
