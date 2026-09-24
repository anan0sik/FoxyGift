package com.foxygift.pos.ui.screens.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.core.security.AuthSessionManager
import com.foxygift.pos.core.security.PinSecurityManager
import com.foxygift.pos.core.security.UserRole
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinUiState(
    val pinDigits:           String  = "",
    val isAuthenticated:     Boolean = false,
    val isLocked:            Boolean = false,
    val isPermanentlyLocked: Boolean = false,
    val lockoutRemainingMs:  Long    = 0L,
    val hasError:            Boolean = false,
    val remainingAttempts:   Int?    = null,
)

@HiltViewModel
class PinViewModel @Inject constructor(
    private val pinSecurityManager: PinSecurityManager,
    private val provisionRepository: ProvisionRepository,
    private val authSessionManager:  AuthSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    init {
        refreshLockStatus()
        startLockCountdown()
    }

    fun onResumeScreen() {
        refreshLockStatus()
        _uiState.value = _uiState.value.copy(
            pinDigits       = "",
            isAuthenticated = false,
            hasError        = false,
            remainingAttempts = null,
        )
    }

    fun onDigit(digit: String) {
        val current = _uiState.value
        if (current.isLocked || current.isPermanentlyLocked) return
        if (current.pinDigits.length >= 6) return

        val newPin = current.pinDigits + digit
        _uiState.value = current.copy(pinDigits = newPin, hasError = false, remainingAttempts = null)

        viewModelScope.launch {
            if (newPin.length >= 4) {
                val matches = authSessionManager.resolveRoleForPin(newPin) != null
                if (matches) {
                    submitPin(newPin)
                    return@launch
                }
            }
            if (newPin.length >= 6) {
                submitPin(newPin)
            }
        }
    }

    fun onSubmitManual() {
        val current = _uiState.value
        if (current.isLocked || current.isPermanentlyLocked) return
        if (current.pinDigits.length >= 4) {
            submitPin(current.pinDigits)
        }
    }

    fun onDelete() {
        val current = _uiState.value
        if (current.pinDigits.isNotEmpty()) {
            _uiState.value = current.copy(
                pinDigits         = current.pinDigits.dropLast(1),
                hasError          = false,
                remainingAttempts = null,
            )
        }
    }

    private fun submitPin(pin: String) {
        viewModelScope.launch {
            val locationName = provisionRepository.getLocationName().ifBlank { "FoxyGift POS" }
            val terminalId   = provisionRepository.getTerminalId().ifBlank { "TERMINAL_001" }

            var resolvedRole: UserRole? = null

            val result = pinSecurityManager.checkPin(
                enteredPin   = pin,
                verifier     = { entered ->
                    val role = authSessionManager.resolveRoleForPin(entered)
                    if (role != null) {
                        resolvedRole = role
                        true
                    } else false
                },
                terminalId   = terminalId,
                locationName = locationName,
            )

            when (result) {
                is PinSecurityManager.PinCheckResult.Correct -> {
                    resolvedRole?.let { authSessionManager.login(it) }
                    _uiState.value = _uiState.value.copy(isAuthenticated = true, pinDigits = "")
                }
                is PinSecurityManager.PinCheckResult.Incorrect -> {
                    _uiState.value = _uiState.value.copy(
                        pinDigits         = "",
                        hasError          = true,
                        remainingAttempts = result.remainingFree,
                    )
                }
                is PinSecurityManager.PinCheckResult.Locked -> {
                    _uiState.value = _uiState.value.copy(
                        pinDigits          = "",
                        isLocked           = true,
                        lockoutRemainingMs = result.remainingMs,
                    )
                }
                is PinSecurityManager.PinCheckResult.PermanentlyLocked -> {
                    _uiState.value = _uiState.value.copy(
                        pinDigits           = "",
                        isPermanentlyLocked = true,
                    )
                }
            }
        }
    }

    private fun refreshLockStatus() {
        val status = pinSecurityManager.getLockoutStatus()
        _uiState.value = _uiState.value.copy(
            isPermanentlyLocked = status.isPermanentlyLocked,
            isLocked            = status.isLocked,
            lockoutRemainingMs  = status.remainingMs,
        )
    }

    private fun startLockCountdown() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _uiState.value
                if (current.isLocked && !current.isPermanentlyLocked) {
                    val remaining = current.lockoutRemainingMs - 1000L
                    if (remaining <= 0) {
                        _uiState.value = current.copy(isLocked = false, lockoutRemainingMs = 0L)
                    } else {
                        _uiState.value = current.copy(lockoutRemainingMs = remaining)
                    }
                }
            }
        }
    }
}
