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

    fun toggleDepositNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings(settings.value.copy(depositNotificationsEnabled = enabled))
        }
    }

    fun toggleRecurringReminders(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings(settings.value.copy(recurringRemindersEnabled = enabled))
        }
    }

    fun updateNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepo.updateSettings(
                settings.value.copy(notificationHour = hour, notificationMinute = minute)
            )
            workScheduler.rescheduleNotifications(hour, minute)
        }
    }
}
