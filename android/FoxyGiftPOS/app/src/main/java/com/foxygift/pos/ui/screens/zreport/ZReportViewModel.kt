package com.foxygift.pos.ui.screens.zreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.core.export.TransactionExporter
import com.foxygift.pos.core.security.TelegramAlarmClient
import com.foxygift.pos.data.db.TransactionDao
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ZReportUiState(
    val shiftDate:        String  = "",
    val issueCount:       Int     = 0,
    val issueTotalCents:  Long    = 0L,
    val redeemCount:      Int     = 0,
    val redeemTotalCents: Long    = 0L,
    val liabilityCents:   Long    = 0L,
    val voidCount:        Int     = 0,
    val totalTxCount:     Int     = 0,
    val currency:         String  = "EUR",
    val isExporting:      Boolean = false,
    val isSendingTg:      Boolean = false,
    val isClosingShift:   Boolean = false,
    val isShiftClosed:    Boolean = false,
    val feedbackMessage:  String? = null,
    val hasTelegram:      Boolean = false,
)

data class ZReportActionStatus(
    val isExporting:     Boolean = false,
    val isSendingTg:     Boolean = false,
    val isClosingShift:   Boolean = false,
    val isShiftClosed:    Boolean = false,
    val feedbackMessage:  String? = null,
)

