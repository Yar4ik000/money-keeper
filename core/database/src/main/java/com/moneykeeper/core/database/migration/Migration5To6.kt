package com.moneykeeper.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Per-budget threshold overrides; nullable — null means "use global default".
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `warningThreshold` INTEGER")
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `criticalThreshold` INTEGER")
    }
}
