package com.foxygift.pos.ui.screens.nfc

import android.nfc.Tag
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxygift.pos.data.repository.CardRepository
import com.foxygift.pos.data.repository.CardRepository.ErrorCode
import com.foxygift.pos.data.repository.CardRepository.OpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NfcUiState(
    val phase:                  NfcPhase  = NfcPhase.WAITING,
    val cardUid:                String    = "",
    val balanceCents:           Long      = 0L,
    val message:                String    = "",
    val errorMessage:           String?   = null,
    val errorCode:              ErrorCode = ErrorCode.NONE,
    val errorExtra:             String    = "",
    val existingBalanceWarning: Long?     = null,
    val pendingTag:             Tag?      = null,
)

@HiltViewModel
class NfcOperationViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    savedStateHandle:           SavedStateHandle,
) : ViewModel() {

    private var runtimeMode:           String = savedStateHandle["mode"]           ?: "balance"
    private var runtimeAmountCents:    Long   = savedStateHandle["amountCents"]    ?: 0L
    private var runtimeValidityMonths: Int    = savedStateHandle["validityMonths"] ?: 6
    private var runtimeProlongMonths:  Int    = savedStateHandle["prolongMonths"]  ?: 3

    fun prepare(
        mode:           String,
        amountCents:    Long = 0L,
        validityMonths: Int  = 6,
        prolongMonths:  Int  = 3,
    ) {
        runtimeMode           = mode
        runtimeAmountCents    = amountCents
        runtimeValidityMonths = validityMonths
        runtimeProlongMonths  = prolongMonths
    }

    private val _uiState = MutableStateFlow(NfcUiState())
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()

    fun onTagDetected(tag: Tag) {
        if (_uiState.value.phase != NfcPhase.WAITING && _uiState.value.existingBalanceWarning == null) return

        _uiState.value = _uiState.value.copy(phase = NfcPhase.READING)

        viewModelScope.launch {
            val result: OpResult = when (runtimeMode) {
                "issue" -> {
                    _uiState.value = _uiState.value.copy(phase = NfcPhase.WRITING)
                    cardRepository.issueCard(tag, runtimeAmountCents, runtimeValidityMonths, forceOverwrite = false)
                }
                "redeem" -> {
                    val r = cardRepository.redeemCard(tag, runtimeAmountCents)
                    if (r is OpResult.Success) {
                        _uiState.value = _uiState.value.copy(phase = NfcPhase.WRITING)
                    }
                    r
                }
                "prolong" -> {
                    val r = cardRepository.prolongCard(tag, runtimeProlongMonths)
                    if (r is OpResult.Success) {
                        _uiState.value = _uiState.value.copy(phase = NfcPhase.WRITING)
                    }
                    r
                }
                "void" -> {
                    val r = cardRepository.voidCard(tag)
                    if (r is OpResult.Success) {
                        _uiState.value = _uiState.value.copy(phase = NfcPhase.WRITING)
                    }
                    r
                }
                else -> cardRepository.readBalance(tag)
            }

            handleResult(result, tag)
        }
    }

    private fun handleResult(result: OpResult, tag: Tag? = null) {
        when (result) {
            is OpResult.Success -> _uiState.value = NfcUiState(
                phase        = NfcPhase.SUCCESS,
                cardUid      = result.cardUid,
                balanceCents = result.balanceCents,
                message      = result.message,
            )
            is OpResult.ExistingBalanceWarning -> _uiState.value = NfcUiState(
                phase                  = NfcPhase.WAITING,
                cardUid                = result.cardNumberDec,
                existingBalanceWarning = result.currentBalanceCents,
                pendingTag             = tag,
            )
            is OpResult.BusinessError -> _uiState.value = NfcUiState(
                phase        = NfcPhase.FAILED,
                errorMessage = result.reason,
                errorCode    = result.code,
                errorExtra   = result.extra,
            )
            is OpResult.NfcError -> _uiState.value = NfcUiState(
                phase        = NfcPhase.FAILED,
                errorMessage = result.reason,
                errorCode    = result.code,
            )
            OpResult.CloneDetected -> _uiState.value = NfcUiState(
                phase        = NfcPhase.FAILED,
                errorCode    = ErrorCode.CLONE_DETECTED,
            )
            OpResult.WrongMerchant -> _uiState.value = NfcUiState(
                phase        = NfcPhase.FAILED,
                errorCode    = ErrorCode.WRONG_MERCHANT,
            )
        }
    }

    fun confirmOverwriteIssue() {
        val tag = _uiState.value.pendingTag
        if (tag == null) {
            _uiState.value = _uiState.value.copy(
                existingBalanceWarning = null,
                phase = NfcPhase.WAITING,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            existingBalanceWarning = null,
            phase = NfcPhase.WRITING,
        )

        viewModelScope.launch {
            val res = cardRepository.issueCard(
                tag            = tag,
                nominalCents   = runtimeAmountCents,
                validityMonths = runtimeValidityMonths,
                forceOverwrite = true,
            )
            handleResult(res, tag)
        }
    }

    fun dismissBalanceWarning() {
        _uiState.value = _uiState.value.copy(
            existingBalanceWarning = null,
            pendingTag = null,
            phase = NfcPhase.WAITING,
        )
    }

    fun reset() {
        _uiState.value = NfcUiState()
    }
}
