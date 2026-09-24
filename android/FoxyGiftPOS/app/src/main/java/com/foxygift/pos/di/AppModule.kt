package com.foxygift.pos.di

import android.content.Context
import android.nfc.NfcAdapter
import androidx.room.Room
import com.foxygift.pos.NfcAdapterHolder
import com.foxygift.pos.core.printer.BalticCharsetRenderer
import com.foxygift.pos.core.printer.EscPosDriver
import com.foxygift.pos.core.printer.ReceiptBuilder
import com.foxygift.pos.data.db.CardDao
import com.foxygift.pos.data.db.FoxyGiftDatabase
import com.foxygift.pos.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FoxyGiftDatabase =
        Room.databaseBuilder(context, FoxyGiftDatabase::class.java, "foxygift.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCardDao(db: FoxyGiftDatabase): CardDao = db.cardDao()
    @Provides fun provideTransactionDao(db: FoxyGiftDatabase): TransactionDao = db.transactionDao()

    /**
     * NfcAdapter may be null on devices without NFC hardware.
     * All NFC callers must handle null gracefully.
     */
    @Provides
    @Singleton
    fun provideNfcAdapter(@ApplicationContext context: Context): NfcAdapter? =
        NfcAdapter.getDefaultAdapter(context)

    @Provides
    @Singleton
    fun provideNfcAdapterHolder(adapter: NfcAdapter?): NfcAdapterHolder = NfcAdapterHolder(adapter)

    @Provides
    @Singleton
    fun provideBalticRenderer(): BalticCharsetRenderer = BalticCharsetRenderer()

    @Provides
    @Singleton
    fun provideEscPosDriver(renderer: BalticCharsetRenderer): EscPosDriver = EscPosDriver(renderer)

    @Provides
    @Singleton
    fun provideReceiptBuilder(renderer: BalticCharsetRenderer): ReceiptBuilder = ReceiptBuilder(renderer)

    /**
     * NtagDriver is a Kotlin object (singleton) — provide it as-is so Hilt
     * can inject it into CardRepository.
     */
    @Provides
    @Singleton
    fun provideNtagDriver(): com.foxygift.pos.core.nfc.NtagDriver = com.foxygift.pos.core.nfc.NtagDriver

    // Note: QrProvisioningScanner, ProvisionRepository, PinSecurityManager,
    // TelegramAlarmClient all have @Singleton @Inject constructor and are
    // provided automatically by Hilt — no @Provides needed here.
}