@HiltViewModel
class ZReportViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val provisionRepo: ProvisionRepository,
    private val transactionExporter: TransactionExporter,
    private val telegramAlarmClient: TelegramAlarmClient,
) : ViewModel() {

    private val shiftId = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val _statusFlow = MutableStateFlow(
        ZReportActionStatus(isShiftClosed = provisionRepo.isShiftClosedToday(shiftId))
    )

    val uiState: StateFlow<ZReportUiState> = combine(
        transactionDao.observeTransactionsForShift(shiftId),
        _statusFlow,
    ) { txList, status ->
        var iCount = 0
        var iCents = 0L
        var rCount = 0
        var rCents = 0L
        var vCount = 0

        for (tx in txList) {
            when {
                tx.operationType == "VOID" || tx.status == "VOID" -> vCount++
                tx.operationType == "ISSUE" -> {
                    iCount++
                    iCents += tx.amountCents
                }
                tx.operationType.startsWith("REDEEM") -> {
                    rCount++
                    rCents += tx.amountCents
                }
            }
        }

        val liability = maxOf(0L, iCents - rCents)

        ZReportUiState(
            shiftDate        = shiftId,
            issueCount       = iCount,
            issueTotalCents  = iCents,
            redeemCount      = rCount,
            redeemTotalCents = rCents,
            liabilityCents   = liability,
            voidCount        = vCount,
            totalTxCount     = txList.size,
            currency         = provisionRepo.getCurrency().ifBlank { "EUR" },
            isExporting      = status.isExporting,
            isSendingTg      = status.isSendingTg,
            isClosingShift   = status.isClosingShift,
            isShiftClosed    = status.isShiftClosed,
            feedbackMessage  = status.feedbackMessage,
            hasTelegram      = telegramAlarmClient.isConfigured(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ZReportUiState(
            shiftDate     = shiftId,
            currency      = provisionRepo.getCurrency().ifBlank { "EUR" },
            isShiftClosed = provisionRepo.isShiftClosedToday(shiftId),
            hasTelegram   = telegramAlarmClient.isConfigured(),
        )
    )

    fun exportCsv() {
        if (_statusFlow.value.isExporting) return
        _statusFlow.value = _statusFlow.value.copy(isExporting = true, feedbackMessage = null)

        viewModelScope.launch {
            val result = transactionExporter.exportAndShareCsv()
            result.onSuccess { count ->
                _statusFlow.value = _statusFlow.value.copy(
                    isExporting     = false,
                    feedbackMessage = "CSV exported ($count transactions)",
                )
            }.onFailure { err ->
                _statusFlow.value = _statusFlow.value.copy(
                    isExporting     = false,
                    feedbackMessage = "Export error: ${err.localizedMessage ?: err.message}",
                )
            }
        }
    }

    fun sendTelegramSummary() {
        if (_statusFlow.value.isSendingTg) return
        _statusFlow.value = _statusFlow.value.copy(isSendingTg = true, feedbackMessage = null)

        viewModelScope.launch {
            val state = uiState.value
            val terminalId = provisionRepo.getTerminalId().ifBlank { "TERMINAL_001" }
            val locationName = provisionRepo.getLocationName().ifBlank { "FoxyGift POS" }

            val iFormatted = "%.2f %s".format(Locale.US, state.issueTotalCents / 100.0, state.currency)
            val rFormatted = "%.2f %s".format(Locale.US, state.redeemTotalCents / 100.0, state.currency)

            val tgRes = telegramAlarmClient.sendZReportSummary(
                terminalId     = terminalId,
                locationName   = locationName,
                shiftDate      = state.shiftDate,
                issued         = "$iFormatted (${state.issueCount} cards)",
                redeemed       = "$rFormatted (${state.redeemCount} tx)",
                txCount        = state.totalTxCount,
                isClosingShift = false,
            )

            // Also forward CSV document to Telegram for the dashboard
            val csvFile = runCatching { transactionExporter.generateCsvFile(state.shiftDate) }.getOrNull()
            var docSent = false
            if (csvFile != null && csvFile.exists() && tgRes.isSuccess) {
                val docRes = telegramAlarmClient.sendDocument(
                    file    = csvFile,
                    caption = "📄 FoxyGift — Выгрузка транзакций (${state.shiftDate}) для дашборда"
                )
                docSent = docRes.isSuccess
            }

            tgRes.onSuccess {
                _statusFlow.value = _statusFlow.value.copy(
                    isSendingTg     = false,
                    feedbackMessage = if (docSent) "Z-отчет и файл транзакций (CSV) отправлены в Telegram" else "Z-отчет отправлен в Telegram",
                )
            }.onFailure { err ->
                _statusFlow.value = _statusFlow.value.copy(
                    isSendingTg     = false,
                    feedbackMessage = "Telegram error: ${err.localizedMessage ?: err.message}",
                )
            }
        }
    }

    fun closeShift() {
        if (_statusFlow.value.isClosingShift) return
        _statusFlow.value = _statusFlow.value.copy(isClosingShift = true, feedbackMessage = null)

        viewModelScope.launch {
            val state = uiState.value
            val terminalId = provisionRepo.getTerminalId().ifBlank { "TERMINAL_001" }
            val locationName = provisionRepo.getLocationName().ifBlank { "FoxyGift POS" }

            val iFormatted = "%.2f %s".format(Locale.US, state.issueTotalCents / 100.0, state.currency)
            val rFormatted = "%.2f %s".format(Locale.US, state.redeemTotalCents / 100.0, state.currency)

            // 1. Mark shift as closed
            provisionRepo.markShiftClosedToday(shiftId)

            // 2. Generate CSV export with all transactions for the dashboard
            val csvFile = runCatching { transactionExporter.generateCsvFile(state.shiftDate) }.getOrNull()

            // 3. Dispatch Z-Report summary text to Telegram
            val tgRes = telegramAlarmClient.sendZReportSummary(
                terminalId     = terminalId,
                locationName   = locationName,
                shiftDate      = state.shiftDate,
                issued         = "$iFormatted (${state.issueCount} cards)",
                redeemed       = "$rFormatted (${state.redeemCount} tx)",
                txCount        = state.totalTxCount,
                isClosingShift = true,
            )

            // 4. Send transactions CSV document to Telegram for further import into client analytics dashboard
            var csvSentToTg = false
            var tgDocError: String? = null
            if (csvFile != null && csvFile.exists() && telegramAlarmClient.isConfigured()) {
                val docRes = telegramAlarmClient.sendDocument(
                    file    = csvFile,
                    caption = "📄 FoxyGift — Транзакции смены ${state.shiftDate} (${state.totalTxCount} операций) для импорта в клиентский дашборд",
                )
                csvSentToTg = docRes.isSuccess
                if (docRes.isFailure) {
                    tgDocError = docRes.exceptionOrNull()?.localizedMessage ?: docRes.exceptionOrNull()?.message
                }
            }

            // 5. Open Android share sheet so operator can also email/save the CSV file
            if (csvFile != null && csvFile.exists()) {
                runCatching { transactionExporter.shareExistingFile(csvFile) }
            }

            val feedback = when {
                tgRes.isSuccess && csvSentToTg ->
                    "Смена успешно закрыта. Z-отчет и файл транзакций (CSV) отправлены в Telegram!"
                tgRes.isSuccess && tgDocError != null ->
                    "Смена закрыта. Z-отчет отправлен, но ошибка CSV файла в Telegram: $tgDocError"
                tgRes.isSuccess ->
                    "Смена успешно закрыта. Z-отчет отправлен в Telegram."
                else ->
                    "Смена закрыта. Ошибка Telegram: ${tgRes.exceptionOrNull()?.localizedMessage ?: tgRes.exceptionOrNull()?.message}"
            }

            _statusFlow.value = _statusFlow.value.copy(
                isClosingShift  = false,
                isShiftClosed   = true,
                feedbackMessage = feedback,
            )
        }
    }

    fun clearFeedback() {
        _statusFlow.value = _statusFlow.value.copy(feedbackMessage = null)
    }
}
