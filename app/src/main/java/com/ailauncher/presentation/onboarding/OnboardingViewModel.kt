package com.ailauncher.presentation.onboarding

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.auth.GoogleAuthRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val googleSignInConfigured: Boolean,
    val signingIn: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: GoogleAuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState(googleSignInConfigured = auth.isGoogleSignInConfigured))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun signIn(activity: Activity) {
        if (_uiState.value.signingIn) return
        _uiState.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            auth.signIn(activity)
                .onSuccess { finish() }
                .onFailure { e ->
                    val message = if (e is GoogleAuthRepository.SignInCancelledException) null else e.message
                    _uiState.update { it.copy(signingIn = false, error = message) }
                }
        }
    }

    /** Guest mode: the launcher is fully usable offline, without any account. */
    fun continueAsGuest() {
        viewModelScope.launch {
            auth.continueAsGuest()
            finish()
        }
    }

    private suspend fun finish() {
        settingsRepository.updateLauncherSettings { it.copy(onboardingCompleted = true) }
        _uiState.update { it.copy(signingIn = false, finished = true) }
    }
}
