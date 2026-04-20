package com.moneykeeper.core.database

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Вставляет дефолтные категории при создании БД и при каждом открытии, если таблица пустая.
 * Второй случай покрывает БД, созданные до введения PrepopulateCallback (первый запуск
 * после установки обновления).
 */
class PrepopulateCallback(private val context: Context) : RoomDatabase.Callback() {

    private val defaultCategories = buildList {
        addExpense("Еда и рестораны",  "#FF7043", "Restaurant")
        addExpense("Транспорт",        "#42A5F5", "DirectionsBus")
        addExpense("ЖКХ",              "#66BB6A", "Home")
        addExpense("Здоровье",         "#EC407A", "LocalHospital")
        addExpense("Развлечения",      "#AB47BC", "SportsEsports")
        addExpense("Одежда",           "#26C6DA", "Checkroom")
        addExpense("Связь",            "#FFA726", "PhoneAndroid")
        addExpense("Образование",      "#5C6BC0", "School")
        addExpense("Кафе и бары",      "#EF5350", "LocalBar")
        addExpense("Продукты",         "#8D6E63", "ShoppingCart")
        addExpense("Прочее",           "#BDBDBD", "MoreHoriz")
        addIncome("Зарплата",  "#66BB6A", "Work")
        addIncome("Фриланс",   "#42A5F5", "Laptop")
        addIncome("Вклады",    "#FFA726", "Savings")
        addIncome("Кэшбэк",    "#26C6DA", "CreditCard")
        addIncome("Прочее",    "#BDBDBD", "MoreHoriz")
    }

    override fun onCreate(db: SupportSQLiteDatabase) = seedIfEmpty(db)
    override fun onOpen(db: SupportSQLiteDatabase) = seedIfEmpty(db)

    private fun seedIfEmpty(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT COUNT(*) FROM categories")
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        if (count > 0) return

        defaultCategories.forEachIndexed { index, row ->
            db.execSQL(
                "INSERT INTO categories (name, type, colorHex, iconName, isDefault, sortOrder) " +
                "VALUES (?, ?, ?, ?, 1, ?)",
                arrayOf(row.name, row.type, row.color, row.icon, index),
            )
        }
    }

    private data class CategoryRow(val name: String, val type: String, val color: String, val icon: String)

    private fun MutableList<CategoryRow>.addExpense(name: String, color: String, icon: String) =
        add(CategoryRow(name, "EXPENSE", color, icon))

    private fun MutableList<CategoryRow>.addIncome(name: String, color: String, icon: String) =
        add(CategoryRow(name, "INCOME", color, icon))
}
