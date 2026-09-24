package com.foxygift.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.foxygift.pos.data.db.entities.CardEntity
import com.foxygift.pos.data.db.entities.TransactionEntity

@Database(
    entities = [
        CardEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FoxyGiftDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun transactionDao(): TransactionDao
}
