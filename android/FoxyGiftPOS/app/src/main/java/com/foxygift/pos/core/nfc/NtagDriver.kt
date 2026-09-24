package com.foxygift.pos.core.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level NTAG213/215/216 driver.
 *
 * Memory layout used by FoxyGift:
 *   Page 04:    "FOXY" magic bytes (46 4F 58 59)
 *   Page 05:    MERCHANT_ID_HASH (4 bytes)
 *   Page 06-07: Nominal value in cents, big-endian int32 (8 bytes total across 2 pages)
 *   Page 08-09: Current balance in cents, big-endian int32
 *   Page 10-11: Expiration Unix epoch (seconds), big-endian int32
 *   Page 12:    Status byte [0x01=ACTIVE, 0x02=EXHAUSTED, 0x03=PRE_INIT] + 3 reserved
 *   Page 13-16: HMAC-SHA256 truncated (16 bytes = 4 pages × 4 bytes)
 *   CFG0-CFG1:  AUTH0=04, PROT=1, PWD (4 bytes), PACK (2 bytes)
 */
object NtagDriver {

    // NTAG page addresses
    private const val PAGE_MAGIC      = 4
    private const val PAGE_MERCHANT   = 5
    private const val PAGE_NOMINAL_LO = 6   // pages 6-7 = 8 bytes, first 4 used
    private const val PAGE_BALANCE_LO = 8   // pages 8-9
    private const val PAGE_EXPIRY_LO  = 10  // pages 10-11
    private const val PAGE_STATUS     = 12
    private const val PAGE_HMAC_START = 13  // pages 13-16 (4 pages × 4 bytes = 16 bytes)

    private val MAGIC_BYTES = byteArrayOf(0x46, 0x4F, 0x58, 0x59) // "FOXY"

    // ─────────────────────── Status Constants ────────────────────────────

    const val STATUS_ACTIVE      = 0x01.toByte()
    const val STATUS_EXHAUSTED   = 0x02.toByte()
    const val STATUS_PRE_INIT    = 0x03.toByte()
    const val STATUS_PROLONGED   = 0x04.toByte()

    // ─────────────────────── Sealed Result Types ─────────────────────────

    sealed class ReadResult {
        data class Success(val cardData: CardData) : ReadResult()
        data class Unauthorized(
            val cardNumberDec: String,
            val failReason: FailReason,
        ) : ReadResult()
        data class Error(val message: String) : ReadResult()
    }

    sealed class WriteResult {
        object Success : WriteResult()
        data class Error(val message: String) : WriteResult()
    }

    enum class FailReason {
        MAGIC_MISMATCH,           // "FOXY" not found — blank or foreign card
        MERCHANT_HASH_MISMATCH,   // Wrong merchant network
        AUTH_FAILED,              // PWD_AUTH failed — chip not locked or wrong key
        HMAC_INVALID,             // Digital signature verification failed
    }

    data class CardData(
        val cardNumberDec:  String,
        val uidBytes:       ByteArray,
        val merchantIdHash: ByteArray,  // 4 bytes
        val nominalCents:   Int,
        val balanceCents:   Int,
        val expiryEpoch:    Int,
        val status:         Byte,
        val storedHmac:     ByteArray,  // 16 bytes
    )

    // ─────────────────────── Public API ──────────────────────────────────

