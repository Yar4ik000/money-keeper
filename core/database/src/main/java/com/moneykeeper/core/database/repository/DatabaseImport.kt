package com.moneykeeper.core.database.repository

import java.io.File

/**
 * Imports an unencrypted (plaintext) SQLite database into a new SQLCipher-encrypted file.
 *
 * Replay strategy: DDL from sqlite_schema + row-by-row INSERT, then set user_version.
 * We keep the encrypted side as MAIN (not ATTACH) to avoid the ATTACH KEY mismatch —
 * SQLCipher's sqlite3_key_v2 with raw bytes uses PBKDF2 under the hood, whereas
 * ATTACH … KEY "x'hex'" bypasses PBKDF2 and would produce a different derived key.
 *
 * @param dbVersion the schema version to stamp onto the output file (typically from the
 *   backup manifest). Room reads user_version to choose onCreate vs onUpgrade; leaving it
 *   at 0 makes Room call onCreate() instead of running migrations.
 */
internal fun importPlainIntoEncrypted(plainDb: File, target: File, dbKey: ByteArray, dbVersion: Int) {
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

        // Preserve the schema version from the backup manifest. user_version lives in the DB
        // file header — it is NOT part of DDL or any table, so the DDL/data replay above
        // leaves it at 0. Room reads user_version to decide onCreate vs onUpgrade: without
        // this, Room treats the restored DB as brand-new and calls onCreate() instead of
        // running migrations, then fails schema validation because the tables are at the old
        // version. We use the manifest value directly rather than PRAGMA src.user_version
        // because SQLCipher does not support the schema-qualified PRAGMA syntax for attached
        // unencrypted databases.
        db.execSQL("PRAGMA user_version = $dbVersion")

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DETACH DATABASE src")
    } finally {
        db.close()
    }
}
