package com.foxygift.pos.core.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    /**
     * Hashes a 4–6 digit PIN using PBKDF2WithHmacSHA256 with 100,000 iterations and 256 bits,
     * exactly matching Web Crypto Subtle in admin_provisioning_tool.html.
     */
    fun hashPin(pin: String, saltBytes: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Verifies entered PIN against stored Base64 hash using constant-time comparison.
     */
    fun verifyPin(enteredPin: String, saltHex: String, storedHashBase64: String): Boolean {
        if (saltHex.isBlank() || storedHashBase64.isBlank()) return false
        val saltBytes = saltHex.hexToBytes()
        if (saltBytes.isEmpty()) return false
        return runCatching {
            val computedHash = hashPin(enteredPin, saltBytes)
            MessageDigest.isEqual(
                computedHash.toByteArray(Charsets.UTF_8),
                storedHashBase64.toByteArray(Charsets.UTF_8)
            )
        }.getOrDefault(false)
    }

    private fun String.hexToBytes(): ByteArray {
        if (length % 2 != 0) return ByteArray(0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
