package com.foxygift.pos.ui.screens.dailyoperations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.core.export.TransactionExporter
import com.foxygift.pos.data.db.TransactionDao
import com.foxygift.pos.data.db.entities.TransactionEntity
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DailyOperationsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val selectedFilter: String = "ALL",
    val totalCount: Int = 0,
    val issueTotalCents: Long = 0L,
    val redeemTotalCents: Long = 0L,
    val currency: String = "EUR",
    val shiftDate: String = "",
    val isExporting: Boolean = false,
    val exportSuccessCount: Int? = null,
    val exportError: String? = null,
)

@HiltViewModel
class DailyOperationsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val provisionRepo: ProvisionRepository,
    private val transactionExporter: TransactionExporter,
) : ViewModel() {

    private val shiftId = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val _selectedFilter = MutableStateFlow("ALL")
    private val _isExporting = MutableStateFlow(false)
    private val _exportSuccessCount = MutableStateFlow<Int?>(null)
    private val _exportError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DailyOperationsUiState> = combine(
        transactionDao.observeAllTransactions(),
        _selectedFilter,
        _isExporting,
        _exportSuccessCount,
        _exportError,
    ) { txList, filter, exporting, successCount, error ->
        val currentShiftId = provisionRepo.getCurrentShiftId()
        val currentShiftNum = provisionRepo.getCurrentShiftNumber()
        val filtered = when (filter) {
            "ISSUE"   -> txList.filter { it.operationType == "ISSUE" }
            "REDEEM"  -> txList.filter { it.operationType.startsWith("REDEEM") }
            "PROLONG" -> txList.filter { it.operationType == "PROLONG" }
            "VOID"    -> txList.filter { it.operationType == "VOID" || it.status == "VOID" }
            else      -> txList
        }

        var issueSum = 0L
        var redeemSum = 0L
        for (tx in txList) {
            if (tx.status != "VOID") {
                if (tx.operationType == "ISSUE") issueSum += tx.amountCents
                if (tx.operationType.startsWith("REDEEM")) redeemSum += tx.amountCents
            }
        }

        DailyOperationsUiState(
            transactions = txList,
            filteredTransactions = filtered,
            selectedFilter = filter,
            totalCount = txList.size,
            issueTotalCents = issueSum,
            redeemTotalCents = redeemSum,
            currency = provisionRepo.getCurrency().ifBlank { "EUR" },
            shiftDate = "$currentShiftId (#$currentShiftNum)",
            isExporting = exporting,
            exportSuccessCount = successCount,
            exportError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyOperationsUiState(
            currency = provisionRepo.getCurrency().ifBlank { "EUR" },
            shiftDate = provisionRepo.getCurrentShiftId(),
        )
    )

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun exportCsv() {
        if (_isExporting.value) return
        _isExporting.value = true
        _exportError.value = null
        _exportSuccessCount.value = null

        viewModelScope.launch {
            val result = transactionExporter.exportAndShareCsv()
            _isExporting.value = false
            result.onSuccess { count ->
                _exportSuccessCount.value = count
            }.onFailure { err ->
                _exportError.value = err.localizedMessage ?: "Export failed"
            }
        }
    }

    fun clearExportMessages() {
        _exportSuccessCount.value = null
        _exportError.value = null
    }
}
