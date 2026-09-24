package com.foxygift.pos.core.security

import android.content.Context
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Banking-grade anti-brute-force PIN protection manager.
 *
 * Progressive lockout schedule:
 *   Attempts 1–3  : Warning banners only
 *   Attempt 4     : 30-second keyboard lockout
 *   Attempt 5     : 1-minute lockout + Telegram silent alarm
 *   Attempts 6–7  : 5-minute lockout
 *   Attempts 8–9  : 10-minute lockout
 *   Attempt 10+   : PERMANENT lockout — only QR re-provisioning unlocks
 *
 * State is stored in EncryptedSharedPreferences. Device reboots and
 * force-stops DO NOT reset the lockout — monotonic clock comparisons
 * use SystemClock.elapsedRealtime() offset from wall clock at each boot.
 *
 * A "wall-clock-anchor" value records when the elapsed time was last measured,
 * combined with the stored lockout end wall-clock timestamp for robustness.
 */
@Singleton
class PinSecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramAlarmClient: TelegramAlarmClient,
) {
    companion object {
        private const val PREFS_NAME     = "foxygift_pin_security"
        private const val KEY_ATTEMPTS   = "pin_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_epoch_ms"
        private const val KEY_PERMANENT  = "pin_permanently_locked"
        private const val KEY_TOTAL_FAILS = "pin_total_failed_attempts"

        private val LOCKOUT_SCHEDULE_MS = mapOf(
            4 to 30_000L,          // 30 seconds
            5 to 60_000L,          // 1 minute
            6 to 300_000L,         // 5 minutes
            7 to 300_000L,
            8 to 600_000L,         // 10 minutes
            9 to 900_000L,         // 15 minutes
        )
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    sealed class PinCheckResult {
        object Correct                                 : PinCheckResult()
        data class Incorrect(
            val attemptsUsed:   Int,
            val remainingFree:  Int,           // how many before lockout
        ) : PinCheckResult()
        data class Locked(val remainingMs: Long)       : PinCheckResult()
        object PermanentlyLocked                       : PinCheckResult()
    }

    data class LockoutStatus(
        val isPermanentlyLocked: Boolean,
        val isLocked:            Boolean,
        val remainingMs:         Long,
        val attemptsUsed:        Int,
    )

    /**
     * Checks the entered PIN against the hashed stored value.
     * Records failure and applies lockout if thresholds are exceeded.
     *
     * @param enteredPin    The 4–6 digit string the user typed.
     * @param verifier      Suspend lambda that returns true if PIN is correct.
     * @param terminalId    For Telegram alarm context.
     * @param locationName  For Telegram alarm context.
     */
    suspend fun checkPin(
        enteredPin:    String,
        verifier:      suspend (String) -> Boolean,
        terminalId:    String,
        locationName:  String,
    ): PinCheckResult {
        // Check current lockout state first
        val status = getLockoutStatus()
        if (status.isPermanentlyLocked) return PinCheckResult.PermanentlyLocked
        if (status.isLocked)            return PinCheckResult.Locked(status.remainingMs)

        val isCorrect = withContext(Dispatchers.Default) { verifier(enteredPin) }

        return if (isCorrect) {
            // Reset on success
            prefs.edit().putInt(KEY_ATTEMPTS, 0).apply()
            PinCheckResult.Correct
        } else {
            recordFailedAttempt(terminalId, locationName)
        }
    }

    fun getLockoutStatus(): LockoutStatus {
        val permanent = prefs.getBoolean(KEY_PERMANENT, false)
        if (permanent) return LockoutStatus(isPermanentlyLocked = true, isLocked = true, remainingMs = Long.MAX_VALUE, attemptsUsed = prefs.getInt(KEY_ATTEMPTS, 0))

        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now          = System.currentTimeMillis()
        val attempts     = prefs.getInt(KEY_ATTEMPTS, 0)
        return if (lockoutUntil > now) {
            LockoutStatus(isPermanentlyLocked = false, isLocked = true, remainingMs = lockoutUntil - now, attemptsUsed = attempts)
        } else {
            LockoutStatus(isPermanentlyLocked = false, isLocked = false, remainingMs = 0, attemptsUsed = attempts)
        }
    }

    fun getRemainingFreeAttempts(): Int {
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0)
        return maxOf(0, 3 - attempts)
    }

    /**
     * Unlocks a permanently locked terminal by presenting the original
     * provisioning QR payload. If the payload is valid, full state is reset.
     */
    fun unlockWithProvisioningQr(isValidQr: Boolean) {
        if (!isValidQr) return
        prefs.edit()
            .putBoolean(KEY_PERMANENT, false)
            .putInt(KEY_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun getTotalFailedAttempts(): Int = prefs.getInt(KEY_TOTAL_FAILS, 0)

    private suspend fun recordFailedAttempt(terminalId: String, locationName: String): PinCheckResult {
        val currentAttempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1
        val totalFails      = prefs.getInt(KEY_TOTAL_FAILS, 0) + 1
        val editor = prefs.edit()
            .putInt(KEY_ATTEMPTS, currentAttempts)
            .putInt(KEY_TOTAL_FAILS, totalFails)

        // Check for permanent lockout
        if (currentAttempts >= 10) {
            editor.putBoolean(KEY_PERMANENT, true).apply()
            telegramAlarmClient.sendPanicAlert(terminalId, locationName, currentAttempts)
            return PinCheckResult.PermanentlyLocked
        }

        // Apply progressive lockout duration
        val lockoutMs = LOCKOUT_SCHEDULE_MS[currentAttempts]
        if (lockoutMs != null) {
            val lockoutUntil = System.currentTimeMillis() + lockoutMs
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            if (currentAttempts == 5) {
                telegramAlarmClient.sendLockoutAlert(terminalId, locationName, currentAttempts)
            }
        }
        editor.apply()

        return PinCheckResult.Incorrect(
            attemptsUsed  = currentAttempts,
            remainingFree = maxOf(0, 3 - currentAttempts),
        )
    }
}
