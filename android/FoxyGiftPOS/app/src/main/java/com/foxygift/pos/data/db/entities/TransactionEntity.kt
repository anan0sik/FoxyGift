package com.foxygift.pos.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single financial transaction in the local Room database.
 *
 * All card references use card_number_dec (decimal string).
 * HEX UID is NEVER stored.
 *
 * receipt_number is a globally incrementing sequential counter (000001, 000002...).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("card_number_dec"),
        Index("operation_type"),
        Index("timestamp"),
        Index("receipt_number"),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: String,                  // ISO-8601 e.g. "2024-01-15T14:23:45Z"

    @ColumnInfo(name = "terminal_id")
    val terminalId: String,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "shift_id")
    val shiftId: String,

    @ColumnInfo(name = "receipt_number")
    val receiptNumber: Int,                 // Sequential, unalterable: 1, 2, 3…

    @ColumnInfo(name = "card_number_dec")
    val cardNumberDec: String,              // Decimal string — NEVER HEX

    @ColumnInfo(name = "operation_type")
    val operationType: String,              // ISSUE | REDEEM | VOID | PROLONG

    @ColumnInfo(name = "amount_cents")
    val amountCents: Int,                   // Transaction amount in cents

    @ColumnInfo(name = "balance_after_cents")
    val balanceAfterCents: Int,             // Card balance after operation

    @ColumnInfo(name = "card_expiration_date")
    val cardExpirationDate: String?,        // ISO-8601 date at time of transaction

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "status")
    val status: String = "SUCCESS",         // SUCCESS | VOID | FAILED
)
