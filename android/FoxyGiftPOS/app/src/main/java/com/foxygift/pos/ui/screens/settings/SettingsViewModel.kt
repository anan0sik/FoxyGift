package com.foxygift.pos.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.core.export.TransactionExporter
import com.foxygift.pos.core.security.TelegramAlarmClient
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val terminalId:   String  = "",
    val locationName: String  = "",
    val merchantId:   String  = "",
    val currency:     String  = "EUR",
    val networkName:  String  = "",
    val hasTelegram:  Boolean = false,
    val tgToken:      String  = "",
    val tgChatId:     String  = "",
    val isExporting:  Boolean = false,
    val exportCount:  Int?    = null,
    val exportError:  String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val provisionRepo: ProvisionRepository,
    private val transactionExporter: TransactionExporter,
    private val telegramAlarmClient: TelegramAlarmClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            terminalId   = provisionRepo.getTerminalId().ifBlank { "TERMINAL_001" },
            locationName = provisionRepo.getLocationName().ifBlank { "FoxyGift Baltic Store" },
            merchantId   = provisionRepo.getMerchantIdHash().take(8).ifBlank { "1042" },
            currency     = provisionRepo.getCurrency().ifBlank { "EUR" },
            networkName  = provisionRepo.getNetworkName().ifBlank { "FoxyGift Network" },
            hasTelegram  = telegramAlarmClient.isConfigured(),
            tgToken      = provisionRepo.getTelegramToken(),
            tgChatId     = provisionRepo.getTelegramChatId(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun saveTelegramConfig(token: String, chatId: String) {
        provisionRepo.saveTelegramConfig(token, chatId)
        telegramAlarmClient.configure(token, chatId)
        _uiState.value = _uiState.value.copy(
            hasTelegram = telegramAlarmClient.isConfigured(),
            tgToken     = token.trim(),
            tgChatId    = chatId.trim(),
        )
    }

    fun testTelegramConnection(token: String, chatId: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = telegramAlarmClient.testConnection(token, chatId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    hasTelegram = true,
                    tgToken     = token.trim(),
                    tgChatId    = chatId.trim(),
                )
            }
            onComplete(result)
        }
    }

    fun exportDatabaseCsv() {
        if (_uiState.value.isExporting) return
        _uiState.value = _uiState.value.copy(isExporting = true, exportCount = null, exportError = null)

        viewModelScope.launch {
            val result = transactionExporter.exportAndShareCsv()
            result.onSuccess { count ->
                _uiState.value = _uiState.value.copy(isExporting = false, exportCount = count)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isExporting = false, exportError = err.localizedMessage)
            }
        }
    }

    fun clearExportMessages() {
        _uiState.value = _uiState.value.copy(exportCount = null, exportError = null)
    }
}
