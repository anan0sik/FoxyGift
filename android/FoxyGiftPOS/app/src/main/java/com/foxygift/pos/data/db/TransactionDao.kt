package com.foxygift.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.foxygift.pos.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE card_number_dec = :cardNumberDec ORDER BY timestamp ASC")
    suspend fun getTransactionsForCard(cardNumberDec: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE shift_id = :shiftId ORDER BY timestamp ASC")
    suspend fun getTransactionsForShift(shiftId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT MAX(receipt_number) FROM transactions")
    suspend fun getMaxReceiptNumber(): Int?

    /** Next sequential receipt number — monotonically increasing. */
    suspend fun nextReceiptNumber(): Int = (getMaxReceiptNumber() ?: 0) + 1

    @Query("SELECT * FROM transactions WHERE timestamp >= :fromIso AND timestamp <= :toIso ORDER BY timestamp ASC")
    suspend fun getTransactionsBetween(fromIso: String, toIso: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE shift_id = :shiftId")
    suspend fun getTransactionCountForShift(shiftId: String): Int

    @Query("SELECT SUM(amount_cents) FROM transactions WHERE shift_id = :shiftId AND operation_type = :opType AND status = 'SUCCESS'")
    suspend fun getSumForShift(shiftId: String, opType: String): Int?

    @Query("SELECT COUNT(*) FROM transactions WHERE operation_type = 'ISSUE' AND status != 'VOID'")
    suspend fun countUnauthorizedPinAttempts(): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY receipt_number DESC")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE shift_id = :shiftId ORDER BY receipt_number DESC")
    fun observeTransactionsForShift(shiftId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateTransactionStatus(id: Long, status: String)

    @Query("DELETE FROM transactions WHERE shift_id = :shiftId")
    suspend fun deleteTransactionsForShift(shiftId: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}
