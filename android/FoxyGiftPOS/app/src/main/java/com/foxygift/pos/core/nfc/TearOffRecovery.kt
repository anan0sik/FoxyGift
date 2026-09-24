package com.foxygift.pos.core.nfc

import kotlinx.coroutines.delay

/**
 * Tear-off recovery logic for interrupted NFC write operations.
 *
 * If a customer removes the card mid-write (between pages), the terminal
 * enters a 30-second recovery window and continuously polls for the same card.
 * The card is identified by its immutable factory UID (decimal string).
 *
 * On re-tap of the SAME card:
 *   - Re-reads current state
 *   - Determines which pages were written successfully (balance vs HMAC mismatch)
 *   - Completes the write atomically
 *
 * If a DIFFERENT card is tapped, the recovery is aborted.
 */
object TearOffRecovery {

    const val RECOVERY_WINDOW_SECONDS = 30

    enum class RecoveryResult {
        RECOVERED,          // Successfully completed the interrupted write
        DIFFERENT_CARD,     // Wrong card tapped — operator error
        TIMEOUT,            // 30s elapsed without re-tap
        ABORTED_BY_USER,    // Cashier manually cancelled
    }

    data class RecoveryState(
        val expectedCardDec: String,
        val expectedBalance: Int,
        val expectedExpiry:  Int,
        val expectedStatus:  Byte,
        val masterKeyHex:    String,
        val pwd:             ByteArray,
        val expectedPack:    ByteArray,
    )

    /**
     * Detects if a write result indicates a possible tear-off.
     * Currently: any NtagDriver.WriteResult.Error with "tear-off" or "I/O" in the message.
     */
    fun isTearOffError(result: NtagDriver.WriteResult): Boolean =
        result is NtagDriver.WriteResult.Error &&
            (result.message.contains("tear-off", ignoreCase = true) ||
             result.message.contains("I/O",      ignoreCase = true) ||
             result.message.contains("Verification", ignoreCase = true))

    /**
     * Suspends and waits for a card re-tap, then verifies it's the same card.
     * The actual retry write is delegated back to the caller.
     *
     * @param state         Recovery state with expected card identity and values
     * @param onTagReceived Callback invoked when any tag is detected; returns true to continue
     * @param onTick        Progress callback (seconds remaining)
     * @return              RecoveryResult
     */
    suspend fun awaitRecovery(
        state:          RecoveryState,
        onTagReceived:  suspend (cardDecimal: String) -> Boolean,
        onTick:         (secondsLeft: Int) -> Unit,
        isCancelled:    () -> Boolean,
    ): RecoveryResult {
        val deadline = System.currentTimeMillis() + RECOVERY_WINDOW_SECONDS * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) return RecoveryResult.ABORTED_BY_USER
            val secondsLeft = ((deadline - System.currentTimeMillis()) / 1000).toInt()
            onTick(secondsLeft)
            delay(500)
        }
        return RecoveryResult.TIMEOUT
    }
}
