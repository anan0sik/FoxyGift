package com.foxygift.pos.core.security

import com.foxygift.pos.data.repository.ProvisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class UserRole {
    CASHIER,
    PROLONG,
    ADMIN
}

@Singleton
class AuthSessionManager @Inject constructor(
    private val provisionRepository: ProvisionRepository,
) {
    private val _currentRole = MutableStateFlow<UserRole?>(null)
    val currentRole: StateFlow<UserRole?> = _currentRole.asStateFlow()

    fun login(role: UserRole) {
        _currentRole.value = role
    }

    fun logout() {
        _currentRole.value = null
    }

    fun isLoggedIn(): Boolean = _currentRole.value != null

    fun hasRole(requiredRole: UserRole): Boolean {
        val current = _currentRole.value ?: return false
        return when (requiredRole) {
            UserRole.CASHIER -> true // Cashier, Prolong, Admin can all access cashier functions
            UserRole.PROLONG -> current == UserRole.PROLONG || current == UserRole.ADMIN
            UserRole.ADMIN   -> current == UserRole.ADMIN
        }
    }

    /**
     * Resolves the UserRole for a given PIN.
     * Returns null if PIN is invalid.
     */
    suspend fun resolveRoleForPin(pin: String): UserRole? = withContext(Dispatchers.Default) {
        val saltHex     = provisionRepository.getSaltHex()
        val adminHash   = provisionRepository.getAdminHash()
        val cashierHash = provisionRepository.getCashierHash()
        val prolongHash = provisionRepository.getProlongHash()

        if (saltHex.isBlank() || (adminHash.isBlank() && cashierHash.isBlank() && prolongHash.isBlank())) {
            // Unprovisioned dev fallback
            return@withContext if (pin == "1234") UserRole.ADMIN else null
        }

        if (PinHasher.verifyPin(pin, saltHex, adminHash))   return@withContext UserRole.ADMIN
        if (PinHasher.verifyPin(pin, saltHex, prolongHash)) return@withContext UserRole.PROLONG
        if (PinHasher.verifyPin(pin, saltHex, cashierHash)) return@withContext UserRole.CASHIER

        null
    }
}
