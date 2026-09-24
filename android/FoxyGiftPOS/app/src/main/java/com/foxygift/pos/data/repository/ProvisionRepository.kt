package com.foxygift.pos.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.foxygift.pos.core.provisioning.QrProvisioningScanner.ProvisioningConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and retrieves provisioning configuration using EncryptedSharedPreferences.
 *
 * All sensitive fields (master key, PWD, PACK, PIN hashes, Telegram token)
 * are stored with AES-256-GCM encryption — never as plaintext.
 */
@Singleton
class ProvisionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "foxygift_provision"

        // Keys
        const val KEY_MASTER_KEY_HEX   = "master_key_hex"
        const val KEY_NETWORK_NAME     = "network_name"
        const val KEY_LOCATION_NAME    = "location_name"
        const val KEY_TERMINAL_ID      = "terminal_id"
        const val KEY_MERCHANT_ID      = "merchant_id"
        const val KEY_MERCHANT_ID_HASH = "merchant_id_hash"
        const val KEY_CURRENCY         = "currency"
        const val KEY_LANGUAGE         = "language"
        const val KEY_VALIDITY_MONTHS  = "validity_months"
        const val KEY_PROLONG_MONTHS   = "prolong_months"
        const val KEY_TG_TOKEN         = "tg_token"
        const val KEY_TG_CHAT_ID       = "tg_chat_id"
        const val KEY_ADMIN_HASH       = "admin_hash"
        const val KEY_CASHIER_HASH     = "cashier_hash"
        const val KEY_PROLONG_HASH     = "prolong_hash"
        const val KEY_SALT_HEX         = "salt_hex"
        const val KEY_PWD_HEX          = "pwd_hex"
        const val KEY_PACK_HEX         = "pack_hex"
        const val KEY_IS_PROVISIONED   = "is_provisioned"
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

    fun isProvisioned(): Boolean = prefs.getBoolean(KEY_IS_PROVISIONED, false)

    /**
     * Returns the stored master key hex string.
     * This is the 64-char hex AES-256 master secret set by the vendor
     * before shipping the terminal (e.g., via a factory provisioning step).
     *
     * Returns empty string if not yet set — the QR scanner will return Invalid
     * for any QR scanned in this state.
     */
    fun getMasterKeyHex(): String = prefs.getString(KEY_MASTER_KEY_HEX, "") ?: ""

    /**
     * Stores the full provisioning config from the decrypted QR payload.
     * Called exactly once per provisioning QR scan.
     */
    fun saveProvisioningConfig(config: ProvisioningConfig) {
        prefs.edit()
            .putString(KEY_MASTER_KEY_HEX,   config.masterKeyHex)
            .putString(KEY_NETWORK_NAME,     config.networkName)
            .putString(KEY_LOCATION_NAME,    config.locationName)
            .putString(KEY_TERMINAL_ID,      config.terminalId)
            .putInt(   "merchant_id_int",    config.merchantId)
            .putString(KEY_MERCHANT_ID_HASH, config.merchantIdHash)
            .putString(KEY_CURRENCY,         config.currency)
            .putString(KEY_LANGUAGE,         config.language)
            .putInt(   KEY_VALIDITY_MONTHS,  config.validityMonths)
            .putInt(   KEY_PROLONG_MONTHS,   config.prolongMonths)
            .putString(KEY_TG_TOKEN,         config.tgToken)
            .putString(KEY_TG_CHAT_ID,       config.tgChatId)
            .putString(KEY_ADMIN_HASH,       config.adminHash)
            .putString(KEY_CASHIER_HASH,     config.cashierHash)
            .putString(KEY_PROLONG_HASH,     config.prolongHash)
            .putString(KEY_SALT_HEX,         config.saltHex)
            .putString(KEY_PWD_HEX,          config.pwd.toHex())
            .putString(KEY_PACK_HEX,         config.pack.toHex())
            .putBoolean(KEY_IS_PROVISIONED,  true)
            .apply()
    }

    /**
     * Saves the vendor-programmed master key. This is called once during
     * device factory setup, not during field provisioning.
     *
     * In production, this would be pre-installed by the vendor.
     * For development, this can be set via the Settings screen.
     */
    fun saveMasterKeyHex(hex: String) {
        prefs.edit().putString(KEY_MASTER_KEY_HEX, hex).apply()
    }

    fun getLocationName(): String  = prefs.getString(KEY_LOCATION_NAME, "")  ?: ""
    fun getNetworkName():  String  = prefs.getString(KEY_NETWORK_NAME,  "")  ?: ""
    fun getTerminalId():   String  = prefs.getString(KEY_TERMINAL_ID,   "")  ?: ""
    fun getCurrency():     String  = prefs.getString(KEY_CURRENCY,      "EUR") ?: "EUR"
    fun getCashierHash():  String  = prefs.getString(KEY_CASHIER_HASH,  "")  ?: ""
    fun getAdminHash():    String  = prefs.getString(KEY_ADMIN_HASH,    "")  ?: ""
    fun getProlongHash():  String  = prefs.getString(KEY_PROLONG_HASH,  "")  ?: ""
    fun getSaltHex():      String  = prefs.getString(KEY_SALT_HEX,      "")  ?: ""
    fun getMerchantIdHash(): String = prefs.getString(KEY_MERCHANT_ID_HASH, "") ?: ""

    fun getPwd(): ByteArray  = prefs.getString(KEY_PWD_HEX, "")?.hexToBytes()  ?: ByteArray(4)
    fun getPack(): ByteArray = prefs.getString(KEY_PACK_HEX, "")?.hexToBytes() ?: ByteArray(2)

    fun getValidityMonths(): Int = prefs.getInt(KEY_VALIDITY_MONTHS, 6)
    fun getProlongMonths():  Int = prefs.getInt(KEY_PROLONG_MONTHS, 3)

    fun getTelegramToken(): String  = prefs.getString(KEY_TG_TOKEN, "") ?: ""
    fun getTelegramChatId(): String = prefs.getString(KEY_TG_CHAT_ID, "") ?: ""

    fun saveTelegramConfig(token: String, chatId: String) {
        prefs.edit()
            .putString(KEY_TG_TOKEN, token.trim())
            .putString(KEY_TG_CHAT_ID, chatId.trim())
            .apply()
    }

    fun isShiftClosedToday(todayIso: String): Boolean =
        prefs.getString("shift_closed_date", "") == todayIso

    fun markShiftClosedToday(todayIso: String) {
        prefs.edit().putString("shift_closed_date", todayIso).apply()
    }

    /** Full reset — used when re-provisioning the terminal. */
    fun clearProvisioning() {
        prefs.edit().clear().apply()
    }

    // ─────────────────────── Helpers ─────────────────────────────────────────
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        if (length % 2 != 0) return ByteArray(0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
