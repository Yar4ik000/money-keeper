package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holder для AppDatabase с отложенной инициализацией.
 *
 * AppDatabase нельзя создать через @Provides @Singleton напрямую: для открытия
 * SQLCipher-БД нужен db_key, который доступен только после успешного ввода пароля
 * пользователем. Решение: этот класс хранит ссылку и выдаёт её по [require] после
 * вызова [initialize] из UnlockController (§3.5).
 *
 * UI-гейтинг: MainActivity наблюдает [state] и компонует NavHost только в состоянии
 * [State.Initialized] — это гарантирует, что ни один feature-экран не создаст DAO
 * до открытия БД.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStorage: DatabaseKeyStorage,
) {
    @Volatile private var db: AppDatabase? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    fun initialize(dbKey: ByteArray) {
        if (db != null) return

        System.loadLibrary("sqlcipher")
        val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(dbKey)

        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(PrepopulateCallback(context))
            .build()
        _state.value = State.Initialized
    }

    @Synchronized
    fun close() {
        db?.close()
        db = null
        _state.value = State.Idle
    }

    fun require(): AppDatabase =
        db ?: error("AppDatabase not initialized. Call DatabaseProvider.initialize(dbKey) after unlock.")

    sealed interface State {
        data object Idle : State        // до первого unlock
        data object Initialized : State // БД открыта
    }
}
