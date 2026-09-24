package com.foxygift.pos.core.printer

import com.foxygift.pos.core.nfc.DecimalUidConverter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds structured ESC/POS receipt line lists for each transaction type.
 * All card numbers are formatted in decimal — HEX is never printed.
 */
@Singleton
class ReceiptBuilder @Inject constructor(private val balticRenderer: BalticCharsetRenderer) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    companion object {
        const val HEADER = "[FOXYGIFT TERMINAL]"
    }

    /** Builds a card issuance receipt. */
    fun buildIssueReceipt(
        receiptNumber:  Int,
        cardNumberDec:  String,
        nominalCents:   Int,
        expiryEpoch:    Int,
        terminalId:     String,
        locationName:   String,
        currency:       String,
        cashierName:    String,
    ): List<ReceiptLine> = buildList {
        addHeader(locationName, terminalId)
        add(ReceiptLine.TextLine("GIFT CARD ISSUED", align = Align.CENTER, bold = true, double = true))
        add(ReceiptLine.Divider)
        addCardNumber(cardNumberDec)
        addField("Nominal:", formatAmount(nominalCents, currency))
        addField("Balance:", formatAmount(nominalCents, currency))
        addField("Expires:", formatEpoch(expiryEpoch))
        addField("Status:", "ACTIVE")
        add(ReceiptLine.Divider)
        addFooter(receiptNumber, cashierName)
        add(ReceiptLine.TextLine("Thank you for your purchase!", align = Align.CENTER))
        add(ReceiptLine.Feed(3))
    }

    /** Builds a card redemption receipt. */
    fun buildRedeemReceipt(
        receiptNumber:  Int,
        cardNumberDec:  String,
        amountCents:    Int,
        balanceBefore:  Int,
        balanceAfter:   Int,
        expiryEpoch:    Int,
        terminalId:     String,
        locationName:   String,
        currency:       String,
        cashierName:    String,
    ): List<ReceiptLine> = buildList {
        addHeader(locationName, terminalId)
        add(ReceiptLine.TextLine("GIFT CARD PAYMENT", align = Align.CENTER, bold = true, double = true))
        add(ReceiptLine.Divider)
        addCardNumber(cardNumberDec)
        addField("Charged:",  formatAmount(amountCents, currency))
        addField("Previous:", formatAmount(balanceBefore, currency))
        addField("Remaining:", formatAmount(balanceAfter, currency))
        addField("Expires:", formatEpoch(expiryEpoch))
        if (balanceAfter <= 0) {
            add(ReceiptLine.Feed(1))
            add(ReceiptLine.TextLine("*** CARD EXHAUSTED ***", align = Align.CENTER, bold = true))
        }
        add(ReceiptLine.Divider)
        addFooter(receiptNumber, cashierName)
        add(ReceiptLine.Feed(3))
    }

    /** Builds a card prolongation receipt. */
    fun buildProlongReceipt(
        receiptNumber:  Int,
        cardNumberDec:  String,
        balanceCents:   Int,
        oldExpiryEpoch: Int,
        newExpiryEpoch: Int,
        terminalId:     String,
        locationName:   String,
        currency:       String,
        cashierName:    String,
    ): List<ReceiptLine> = buildList {
        addHeader(locationName, terminalId)
        add(ReceiptLine.TextLine("FOXYGIFT: CARD PROLONGED", align = Align.CENTER, bold = true))
        add(ReceiptLine.Divider)
        addCardNumber(cardNumberDec)
        addField("Balance:", formatAmount(balanceCents, currency))
        addField("Was valid:", formatEpoch(oldExpiryEpoch))
        addField("New expiry:", formatEpoch(newExpiryEpoch))
        add(ReceiptLine.Divider)
        addFooter(receiptNumber, cashierName)
        add(ReceiptLine.Feed(3))
    }

    /** Builds a void/refund receipt. */
    fun buildVoidReceipt(
        receiptNumber:  Int,
        originalReceipt:Int,
        cardNumberDec:  String,
        refundCents:    Int,
        balanceAfter:   Int,
        terminalId:     String,
        locationName:   String,
        currency:       String,
        adminName:      String,
    ): List<ReceiptLine> = buildList {
        addHeader(locationName, terminalId)
        add(ReceiptLine.TextLine("VOID / REFUND", align = Align.CENTER, bold = true, double = true))
        add(ReceiptLine.Divider)
        addCardNumber(cardNumberDec)
        addField("Refunded:", formatAmount(refundCents, currency))
        addField("New balance:", formatAmount(balanceAfter, currency))
        addField("Orig. receipt:", "#${originalReceipt.toString().padStart(6,'0')}")
        add(ReceiptLine.Divider)
        add(ReceiptLine.TextLine("Authorized by Admin", align = Align.CENTER))
        addFooter(receiptNumber, adminName)
        add(ReceiptLine.Feed(3))
    }

    // ─────────────────────── Private helpers ─────────────────────────────

    private fun MutableList<ReceiptLine>.addHeader(locationName: String, terminalId: String) {
        add(ReceiptLine.Feed(1))
        add(ReceiptLine.TextLine(HEADER, align = Align.CENTER, bold = true))
        // Location may have Baltic chars — render via Bitmap
        add(ReceiptLine.BalticText("  $locationName", 13f))
        add(ReceiptLine.TextLine("Terminal: $terminalId", align = Align.CENTER))
        add(ReceiptLine.TextLine(formatNow(), align = Align.CENTER))
        add(ReceiptLine.Divider)
    }

    private fun MutableList<ReceiptLine>.addCardNumber(cardNumberDec: String) {
        val display = DecimalUidConverter.formatForDisplay(cardNumberDec)
        add(ReceiptLine.TextLine("Card No:", bold = true))
        add(ReceiptLine.TextLine("  $display", bold = true))
        add(ReceiptLine.TextLine("  (DEC ONLY - NO HEX)"))
        add(ReceiptLine.Feed(1))
    }

    private fun MutableList<ReceiptLine>.addField(label: String, value: String) {
        val line = label.padEnd(14) + value
        add(ReceiptLine.TextLine(line))
    }

    private fun MutableList<ReceiptLine>.addFooter(receiptNumber: Int, operatorName: String) {
        add(ReceiptLine.TextLine("Receipt: #${receiptNumber.toString().padStart(6,'0')}"))
        add(ReceiptLine.TextLine("Operator: $operatorName"))
        add(ReceiptLine.Feed(1))
    }

    private fun formatAmount(cents: Int, currency: String): String =
        "${cents / 100}.${(cents % 100).toString().padStart(2,'0')} $currency"

    private fun formatEpoch(epoch: Int): String =
        DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(epoch.toLong()))

    private fun formatNow(): String =
        dateFormatter.format(Instant.now())
}
