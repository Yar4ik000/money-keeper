package com.moneykeeper.feature.settings.ui.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.feature.auth.domain.BiometricEnrollment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val biometricEnrollment: BiometricEnrollment,
) : ViewModel() {

    val isBiometricAvailable: Boolean = biometricEnrollment.isAvailable()

    private val _isBiometricEnrolled = MutableStateFlow(biometricEnrollment.isEnrolled())
    val isBiometricEnrolled = _isBiometricEnrolled.asStateFlow()

    private val _enrollError = MutableStateFlow<String?>(null)
    val enrollError = _enrollError.asStateFlow()

    fun enrollBiometric(activity: FragmentActivity) = viewModelScope.launch {
        val result = biometricEnrollment.enroll(activity)
        when (result) {
            BiometricEnrollment.EnrollResult.Success -> _isBiometricEnrolled.value = true
            BiometricEnrollment.EnrollResult.Cancelled -> Unit
            BiometricEnrollment.EnrollResult.Unavailable ->
                _enrollError.update { "Биометрия недоступна на этом устройстве" }
            BiometricEnrollment.EnrollResult.NotEnrolledInSystem ->
                _enrollError.update { "Сначала добавьте отпечаток пальца в настройках телефона" }
            BiometricEnrollment.EnrollResult.NotUnlocked ->
                _enrollError.update { "Приложение не разблокировано" }
            is BiometricEnrollment.EnrollResult.Error ->
                _enrollError.update { result.message }
        }
    }

    fun disableBiometric() {
        biometricEnrollment.disable()
        _isBiometricEnrolled.value = false
    }

    fun clearEnrollError() = _enrollError.update { null }
}
