package com.foxygift.pos.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.foxygift.pos.data.db.TransactionDao
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val provisionRepo: ProvisionRepository,
) {
    /**
     * Generates a standard CSV file with all transactions matching the FoxyGift analytics dashboard schema.
     */
    suspend fun generateCsvFile(customDate: String? = null): File = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getAllTransactionsList()
        val today = customDate ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val csvFile = File(exportDir, "Transactions_${today}.csv")

        csvFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            // CSV header matching FoxyGift client analytics dashboard schema
            writer.write("timestamp,terminal_id,location_name,shift_id,receipt_number,card_number_dec,operation_type,amount,balance_after,card_expiration_date,currency,status\n")
            for (tx in transactions) {
                val amountEur = "%.2f".format(Locale.US, tx.amountCents / 100.0)
                val balanceEur = "%.2f".format(Locale.US, tx.balanceAfterCents / 100.0)
                val exp = tx.cardExpirationDate ?: ""
                writer.write(
                    "\"${tx.timestamp}\",\"${tx.terminalId}\",\"${tx.locationName}\",\"${tx.shiftId}\",${tx.receiptNumber},\"${tx.cardNumberDec}\",\"${tx.operationType}\",$amountEur,$balanceEur,\"$exp\",\"${tx.currency}\",\"${tx.status}\"\n"
                )
            }
        }
        csvFile
    }

    /**
     * Opens the Android share chooser to send or email an existing CSV export file.
     */
    fun shareExistingFile(csvFile: File) {
        val terminalId = provisionRepo.getTerminalId().ifBlank { "TERMINAL_001" }
        val locationName = provisionRepo.getLocationName().ifBlank { "FoxyGift Store" }
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "FoxyGift POS Transactions - $terminalId ($today)")
            putExtra(
                Intent.EXTRA_TEXT,
                "FoxyGift POS Card Turnover Report\n\nTerminal ID: $terminalId\nLocation: $locationName\nDate: $today\n\nAttached is the CSV export file containing card turnover transaction logs for import into the merchant analytics dashboard."
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Send transactions CSV...").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun exportAndShareCsv(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = transactionDao.getAllTransactionsList()
            val file = generateCsvFile()
            shareExistingFile(file)
            transactions.size
        }
    }
}
