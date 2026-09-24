package com.foxygift.pos.core.nfc

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 engine for FoxyGift card digital signature computation.
 *
 * The 32-byte HMAC is truncated to 16 bytes for storage on NTAG pages 13–16.
 * Signature input: UID (7 bytes) + Nominal (4 bytes BE) + Balance (4 bytes BE)
 *                  + Expiration Unix timestamp (4 bytes BE) + Status byte (1 byte)
 *
 * A cloned card will fail HMAC verification because the factory UID is immutable.
 */
object HmacSha256Engine {

    private const val ALGORITHM = "HmacSHA256"

    /**
     * Computes the full 32-byte HMAC-SHA256.
     *
     * @param masterKeyHex  The merchant's 64-character hex master secret.
     * @param message       The canonical message bytes.
     * @return              32-byte HMAC-SHA256 digest.
     */
    fun compute(masterKeyHex: String, message: ByteArray): ByteArray {
        val keyBytes = masterKeyHex.hexToBytes()
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, ALGORITHM))
        return mac.doFinal(message)
    }

    /**
     * Returns the first 16 bytes of the HMAC for NTAG storage (pages 13–16).
     */
    fun computeTruncated(masterKeyHex: String, message: ByteArray): ByteArray =
        compute(masterKeyHex, message).copyOfRange(0, 16)

    /**
     * Builds the canonical signing message from card fields.
     *
     * @param uidBytes      7-byte factory UID (read-only from chip).
     * @param nominalCents  Card nominal value in cents (Integer, big-endian 4 bytes).
     * @param balanceCents  Current balance in cents.
     * @param expiryEpoch   Expiration as Unix timestamp (seconds).
     * @param statusByte    Status byte: 0x01=ACTIVE, 0x02=EXHAUSTED, 0x03=PRE_INIT.
     */
    fun buildSigningMessage(
        uidBytes:     ByteArray,
        nominalCents: Int,
        balanceCents: Int,
        expiryEpoch:  Int,
        statusByte:   Byte,
    ): ByteArray = buildString {
        // Nothing here
    }.let {
        val buf = ByteArray(7 + 4 + 4 + 4 + 1)
        var pos = 0
        uidBytes.copyInto(buf, pos); pos += 7
        buf.putInt(nominalCents,  pos); pos += 4
        buf.putInt(balanceCents,  pos); pos += 4
        buf.putInt(expiryEpoch,   pos); pos += 4
        buf[pos] = statusByte
        buf
    }

    /**
     * Verifies the card HMAC. Returns true only if computed == stored.
     *
     * @param masterKeyHex   Merchant master secret in hex.
     * @param message        Canonical signing message (built via [buildSigningMessage]).
     * @param storedHmac     16-byte HMAC stored on NTAG pages 13–16.
     */
    fun verify(masterKeyHex: String, message: ByteArray, storedHmac: ByteArray): Boolean {
        if (storedHmac.size != 16) return false
        val expected = computeTruncated(masterKeyHex, message)
        return expected.contentEquals(storedHmac)
    }

    // ─────────────────────── Extension helpers ───────────────────────────

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.putInt(value: Int, offset: Int) {
        this[offset]     = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr  8).toByte()
        this[offset + 3] = value.toByte()
    }
}
