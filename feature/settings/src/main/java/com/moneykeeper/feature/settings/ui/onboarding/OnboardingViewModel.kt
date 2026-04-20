package com.moneykeeper.feature.settings.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    fun completeOnboarding() = viewModelScope.launch {
        val current = settingsRepo.settings.first()
        settingsRepo.updateSettings(current.copy(onboardingCompleted = true))
    }
}
