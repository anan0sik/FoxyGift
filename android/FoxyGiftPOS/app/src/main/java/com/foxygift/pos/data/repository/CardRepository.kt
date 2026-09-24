package com.foxygift.pos.data.repository

import android.nfc.Tag
import com.foxygift.pos.core.nfc.DecimalUidConverter
import com.foxygift.pos.core.nfc.NtagDriver
import com.foxygift.pos.core.nfc.NtagDriver.FailReason
import com.foxygift.pos.core.security.TelegramAlarmClient
import com.foxygift.pos.data.db.CardDao
import com.foxygift.pos.data.db.TransactionDao
import com.foxygift.pos.data.db.entities.CardEntity
import com.foxygift.pos.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository for all gift card operations.
 *
 * Delegates NFC chip I/O to [NtagDriver] and Room persistence to [CardDao] / [TransactionDao].
 * All operations are fully offline — no cloud required.
 */
@Singleton
class CardRepository @Inject constructor(
    private val ntagDriver:     NtagDriver,
    private val cardDao:        CardDao,
    private val transactionDao: TransactionDao,
    private val provisionRepo:  ProvisionRepository,
    private val telegramClient: TelegramAlarmClient,
) {
    enum class ErrorCode {
        NONE,
        CLONE_DETECTED,
        WRONG_MERCHANT,
        INSUFFICIENT_BALANCE,
        EXPIRED,
        EXHAUSTED,
        AUTH_FAILED,
        NFC_ERROR,
    }

    sealed class OpResult {
        data class Success(
            val cardUid:      String,
            val balanceCents: Long,
            val message:      String,
        ) : OpResult()
        data class BusinessError(val reason: String, val code: ErrorCode = ErrorCode.NONE, val extra: String = "") : OpResult()
        data class NfcError(val reason: String, val code: ErrorCode = ErrorCode.NFC_ERROR)      : OpResult()
        object CloneDetected : OpResult()
        object WrongMerchant : OpResult()
        data class ExistingBalanceWarning(
            val cardNumberDec:       String,
            val currentBalanceCents: Long,
        ) : OpResult()
    }

    // ─────────────────────── Helpers ─────────────────────────────────────────

    private fun creds() = Triple(
        provisionRepo.getPwd(),
        provisionRepo.getPack(),
        provisionRepo.getMasterKeyHex(),
    )

    private fun LocalDate.toEpoch(): Int =
        atStartOfDay(ZoneOffset.UTC).toEpochSecond().toInt()

    private fun Int.toLocalDate(): LocalDate =
        Instant.ofEpochSecond(toLong()).atZone(ZoneOffset.UTC).toLocalDate()

    private fun String.hexToBytes(n: Int): ByteArray {
        val hex  = filter { it.isLetterOrDigit() }
        val even = if (hex.length % 2 != 0) "0$hex" else hex
        val raw  = ByteArray(even.length / 2) { i ->
            even.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return if (raw.size >= n) raw.copyOfRange(0, n) else raw + ByteArray(n - raw.size)
    }

    private fun nowIso(): String = Instant.now().toString()
    private fun LocalDate.toIso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
    private suspend fun nextReceipt() = transactionDao.nextReceiptNumber()
    private fun formatCents(cents: Long): String = "%.2f".format(java.util.Locale.US, cents / 100.0)

    // ─────────────────────── ISSUE / TOP-UP ──────────────────────────────────

    /**
     * Issues a new gift card or tops up / re-activates an existing one.
     *
     * Accepted card states:
     *  - Blank NTAG (no FOXY magic)            → first-time issue
     *  - PRE_INIT (Admin Tool, HMAC mismatch)  → first activation on terminal
     *  - ACTIVE / EXHAUSTED                    → top-up, new nominal & balance
     *
     * Blocked states:
     *  - AUTH_FAILED (unknown password)        → hard error
     *  - MERCHANT_HASH_MISMATCH                → wrong merchant network
     */
    suspend fun issueCard(
        tag:            Tag,
        nominalCents:   Long,
        validityMonths: Int,
        forceOverwrite: Boolean = false,
    ): OpResult = withContext(Dispatchers.IO) {
        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)
        val uid: String
        val baseCard: NtagDriver.CardData?

        when (readResult) {
            is NtagDriver.ReadResult.Error -> return@withContext OpResult.NfcError(readResult.message)
            is NtagDriver.ReadResult.Unauthorized -> {
                when (readResult.failReason) {
                    FailReason.MAGIC_MISMATCH -> {
                        // Totally blank card — UID from Android tag
                        uid      = DecimalUidConverter.toDecimalString(tag.id)
                        baseCard = null
                    }
                    FailReason.HMAC_INVALID -> {
                        // Card has FOXY magic + correct merchant, but HMAC doesn't match.
                        // This happens with cards pre-initialized via Admin Tool (test HMAC key).
                        // Safe to overwrite: issue writes a fresh HMAC with the merchant key.
                        uid      = readResult.cardNumberDec
                        baseCard = null
                    }
                    FailReason.MERCHANT_HASH_MISMATCH ->
                        return@withContext OpResult.WrongMerchant
                    FailReason.AUTH_FAILED ->
                        return@withContext OpResult.NfcError(
                            "Card authentication failed — card may be locked with an unknown password.",
                            ErrorCode.AUTH_FAILED,
                        )
                }
            }
            is NtagDriver.ReadResult.Success -> {
                uid      = readResult.cardData.cardNumberDec
                baseCard = readResult.cardData

                // Prevent accidental erasure of funds on reload
                if (!forceOverwrite && readResult.cardData.balanceCents > 0) {
                    return@withContext OpResult.ExistingBalanceWarning(
                        cardNumberDec       = uid,
                        currentBalanceCents = readResult.cardData.balanceCents.toLong(),
                    )
                }
            }
        }

        val expiryEpoch  = LocalDate.now().plusMonths(validityMonths.toLong()).toEpoch()
        val scaffoldData = baseCard ?: NtagDriver.CardData(
            cardNumberDec  = uid,
            uidBytes       = tag.id,
            merchantIdHash = merchantHashBytes,
            nominalCents   = nominalCents.toInt(),
            balanceCents   = 0,
            expiryEpoch    = expiryEpoch,
            status         = NtagDriver.STATUS_PRE_INIT,
            storedHmac     = ByteArray(16),
        )

        val writeResult = ntagDriver.writeCard(
            tag             = tag,
            pwd             = pwd,
            expectedPack    = pack,
            masterKeyHex    = masterKey,
            cardData        = scaffoldData.copy(
                merchantIdHash = merchantHashBytes,
                nominalCents   = nominalCents.toInt(),
            ),
            newBalanceCents = nominalCents.toInt(),
            newExpiryEpoch  = expiryEpoch,
            newStatus       = NtagDriver.STATUS_ACTIVE,
        )

        if (writeResult is NtagDriver.WriteResult.Error) {
            return@withContext OpResult.NfcError(writeResult.message)
        }

        val expiryDate    = expiryEpoch.toLocalDate()
        val locationName  = provisionRepo.getLocationName()
        val terminalId    = provisionRepo.getTerminalId()
        val currency      = provisionRepo.getCurrency()
        val receiptNumber = nextReceipt()
        val now           = nowIso()

        cardDao.upsertCard(CardEntity(
            cardNumberDec      = uid,
            nominalCents       = nominalCents.toInt(),
            balanceCents       = nominalCents.toInt(),
            activatedAt        = now,
            cardExpirationDate = expiryDate.toIso(),
            expiryEpoch        = expiryEpoch,
            status             = "ACTIVE",
            terminalId         = terminalId,
            locationName       = locationName,
        ))
        transactionDao.insertTransaction(TransactionEntity(
            timestamp          = now,
            terminalId         = terminalId,
            locationName       = locationName,
            shiftId            = shiftId(),
            receiptNumber      = receiptNumber,
            cardNumberDec      = uid,
            operationType      = "ISSUE",
            amountCents        = nominalCents.toInt(),
            balanceAfterCents  = nominalCents.toInt(),
            cardExpirationDate = expiryDate.toIso(),
            currency           = currency,
        ))

        runCatching {
            telegramClient.sendZReportSummary(
                terminalId   = terminalId,
                locationName = locationName,
                shiftDate    = LocalDate.now().toIso(),
                issued       = "${formatCents(nominalCents)} $currency",
                redeemed     = "—",
                txCount      = 1,
            )
        }

        OpResult.Success(
            cardUid      = uid,
            balanceCents = nominalCents,
            message      = "Card issued: ${formatCents(nominalCents)} $currency • Expires ${expiryDate.toIso()}",
        )
    }

    // ─────────────────────── REDEEM ──────────────────────────────────────────

    suspend fun redeemCard(
        tag:         Tag,
        redeemCents: Long,
    ): OpResult = withContext(Dispatchers.IO) {
        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)

        when (readResult) {
            is NtagDriver.ReadResult.Error        -> return@withContext OpResult.NfcError(readResult.message)
            is NtagDriver.ReadResult.Unauthorized -> return@withContext when (readResult.failReason) {
                FailReason.HMAC_INVALID           -> OpResult.CloneDetected
                FailReason.MERCHANT_HASH_MISMATCH -> OpResult.WrongMerchant
                else -> OpResult.NfcError("Auth failed: ${readResult.failReason}")
            }
            is NtagDriver.ReadResult.Success -> Unit
        }

        val cardData = (readResult as NtagDriver.ReadResult.Success).cardData
        val uid      = cardData.cardNumberDec
        val expiry   = cardData.expiryEpoch.toLocalDate()

        if (LocalDate.now().isAfter(expiry)) {
            return@withContext OpResult.BusinessError("Card expired on ${expiry.toIso()}.", ErrorCode.EXPIRED, expiry.toIso())
        }
        if (cardData.balanceCents < redeemCents.toInt()) {
            return@withContext OpResult.BusinessError(
                "Insufficient balance. Available: ${formatCents(cardData.balanceCents.toLong())} EUR, " +
                "Requested: ${formatCents(redeemCents)} EUR",
                ErrorCode.INSUFFICIENT_BALANCE,
                formatCents(cardData.balanceCents.toLong()),
            )
        }
        if (cardData.status == NtagDriver.STATUS_EXHAUSTED) {
            return@withContext OpResult.BusinessError("Card balance is fully exhausted.", ErrorCode.EXHAUSTED)
        }

        val newBalance = cardData.balanceCents - redeemCents.toInt()
        val newStatus  = if (newBalance == 0) NtagDriver.STATUS_EXHAUSTED else NtagDriver.STATUS_ACTIVE

        val writeResult = ntagDriver.writeCard(
            tag             = tag,
            pwd             = pwd,
            expectedPack    = pack,
            masterKeyHex    = masterKey,
            cardData        = cardData,
            newBalanceCents = newBalance,
            newExpiryEpoch  = cardData.expiryEpoch,
            newStatus       = newStatus,
        )

        if (writeResult is NtagDriver.WriteResult.Error) {
            return@withContext OpResult.NfcError(writeResult.message)
        }

        val locationName  = provisionRepo.getLocationName()
        val terminalId    = provisionRepo.getTerminalId()
        val currency      = provisionRepo.getCurrency()
        val receiptNumber = nextReceipt()
        val now           = nowIso()
        val statusStr     = if (newBalance == 0) "EXHAUSTED" else "ACTIVE"

        cardDao.upsertCard(CardEntity(
            cardNumberDec      = uid,
            nominalCents       = cardData.nominalCents,
            balanceCents       = newBalance,
            activatedAt        = null,
            cardExpirationDate = expiry.toIso(),
            expiryEpoch        = cardData.expiryEpoch,
            status             = statusStr,
            terminalId         = terminalId,
            locationName       = locationName,
        ))
        transactionDao.insertTransaction(TransactionEntity(
            timestamp          = now,
            terminalId         = terminalId,
            locationName       = locationName,
            shiftId            = shiftId(),
            receiptNumber      = receiptNumber,
            cardNumberDec      = uid,
            operationType      = if (newBalance == 0) "REDEEM_FULL" else "REDEEM",
            amountCents        = redeemCents.toInt(),
            balanceAfterCents  = newBalance,
            cardExpirationDate = expiry.toIso(),
            currency           = currency,
        ))

        OpResult.Success(
            cardUid      = uid,
            balanceCents = newBalance.toLong(),
            message      = "Redeemed ${formatCents(redeemCents)} $currency • Balance: ${formatCents(newBalance.toLong())} $currency",
        )
    }

    // ─────────────────────── PROLONG ─────────────────────────────────────────

    suspend fun prolongCard(
        tag:           Tag,
        prolongMonths: Int,
    ): OpResult = withContext(Dispatchers.IO) {
        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)

        when (readResult) {
            is NtagDriver.ReadResult.Error        -> return@withContext OpResult.NfcError(readResult.message)
            is NtagDriver.ReadResult.Unauthorized -> return@withContext when (readResult.failReason) {
                FailReason.HMAC_INVALID           -> OpResult.CloneDetected
                FailReason.MERCHANT_HASH_MISMATCH -> OpResult.WrongMerchant
                else -> OpResult.NfcError("Auth failed: ${readResult.failReason}")
            }
            is NtagDriver.ReadResult.Success -> Unit
        }

        val cardData      = (readResult as NtagDriver.ReadResult.Success).cardData
        val uid           = cardData.cardNumberDec
        val currentExpiry = cardData.expiryEpoch.toLocalDate()
        val base          = if (currentExpiry.isAfter(LocalDate.now())) currentExpiry else LocalDate.now()
        val newExpiry     = base.plusMonths(prolongMonths.toLong())
        val newEpoch      = newExpiry.toEpoch()

        val writeResult = ntagDriver.writeCard(
            tag             = tag,
            pwd             = pwd,
            expectedPack    = pack,
            masterKeyHex    = masterKey,
            cardData        = cardData,
            newBalanceCents = cardData.balanceCents,
            newExpiryEpoch  = newEpoch,
            newStatus       = NtagDriver.STATUS_PROLONGED,
        )

        if (writeResult is NtagDriver.WriteResult.Error) {
            return@withContext OpResult.NfcError(writeResult.message)
        }

        val locationName  = provisionRepo.getLocationName()
        val terminalId    = provisionRepo.getTerminalId()
        val currency      = provisionRepo.getCurrency()
        val receiptNumber = nextReceipt()
        val now           = nowIso()

        cardDao.upsertCard(CardEntity(
            cardNumberDec      = uid,
            nominalCents       = cardData.nominalCents,
            balanceCents       = cardData.balanceCents,
            activatedAt        = null,
            cardExpirationDate = newExpiry.toIso(),
            expiryEpoch        = newEpoch,
            status             = "PROLONGED",
            terminalId         = terminalId,
            locationName       = locationName,
            isProlonged        = true,
        ))
        transactionDao.insertTransaction(TransactionEntity(
            timestamp          = now,
            terminalId         = terminalId,
            locationName       = locationName,
            shiftId            = shiftId(),
            receiptNumber      = receiptNumber,
            cardNumberDec      = uid,
            operationType      = "PROLONG",
            amountCents        = 0,
            balanceAfterCents  = cardData.balanceCents,
            cardExpirationDate = newExpiry.toIso(),
            currency           = currency,
        ))

        OpResult.Success(
            cardUid      = uid,
            balanceCents = cardData.balanceCents.toLong(),
            message      = "Card extended. New expiry: ${newExpiry.toIso()}",
        )
    }

    // ─────────────────────── BALANCE ENQUIRY ─────────────────────────────────

    suspend fun readBalance(tag: Tag): OpResult = withContext(Dispatchers.IO) {
        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)

        when (readResult) {
            is NtagDriver.ReadResult.Error        -> return@withContext OpResult.NfcError(readResult.message)
            is NtagDriver.ReadResult.Unauthorized -> return@withContext when (readResult.failReason) {
                FailReason.HMAC_INVALID           -> OpResult.CloneDetected
                FailReason.MERCHANT_HASH_MISMATCH -> OpResult.WrongMerchant
                else -> OpResult.NfcError("Auth failed: ${readResult.failReason}")
            }
            is NtagDriver.ReadResult.Success -> Unit
        }

        val cardData  = (readResult as NtagDriver.ReadResult.Success).cardData
        val expiry    = cardData.expiryEpoch.toLocalDate()
        val isExpired = LocalDate.now().isAfter(expiry)

        OpResult.Success(
            cardUid      = cardData.cardNumberDec,
            balanceCents = cardData.balanceCents.toLong(),
            message      = buildString {
                append("Balance: ${formatCents(cardData.balanceCents.toLong())} EUR")
                append(" • Expires: ${expiry.toIso()}")
                if (isExpired) append(" ⚠ EXPIRED")
            },
        )
    }

    // ─────────────────────── VOID / ANNUL ───────────────────────────────────

    suspend fun voidCard(tag: Tag): OpResult = withContext(Dispatchers.IO) {
        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)

        when (readResult) {
            is NtagDriver.ReadResult.Error        -> return@withContext OpResult.NfcError(readResult.message)
            is NtagDriver.ReadResult.Unauthorized -> return@withContext when (readResult.failReason) {
                FailReason.HMAC_INVALID           -> OpResult.CloneDetected
                FailReason.MERCHANT_HASH_MISMATCH -> OpResult.WrongMerchant
                else -> OpResult.NfcError("Auth failed: ${readResult.failReason}")
            }
            is NtagDriver.ReadResult.Success -> Unit
        }

        val cardData = (readResult as NtagDriver.ReadResult.Success).cardData
        val uid      = cardData.cardNumberDec
        val expiry   = cardData.expiryEpoch.toLocalDate()
        val prevBalance = cardData.balanceCents

        val writeResult = ntagDriver.writeCard(
            tag             = tag,
            pwd             = pwd,
            expectedPack    = pack,
            masterKeyHex    = masterKey,
            cardData        = cardData,
            newBalanceCents = 0,
            newExpiryEpoch  = cardData.expiryEpoch,
            newStatus       = NtagDriver.STATUS_EXHAUSTED,
        )

        if (writeResult is NtagDriver.WriteResult.Error) {
            return@withContext OpResult.NfcError(writeResult.message)
        }

        val locationName  = provisionRepo.getLocationName()
        val terminalId    = provisionRepo.getTerminalId()
        val currency      = provisionRepo.getCurrency()
        val receiptNumber = nextReceipt()
        val now           = nowIso()

        cardDao.upsertCard(CardEntity(
            cardNumberDec      = uid,
            nominalCents       = cardData.nominalCents,
            balanceCents       = 0,
            activatedAt        = null,
            cardExpirationDate = expiry.toIso(),
            expiryEpoch        = cardData.expiryEpoch,
            status             = "VOID",
            terminalId         = terminalId,
            locationName       = locationName,
        ))

        transactionDao.insertTransaction(TransactionEntity(
            timestamp          = now,
            terminalId         = terminalId,
            locationName       = locationName,
            shiftId            = shiftId(),
            receiptNumber      = receiptNumber,
            cardNumberDec      = uid,
            operationType      = "VOID",
            amountCents        = prevBalance,
            balanceAfterCents  = 0,
            cardExpirationDate = expiry.toIso(),
            currency           = currency,
            status             = "SUCCESS",
        ))

        OpResult.Success(
            cardUid      = uid,
            balanceCents = 0L,
            message      = "Card $uid voided / cancelled. Balance: 0.00 $currency",
        )
    }

    // ─────────────────────── SPECIFIC TRANSACTION CANCELLATION ───────────────

    /**
     * Cancels a specific transaction following strict business rules:
     *  1. Same day check: Transaction shiftId must match today's shiftId.
     *  2. Not already voided check.
     *  3. Card scan & UID match check: Scanned card UID must match targetTx.cardNumberDec.
     *  4. Top-up / Issue cancellation: Deduct top-up amount from card balance. Requires card.balanceCents >= tx.amountCents.
     *  5. Payment / Redeem cancellation: Refund payment amount back to card balance.
     *  6. Write updated balance to NFC card.
     *  7. Update Room database (mark targetTx status = "VOID", upsert CardEntity, insert new VOID TransactionEntity).
     */
    suspend fun cancelTransaction(
        tag: Tag,
        targetTx: TransactionEntity,
    ): OpResult = withContext(Dispatchers.IO) {
        val todayShift = shiftId()

        // 1. Same Day Check
        if (targetTx.shiftId != todayShift && !targetTx.timestamp.startsWith(todayShift)) {
            return@withContext OpResult.BusinessError(
                "Сделка проведена не сегодня. Аннулирование невозможно."
            )
        }

        // 2. Already Voided Check
        if (targetTx.status == "VOID" || targetTx.operationType == "VOID") {
            return@withContext OpResult.BusinessError(
                "Сделка уже аннулирована. Аннулирование невозможно."
            )
        }

        val (pwd, pack, masterKey) = creds()
        val merchantHashBytes = provisionRepo.getMerchantIdHash().hexToBytes(4)

        // 3. Read Card
        val readResult = ntagDriver.readCard(tag, pwd, pack, masterKey, merchantHashBytes)

        val cardData = when (readResult) {
            is NtagDriver.ReadResult.Error -> return@withContext OpResult.NfcError(
                "Ошибка чтения карты. Аннулирование невозможно."
            )
            is NtagDriver.ReadResult.Unauthorized -> return@withContext when (readResult.failReason) {
                FailReason.HMAC_INVALID -> OpResult.BusinessError(
                    "Подпись карты недействительна. Аннулирование невозможно."
                )
                FailReason.MERCHANT_HASH_MISMATCH -> OpResult.BusinessError(
                    "Карта другого мерчанта. Аннулирование невозможно."
                )
                else -> OpResult.NfcError("Ошибка авторизации карты. Аннулирование невозможно.")
            }
            is NtagDriver.ReadResult.Success -> readResult.cardData
        }

        val scannedUid = cardData.cardNumberDec

        // 4. Card UID Match Check
        if (scannedUid != targetTx.cardNumberDec) {
            return@withContext OpResult.BusinessError(
                "Считанная карта не совпадает со сделкой. Аннулирование невозможно."
            )
        }

        // 5. Calculate New Balance based on Operation Type
        val newBalance: Int
        val newStatus: Byte
        val isIssueTopUp = targetTx.operationType == "ISSUE"
        val isRedeem = targetTx.operationType.startsWith("REDEEM")

        if (isIssueTopUp) {
            // Cancelling top-up / issue -> deduct top-up amount from card balance
            if (cardData.balanceCents < targetTx.amountCents) {
                return@withContext OpResult.BusinessError(
                    "На карте не хватает средств для аннулирования. Аннулирование невозможно."
                )
            }
            newBalance = cardData.balanceCents - targetTx.amountCents
            newStatus = if (newBalance == 0) NtagDriver.STATUS_EXHAUSTED else NtagDriver.STATUS_ACTIVE
        } else if (isRedeem) {
            // Cancelling payment -> refund amount back to card balance
            newBalance = cardData.balanceCents + targetTx.amountCents
            newStatus = NtagDriver.STATUS_ACTIVE
        } else {
            // Prolong or other operation -> balance remains same
            newBalance = cardData.balanceCents
            newStatus = cardData.status
        }

        // 6. Write New Balance to NFC Chip
        val writeResult = ntagDriver.writeCard(
            tag             = tag,
            pwd             = pwd,
            expectedPack    = pack,
            masterKeyHex    = masterKey,
            cardData        = cardData,
            newBalanceCents = newBalance,
            newExpiryEpoch  = cardData.expiryEpoch,
            newStatus       = newStatus,
        )

        if (writeResult is NtagDriver.WriteResult.Error) {
            return@withContext OpResult.NfcError("Ошибка записи карты. Аннулирование невозможно.")
        }

        // 7. Update Room Database
        val locationName  = provisionRepo.getLocationName()
        val terminalId    = provisionRepo.getTerminalId()
        val currency      = provisionRepo.getCurrency()
        val receiptNumber = nextReceipt()
        val now           = nowIso()
        val expiry        = cardData.expiryEpoch.toLocalDate()

        // Mark original transaction as VOID
        transactionDao.updateTransactionStatus(targetTx.id, "VOID")

        // Update card record in DB
        val cardStatusStr = if (newBalance == 0) "EXHAUSTED" else "ACTIVE"
        cardDao.upsertCard(CardEntity(
            cardNumberDec      = scannedUid,
            nominalCents       = cardData.nominalCents,
            balanceCents       = newBalance,
            activatedAt        = null,
            cardExpirationDate = expiry.toIso(),
            expiryEpoch        = cardData.expiryEpoch,
            status             = cardStatusStr,
            terminalId         = terminalId,
            locationName       = locationName,
        ))

        // Insert new VOID transaction entry
        transactionDao.insertTransaction(TransactionEntity(
            timestamp          = now,
            terminalId         = terminalId,
            locationName       = locationName,
            shiftId            = todayShift,
            receiptNumber      = receiptNumber,
            cardNumberDec      = scannedUid,
            operationType      = "VOID",
            amountCents        = targetTx.amountCents,
            balanceAfterCents  = newBalance,
            cardExpirationDate = expiry.toIso(),
            currency           = currency,
            status             = "SUCCESS",
        ))

        OpResult.Success(
            cardUid      = scannedUid,
            balanceCents = newBalance.toLong(),
            message      = "Сделка #${targetTx.receiptNumber} успешно аннулирована.",
        )
    }

    // ─────────────────────── Private ─────────────────────────────────────────

    /** Shift ID from provision repository, supporting multiple shifts per day. */
    private fun shiftId(): String = provisionRepo.getCurrentShiftId()
}