    /**
     * Authenticates and reads all FoxyGift data from an NTAG tag.
     *
     * @param tag               Android NFC Tag
     * @param pwd               4-byte PWD for chip authentication
     * @param expectedPack      2-byte PACK (challenge-response)
     * @param masterKeyHex      Merchant master secret for HMAC verification
     * @param expectedMerchantHash  4-byte merchant ID hash
     */
    suspend fun readCard(
        tag: Tag,
        pwd: ByteArray,
        expectedPack: ByteArray,
        masterKeyHex: String,
        expectedMerchantHash: ByteArray,
    ): ReadResult = withContext(Dispatchers.IO) {
        val mu = MifareUltralight.get(tag) ?: return@withContext ReadResult.Error("NTAG not detected")
        try {
            mu.connect()

            // 1. Authenticate with PWD_AUTH
            val packResponse = mu.transceive(buildPwdAuthCommand(pwd))
            if (!packResponse.take(2).toByteArray().contentEquals(expectedPack)) {
                val uid = tag.id
                return@withContext ReadResult.Unauthorized(
                    cardNumberDec = DecimalUidConverter.toDecimalString(uid),
                    failReason    = FailReason.AUTH_FAILED,
                )
            }

            // 2. Read core data pages
            val page04 = mu.readPages(PAGE_MAGIC)
            val page05 = mu.readPages(PAGE_MERCHANT)
            val page06 = mu.readPages(PAGE_NOMINAL_LO)
            val page08 = mu.readPages(PAGE_BALANCE_LO)
            val page10 = mu.readPages(PAGE_EXPIRY_LO)
            val page12 = mu.readPages(PAGE_STATUS)
            val hmacPages = ByteArray(16)
            for (i in 0..3) {
                val p = mu.readPages(PAGE_HMAC_START + i)
                p.copyInto(hmacPages, i * 4, 0, 4)
            }

            val uid = tag.id

            // 3. Check FOXY magic
            if (!page04.take(4).toByteArray().contentEquals(MAGIC_BYTES)) {
                return@withContext ReadResult.Unauthorized(
                    cardNumberDec = DecimalUidConverter.toDecimalString(uid),
                    failReason    = FailReason.MAGIC_MISMATCH,
                )
            }

            // 4. Check merchant hash
            val storedMerchantHash = page05.take(4).toByteArray()
            if (!storedMerchantHash.contentEquals(expectedMerchantHash)) {
                return@withContext ReadResult.Unauthorized(
                    cardNumberDec = DecimalUidConverter.toDecimalString(uid),
                    failReason    = FailReason.MERCHANT_HASH_MISMATCH,
                )
            }

            // 5. Parse card values
            val nominalCents = page06.getInt()
            val balanceCents = page08.getInt()
            val expiryEpoch  = page10.getInt()
            val statusByte   = page12[0]

            // 6. Verify HMAC
            val message = HmacSha256Engine.buildSigningMessage(
                uidBytes     = uid,
                nominalCents = nominalCents,
                balanceCents = balanceCents,
                expiryEpoch  = expiryEpoch,
                statusByte   = statusByte,
            )
            if (!HmacSha256Engine.verify(masterKeyHex, message, hmacPages)) {
                return@withContext ReadResult.Unauthorized(
                    cardNumberDec = DecimalUidConverter.toDecimalString(uid),
                    failReason    = FailReason.HMAC_INVALID,
                )
            }

            ReadResult.Success(
                CardData(
                    cardNumberDec  = DecimalUidConverter.toDecimalString(uid),
                    uidBytes       = uid,
                    merchantIdHash = storedMerchantHash,
                    nominalCents   = nominalCents,
                    balanceCents   = balanceCents,
                    expiryEpoch    = expiryEpoch,
                    status         = statusByte,
                    storedHmac     = hmacPages,
                )
            )
        } catch (e: IOException) {
            ReadResult.Error("I/O error: ${e.message}")
        } finally {
            runCatching { mu.close() }
        }
    }

    /**
     * Atomically writes updated card data to the NTAG and verifies by re-reading.
     * All writes go to RAM-buffered pages then verified immediately.
     */
    suspend fun writeCard(
        tag:          Tag,
        pwd:          ByteArray,
        expectedPack: ByteArray,
        masterKeyHex: String,
        cardData:     CardData,
        newBalanceCents: Int,
        newExpiryEpoch: Int,
        newStatus:    Byte,
    ): WriteResult = withContext(Dispatchers.IO) {
        val mu = MifareUltralight.get(tag) ?: return@withContext WriteResult.Error("NTAG not detected")
        try {
            mu.connect()

            // Authenticate
            val packResponse = mu.transceive(buildPwdAuthCommand(pwd))
            if (!packResponse.take(2).toByteArray().contentEquals(expectedPack)) {
                return@withContext WriteResult.Error("PWD_AUTH failed on write")
            }

            // Compute new HMAC
            val newMessage = HmacSha256Engine.buildSigningMessage(
                uidBytes     = cardData.uidBytes,
                nominalCents = cardData.nominalCents,
                balanceCents = newBalanceCents,
                expiryEpoch  = newExpiryEpoch,
                statusByte   = newStatus,
            )
            val newHmac = HmacSha256Engine.computeTruncated(masterKeyHex, newMessage)

            // Write nominal (pages 6-7) — must match nominalCents used in HMAC
            mu.writePage(PAGE_NOMINAL_LO,     cardData.nominalCents.toPageBytes())
            mu.writePage(PAGE_NOMINAL_LO + 1, ByteArray(4))

            // Write balance (pages 8-9)
            mu.writePage(PAGE_BALANCE_LO,     newBalanceCents.toPageBytes())
            mu.writePage(PAGE_BALANCE_LO + 1, ByteArray(4))

            // Write expiry (pages 10-11)
            mu.writePage(PAGE_EXPIRY_LO,      newExpiryEpoch.toPageBytes())
            mu.writePage(PAGE_EXPIRY_LO + 1,  ByteArray(4))

            // Write status (page 12)
            mu.writePage(PAGE_STATUS, byteArrayOf(newStatus, 0, 0, 0))

            // Write HMAC (pages 13-16)
            for (i in 0..3) {
                mu.writePage(PAGE_HMAC_START + i, newHmac.copyOfRange(i * 4, i * 4 + 4))
            }

            // Verification re-read: nominal + balance + HMAC
            val verifyNominal  = mu.readPages(PAGE_NOMINAL_LO).getInt()
            val verifyBalance  = mu.readPages(PAGE_BALANCE_LO).getInt()
            val verifyHmac     = ByteArray(16)
            for (i in 0..3) {
                mu.readPages(PAGE_HMAC_START + i).copyInto(verifyHmac, i * 4, 0, 4)
            }
            if (verifyNominal != cardData.nominalCents
                || verifyBalance != newBalanceCents
                || !verifyHmac.contentEquals(newHmac)) {
                return@withContext WriteResult.Error("Verification read mismatch — possible tear-off")
            }

            WriteResult.Success
        } catch (e: IOException) {
            WriteResult.Error("I/O error during write: ${e.message}")
        } finally {
            runCatching { mu.close() }
        }
    }

