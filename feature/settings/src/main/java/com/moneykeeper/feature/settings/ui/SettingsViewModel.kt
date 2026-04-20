package com.moneykeeper.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.AppSettings
import com.moneykeeper.core.domain.repository.SettingsRepository
import com.moneykeeper.core.domain.repository.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun toggleDepositNotifications(enabled: Boolean) = update { copy(depositNotificationsEnabled = enabled) }

    fun toggleRecurringReminders(enabled: Boolean) = update { copy(recurringRemindersEnabled = enabled) }

    fun updateNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepo.updateSettings(settings.value.copy(notificationHour = hour, notificationMinute = minute))
            workScheduler.rescheduleNotifications(hour, minute)
        }
    }

    fun setThemeMode(mode: String) = update { copy(themeMode = mode) }

    fun setCurrency(code: String) = update { copy(currencyCode = code) }

    private fun update(block: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settingsRepo.updateSettings(settings.value.block()) }
    }
}
