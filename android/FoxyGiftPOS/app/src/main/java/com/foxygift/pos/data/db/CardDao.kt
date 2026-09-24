package com.foxygift.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foxygift.pos.data.db.entities.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity): Long

    @Query("SELECT * FROM cards WHERE card_number_dec = :cardNumberDec")
    suspend fun getCard(cardNumberDec: String): CardEntity?

    @Query("SELECT * FROM cards WHERE card_number_dec = :cardNumberDec")
    fun observeCard(cardNumberDec: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards ORDER BY card_number_dec")
    fun observeAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE status = :status ORDER BY card_expiration_date ASC")
    suspend fun getCardsByStatus(status: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE card_expiration_date <= :date AND status = 'ACTIVE' ORDER BY card_expiration_date ASC")
    suspend fun getCardsExpiringBefore(date: String): List<CardEntity>

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("SELECT COUNT(*) FROM cards WHERE status = :status")
    suspend fun getCardCountByStatus(status: String): Int
}