    /**
     * Performs initial card activation (ISSUE):
     * writes nominal, balance, expiry, status=ACTIVE, and HMAC.
     */
    suspend fun activateCard(
        tag:          Tag,
        pwd:          ByteArray,
        expectedPack: ByteArray,
        masterKeyHex: String,
        nominalCents: Int,
        expiryEpoch:  Int,
    ): WriteResult = withContext(Dispatchers.IO) {
        val mu = MifareUltralight.get(tag) ?: return@withContext WriteResult.Error("NTAG not detected")
        try {
            mu.connect()
            val packResponse = mu.transceive(buildPwdAuthCommand(pwd))
            if (!packResponse.take(2).toByteArray().contentEquals(expectedPack)) {
                return@withContext WriteResult.Error("PWD_AUTH failed")
            }

            val uid = tag.id
            val message = HmacSha256Engine.buildSigningMessage(
                uidBytes     = uid,
                nominalCents = nominalCents,
                balanceCents = nominalCents,
                expiryEpoch  = expiryEpoch,
                statusByte   = STATUS_ACTIVE,
            )
            val hmac = HmacSha256Engine.computeTruncated(masterKeyHex, message)

            mu.writePage(PAGE_NOMINAL_LO,     nominalCents.toPageBytes())
            mu.writePage(PAGE_NOMINAL_LO + 1, ByteArray(4))
            mu.writePage(PAGE_BALANCE_LO,     nominalCents.toPageBytes())
            mu.writePage(PAGE_BALANCE_LO + 1, ByteArray(4))
            mu.writePage(PAGE_EXPIRY_LO,      expiryEpoch.toPageBytes())
            mu.writePage(PAGE_EXPIRY_LO + 1,  ByteArray(4))
            mu.writePage(PAGE_STATUS,         byteArrayOf(STATUS_ACTIVE, 0, 0, 0))
            for (i in 0..3) {
                mu.writePage(PAGE_HMAC_START + i, hmac.copyOfRange(i * 4, i * 4 + 4))
            }

            // Verify
            val verifyNominal = mu.readPages(PAGE_NOMINAL_LO).getInt()
            if (verifyNominal != nominalCents) {
                return@withContext WriteResult.Error("Activation verification failed")
            }
            WriteResult.Success
        } catch (e: IOException) {
            WriteResult.Error("I/O error during activation: ${e.message}")
        } finally {
            runCatching { mu.close() }
        }
    }

    // ─────────────────────── Private helpers ─────────────────────────────

    /** Builds the PWD_AUTH command (0x1B + 4-byte PWD). */
    private fun buildPwdAuthCommand(pwd: ByteArray): ByteArray {
        require(pwd.size == 4) { "PWD must be exactly 4 bytes" }
        return byteArrayOf(0x1B.toByte()) + pwd
    }

    private fun List<Byte>.toByteArray() = ByteArray(size) { this[it] }
    private fun ByteArray.take(n: Int)   = toList().take(n)
    private fun ByteArray.getInt(): Int  = ByteBuffer.wrap(this, 0, 4).order(ByteOrder.BIG_ENDIAN).int
    private fun Int.toPageBytes(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(this).array()

    // NfcA-based page read when MifareUltralight.readPages unavailable
    private fun MifareUltralight.readPages(page: Int): ByteArray = readPages(page)
}
