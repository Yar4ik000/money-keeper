package com.moneykeeper.core.database

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Вызывается Room один раз — при создании новой БД (первый запуск после setup-пароля).
 * Вставляем дефолтные категории через raw SQL, так как DAO недоступны в этот момент
 * (AppDatabase ещё не возвращена из build()).
 */
class PrepopulateCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        // EXPENSE-категории
        val expense = listOf(
            Triple("Еда и рестораны",  "EXPENSE", "#FF7043" to "Restaurant"),
            Triple("Транспорт",        "EXPENSE", "#42A5F5" to "DirectionsBus"),
            Triple("ЖКХ",              "EXPENSE", "#66BB6A" to "Home"),
            Triple("Здоровье",         "EXPENSE", "#EC407A" to "LocalHospital"),
            Triple("Развлечения",      "EXPENSE", "#AB47BC" to "SportsEsports"),
            Triple("Одежда",           "EXPENSE", "#26C6DA" to "Checkroom"),
            Triple("Связь",            "EXPENSE", "#FFA726" to "PhoneAndroid"),
            Triple("Прочее",           "EXPENSE", "#BDBDBD" to "MoreHoriz"),
        )
        val income = listOf(
            Triple("Зарплата",  "INCOME", "#66BB6A" to "Work"),
            Triple("Фриланс",   "INCOME", "#42A5F5" to "Laptop"),
            Triple("Вклады",    "INCOME", "#FFA726" to "Savings"),
            Triple("Прочее",    "INCOME", "#BDBDBD" to "MoreHoriz"),
        )

        (expense + income).forEachIndexed { index, (name, type, colorIcon) ->
            val (color, icon) = colorIcon
            db.execSQL(
                "INSERT INTO categories (name, type, colorHex, iconName, isDefault, sortOrder) " +
                "VALUES (?, ?, ?, ?, 1, ?)",
                arrayOf(name, type, color, icon, index),
            )
        }
    }
}
