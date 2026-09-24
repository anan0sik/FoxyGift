package com.foxygift.pos.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single gift card in the local Room database.
 *
 * card_number_dec is the BigInteger decimal representation of the
 * 7-byte factory UID — NEVER stored as HEX.
 */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["card_number_dec"], unique = true)]
)
data class CardEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_number_dec")
    val cardNumberDec: String,              // Decimal string, e.g. "1304289871234560"

    @ColumnInfo(name = "nominal_cents")
    val nominalCents: Int,                  // Original face value in cents

    @ColumnInfo(name = "balance_cents")
    val balanceCents: Int,                  // Current balance in cents

    @ColumnInfo(name = "activated_at")
    val activatedAt: String?,               // ISO-8601 timestamp of first ISSUE

    @ColumnInfo(name = "card_expiration_date")
    val cardExpirationDate: String?,        // ISO-8601 date string "YYYY-MM-DD"

    @ColumnInfo(name = "expiry_epoch")
    val expiryEpoch: Int,                   // Unix epoch seconds for chip storage

    @ColumnInfo(name = "status")
    val status: String,                     // ACTIVE | EXHAUSTED | PROLONGED | EXPIRED

    @ColumnInfo(name = "terminal_id")
    val terminalId: String,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "is_prolonged")
    val isProlonged: Boolean = false,
)
