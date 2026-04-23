package com.moneykeeper.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.AppSettings
import com.moneykeeper.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TipViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun markDashboardTipSeen() = markSeen { copy(seenTipDashboard = true) }
    fun markAccountsTipSeen() = markSeen { copy(seenTipAccounts = true) }
    fun markAnalyticsTipSeen() = markSeen { copy(seenTipAnalytics = true) }
    fun markForecastTipSeen() = markSeen { copy(seenTipForecast = true) }
    fun markBudgetsTipSeen() = markSeen { copy(seenTipBudgets = true) }

    fun markAllTipsSeen() = viewModelScope.launch {
        settingsRepo.updateSettings(
            settings.value.copy(
                seenTipDashboard = true,
                seenTipAccounts = true,
                seenTipAnalytics = true,
                seenTipForecast = true,
                seenTipBudgets = true,
            )
        )
    }

    private fun markSeen(update: AppSettings.() -> AppSettings) = viewModelScope.launch {
        settingsRepo.updateSettings(settings.value.update())
    }
}
