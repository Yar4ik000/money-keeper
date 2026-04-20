package com.moneykeeper.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.domain.repository.SettingsRepository
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the app's foreground/background transitions via [ProcessLifecycleOwner].
 *
 * When the app goes to background ([onStop]), a coroutine is started that waits for the
 * configured timeout and then clears the master key + closes the database.
 * [DatabaseProvider.state] transitions to Idle, which [AuthGateViewModel] observes and
 * translates to [AuthState.Locked].
 *
 * When the app returns to the foreground ([onStart]), the pending lock job is cancelled.
 */
@Singleton
class AppAutoLockObserver @Inject constructor(
    private val masterKeyHolder: MasterKeyHolder,
    private val databaseProvider: DatabaseProvider,
    private val settingsRepository: SettingsRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lockJob: Job? = null

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            val timeout = settingsRepository.settings.first().autoLockTimeoutMinutes
            if (timeout < 0) return@launch  // -1 = disabled
            lockJob = scope.launch {
                delay(timeout * 60_000L)
                if (databaseProvider.state.value == DatabaseProvider.State.Initialized) {
                    masterKeyHolder.clear()
                    databaseProvider.close()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lockJob?.cancel()
        lockJob = null
    }
}
