package com.foxygift.pos.ui.screens.cancel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.data.db.TransactionDao
import com.foxygift.pos.data.db.entities.TransactionEntity
import com.foxygift.pos.data.repository.CardRepository
import com.foxygift.pos.data.repository.CardRepository.OpResult
import com.foxygift.pos.data.repository.ProvisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class CancelStep {
    SELECT_TRANSACTION, // Step 3: Choose transaction from today's list
    SCAN_CARD,          // Step 4: Scan card for selected transaction
    SUCCESS,            // Step 5: Cancellation executed successfully
    ERROR               // Step 5: Cancellation failed with error
}

data class CancelTransactionUiState(
    val step: CancelStep = CancelStep.SELECT_TRANSACTION,
    val todayTransactions: List<TransactionEntity> = emptyList(),
    val selectedTx: TransactionEntity? = null,
    val currency: String = "EUR",
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

private data class InternalParams(
    val selectedTx: TransactionEntity? = null,
    val step: CancelStep = CancelStep.SELECT_TRANSACTION,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class CancelTransactionViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val cardRepository: CardRepository,
    private val provisionRepo: ProvisionRepository,
) : ViewModel() {

    private val todayShift = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val _params = MutableStateFlow(InternalParams())

    val uiState: StateFlow<CancelTransactionUiState> = combine(
        transactionDao.observeTransactionsForShift(todayShift),
        _params,
    ) { txList, params ->
        val validTxs = txList.filter { it.status != "VOID" && it.operationType != "VOID" }

        CancelTransactionUiState(
            step = params.step,
            todayTransactions = validTxs,
            selectedTx = params.selectedTx,
            currency = provisionRepo.getCurrency().ifBlank { "EUR" },
            isProcessing = params.isProcessing,
            errorMessage = params.errorMessage,
            successMessage = params.successMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CancelTransactionUiState(
            currency = provisionRepo.getCurrency().ifBlank { "EUR" }
        )
    )

    fun selectTransaction(tx: TransactionEntity) {
        _params.update {
            it.copy(
                selectedTx = tx,
                step = CancelStep.SCAN_CARD,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun backToSelect() {
        _params.update {
            it.copy(
                selectedTx = null,
                step = CancelStep.SELECT_TRANSACTION,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun onTagDetected(tag: Tag) {
        val currentParams = _params.value
        val target = currentParams.selectedTx ?: return
        if (currentParams.isProcessing || currentParams.step != CancelStep.SCAN_CARD) return

        _params.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = cardRepository.cancelTransaction(tag, target)

            when (result) {
                is OpResult.Success -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.SUCCESS,
                            successMessage = result.message,
                        )
                    }
                }
                is OpResult.BusinessError -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.ERROR,
                            errorMessage = result.reason,
                        )
                    }
                }
                is OpResult.NfcError -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.ERROR,
                            errorMessage = result.reason,
                        )
                    }
                }
                OpResult.CloneDetected -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.ERROR,
                            errorMessage = "Подпись карты недействительна. Аннулирование невозможно.",
                        )
                    }
                }
                OpResult.WrongMerchant -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.ERROR,
                            errorMessage = "Карта другого мерчанта. Аннулирование невозможно.",
                        )
                    }
                }
                is OpResult.ExistingBalanceWarning -> {
                    _params.update {
                        it.copy(
                            isProcessing = false,
                            step = CancelStep.ERROR,
                            errorMessage = "Операция не поддерживается для данной карты.",
                        )
                    }
                }
            }
        }
    }

    fun retryScan() {
        _params.update {
            it.copy(
                step = CancelStep.SCAN_CARD,
                errorMessage = null,
            )
        }
    }
}
