package com.foxygift.pos.ui.screens.home

import androidx.lifecycle.ViewModel
import com.foxygift.pos.core.security.AuthSessionManager
import com.foxygift.pos.core.security.UserRole
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val authSessionManager: AuthSessionManager,
    private val provisionRepository: ProvisionRepository,
) : ViewModel() {

    val currentRole: StateFlow<UserRole?> = authSessionManager.currentRole

    fun getLocationName(): String = provisionRepository.getLocationName().ifBlank { "FoxyGift POS" }

    fun getTerminalId(): String = provisionRepository.getTerminalId().ifBlank { "TERMINAL_001" }

    fun hasRole(role: UserRole): Boolean = authSessionManager.hasRole(role)

    fun logout() {
        authSessionManager.logout()
    }
}
