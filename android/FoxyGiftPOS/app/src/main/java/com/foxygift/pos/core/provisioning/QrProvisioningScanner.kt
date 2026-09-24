package com.foxygift.pos.core.provisioning

import android.util.Base64
import com.foxygift.pos.core.security.TelegramAlarmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and decrypts the FoxyGift Terminal Provisioning QR payload.
 *
 * QR JSON format (type = "FOXYGIFT_PROVISION_V2"):
 * {
 *   "t":   "FOXYGIFT_PROVISION_V2",
 *   "iv":  "<12-byte IV hex>",
 *   "ct":  "<AES-256-GCM ciphertext hex>",
 *   "kid": "<key ID string>",
 *   "mid": <merchant ID integer>
 * }
 *
 * The decryption key must be supplied by the operator (entered once
 * by admin and stored in EncryptedSharedPreferences — never the QR itself).
 * Alternatively, the QR can be self-contained with a KDF derivation from a
 * vendor-known password for offline environments.
 */
@Singleton
class QrProvisioningScanner @Inject constructor(
    private val telegramAlarmClient: TelegramAlarmClient,
) {
    companion object {
        const val VENDOR_PROVISIONING_KEY_HEX = "466f78794769667456656e646f7250726f766973696f6e323032364b65792121"
    }

    sealed class ScanResult {
        data class Success(val config: ProvisioningConfig) : ScanResult()
        data class Invalid(val reason: String)             : ScanResult()
        object NotAProvisioningQr                          : ScanResult()
    }

    data class ProvisioningConfig(
        val version:        Int,
        val merchantId:     Int,
        val networkName:    String,
        val locationName:   String,
        val terminalId:     String,
        val merchantIdHash: String,
        val masterKeyId:    String,
        val masterKeyHex:   String,
        val currency:       String,
        val language:       String,
        val validityMonths: Int,
        val prolongMonths:  Int,
        val tgToken:        String,
        val tgChatId:       String,
        val adminHash:      String,
        val cashierHash:    String,
        val prolongHash:    String,
        val saltHex:        String,
        // Derived from masterKeyId + terminal ID
        val pwd:            ByteArray,
        val pack:           ByteArray,
    )

    /**
     * Parses a raw QR string. Decrypts it using the built-in offline vendor
     * provisioning key or [masterKeyHex] and returns a [ProvisioningConfig].
     *
     * @param rawQrText  The raw string decoded from the QR code.
     * @param masterKeyHex  Optional custom master key (if pre-configured on terminal).
     */
    suspend fun parse(rawQrText: String, masterKeyHex: String = ""): ScanResult =
        withContext(Dispatchers.Default) {
            runCatching {
                val json = JSONObject(rawQrText)
                val type = json.optString("t")
                if (type != "FOXYGIFT_PROVISION_V2") return@withContext ScanResult.NotAProvisioningQr

                val isBase64 = json.optString("enc") == "b64"
                val ivRaw  = json.getString("iv")
                val ctRaw  = json.getString("ct")
                val keyId  = json.getString("kid")
                val mid    = json.getInt("mid")

                val iv = if (isBase64) Base64.decode(ivRaw, Base64.DEFAULT) else ivRaw.hexToBytes()
                val ct = if (isBase64) Base64.decode(ctRaw, Base64.DEFAULT) else ctRaw.hexToBytes()

                // Try decrypting with Vendor key first, then custom master key if provided
                val candidateKeys = listOfNotNull(
                    VENDOR_PROVISIONING_KEY_HEX,
                    masterKeyHex.takeIf { it.isNotBlank() && it.length == 64 }
                ).distinct()

                var decryptedBytes: ByteArray? = null
                var lastError: Throwable? = null

                for (candidate in candidateKeys) {
                    try {
                        val keyBytes = candidate.hexToBytes()
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                        decryptedBytes = cipher.doFinal(ct)
                        break
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }

                if (decryptedBytes == null) {
                    throw lastError ?: IllegalStateException("Decryption failed")
                }

                val payload = JSONObject(String(decryptedBytes, Charsets.UTF_8))

                // The merchant master key is embedded in payload ("mk"), or fallback to masterKeyHex
                val effectiveMasterKey = payload.optString("mk").ifBlank {
                    payload.optString("masterKeyHex").ifBlank { masterKeyHex }
                }

                // Derive PWD/PACK from master key + terminal ID deterministically
                val terminalId = payload.optString("tid").ifBlank { payload.getString("terminalId") }
                val (pwd, pack) = derivePwdPack(effectiveMasterKey, mid.toString(), terminalId)

                val config = ProvisioningConfig(
                    version        = payload.optInt("v", 2),
                    merchantId     = mid,
                    networkName    = payload.optString("net").ifBlank { payload.getString("networkName") },
                    locationName   = payload.optString("loc").ifBlank { payload.getString("locationName") },
                    terminalId     = terminalId,
                    merchantIdHash = payload.optString("mh").ifBlank { payload.getString("merchantIdHash") },
                    masterKeyId    = keyId,
                    masterKeyHex   = effectiveMasterKey,
                    currency       = payload.optString("cur").ifBlank { payload.optString("currency", "EUR") },
                    language       = payload.optString("lng").ifBlank { payload.optString("language", "en") },
                    validityMonths = if (payload.has("val")) payload.optInt("val", 6) else payload.optInt("validity", 6),
                    prolongMonths  = if (payload.has("pro")) payload.optInt("pro", 3) else payload.optInt("prolongation", 3),
                    tgToken        = payload.optString("tok").ifBlank { payload.optString("tgToken", "") },
                    tgChatId       = payload.optString("cid").ifBlank { payload.optString("tgChatId", "") },
                    adminHash      = payload.optString("ah").ifBlank { payload.getString("adminHash") },
                    cashierHash    = payload.optString("ch").ifBlank { payload.getString("cashierHash") },
                    prolongHash    = payload.optString("ph").ifBlank { payload.getString("prolongHash") },
                    saltHex        = payload.optString("s").ifBlank { payload.getString("saltHex") },
                    pwd            = pwd,
                    pack           = pack,
                )

                // Configure Telegram client immediately
                if (config.tgToken.isNotBlank()) {
                    telegramAlarmClient.configure(config.tgToken, config.tgChatId)
                }

                ScanResult.Success(config)
            }.getOrElse { e ->
                ScanResult.Invalid("Decryption failed: ${e.message}")
            }
        }

    /**
     * Derives the 4-byte PWD and 2-byte PACK for NTAG chip authentication
     * from the master key, merchant ID, and terminal ID using HMAC-SHA256.
     *
     * This is deterministic — the same inputs always produce the same PWD/PACK.
     */
    private fun derivePwdPack(masterKeyHex: String, merchantId: String, terminalId: String): Pair<ByteArray, ByteArray> {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKeyHex.hexToBytes(), "HmacSHA256"))
        val derived = mac.doFinal("PWD:$merchantId:$terminalId".toByteArray())
        val pwd  = derived.copyOfRange(0, 4)
        val pack = derived.copyOfRange(4, 6)
        return Pair(pwd, pack)
    }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
