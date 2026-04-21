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

        // Apply any pending restore before Room opens the database. This handles the case
        // where restoreBackup() wrote the .restore-pending file but the process crashed
        // before restartProcess() could do the atomic swap — on the next cold start, the
        // restore is applied here transparently without requiring user intervention.
        applyPendingRestoreIfNeeded()

        // SupportOpenHelperFactory keeps a reference, not a copy, of the key byte array.
        // The caller zeros dbKey after initialize() returns (security hygiene), which would
        // corrupt any subsequent pool connection opened by SQLCipher. Use a dedicated copy
        // whose lifetime is tied to this factory/database pair instead.
        val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(dbKey.copyOf())

        val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(PrepopulateCallback(context))
            .build()

        // Room is lazy — force-open now so a wrong-key exception surfaces here (inside
        // UnlockController's try-catch) rather than later on a background thread with no handler.
        database.openHelper.writableDatabase

        db = database
        _state.value = State.Initialized
    }

    private fun applyPendingRestoreIfNeeded() {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val dbParent = dbFile.parentFile ?: return
        val pending = java.io.File(dbParent, "${dbFile.name}.restore-pending")
        if (!pending.exists()) return
        try {
            java.io.File(dbParent, "${dbFile.name}-wal").delete()
            java.io.File(dbParent, "${dbFile.name}-shm").delete()
            java.nio.file.Files.move(
                pending.toPath(), dbFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            pending.delete() // Failed swap — open whatever DB exists; discard corrupt pending file.
        }
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
