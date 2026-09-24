package com.foxygift.pos.ui.screens.cancel

import android.nfc.Tag
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.core.nfc.DecimalUidConverter
import com.foxygift.pos.data.db.entities.TransactionEntity
import com.foxygift.pos.ui.screens.nfc.NfcPhase
import com.foxygift.pos.ui.screens.nfc.NfcRadarAnimation
import com.foxygift.pos.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelTransactionScreen(
    navController: NavController,
    onRegisterNfcListener: ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
    viewModel: CancelTransactionViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val uiState by viewModel.uiState.collectAsState()

    // Register NFC listener when on SCAN_CARD step
    DisposableEffect(uiState.step) {
        if (uiState.step == CancelStep.SCAN_CARD) {
            onRegisterNfcListener { tag -> viewModel.onTagDetected(tag) }
        }
        onDispose { onUnregisterNfcListener() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = FoxyError,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = s.cancelTitle,
                            color = FoxyError,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.step != CancelStep.SELECT_TRANSACTION) {
                            viewModel.backToSelect()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back, tint = Graphite400)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite900),
            )

            AnimatedContent(
                targetState = uiState.step,
                label = "cancel_step_transition",
                modifier = Modifier.fillMaxSize()
            ) { step ->
                when (step) {
                    CancelStep.SELECT_TRANSACTION -> SelectTransactionStep(
                        transactions = uiState.todayTransactions,
                        currency = uiState.currency,
                        strings = s,
                        onSelect = { tx -> viewModel.selectTransaction(tx) }
                    )
                    CancelStep.SCAN_CARD -> ScanCardStep(
                        selectedTx = uiState.selectedTx,
                        currency = uiState.currency,
                        isProcessing = uiState.isProcessing,
                        strings = s,
                        onBack = { viewModel.backToSelect() }
                    )
                    CancelStep.ERROR -> ErrorStep(
                        selectedTx = uiState.selectedTx,
                        errorMessage = uiState.errorMessage ?: s.nfcFailed,
                        strings = s,
                        onRetry = { viewModel.retryScan() },
                        onSelectOther = { viewModel.backToSelect() }
                    )
                    CancelStep.SUCCESS -> SuccessStep(
                        selectedTx = uiState.selectedTx,
                        successMessage = uiState.successMessage ?: "",
                        strings = s,
                        onDone = { viewModel.backToSelect() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectTransactionStep(
    transactions: List<TransactionEntity>,
    currency: String,
    strings: AppStrings,
    onSelect: (TransactionEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            color = Graphite800,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FoxyAmber500.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = FoxyAmber400)
                Text(
                    text = strings.cancelSelectTxPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite100,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Graphite600,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = strings.noOperationsToday,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Graphite400,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    SelectableTransactionCard(
                        tx = tx,
                        currency = currency,
                        strings = strings,
                        onClick = { onSelect(tx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableTransactionCard(
    tx: TransactionEntity,
    currency: String,
    strings: AppStrings,
    onClick: () -> Unit,
) {
    val isIssue = tx.operationType == "ISSUE"
    val isRedeem = tx.operationType.startsWith("REDEEM")
    val isProlong = tx.operationType == "PROLONG"

    val (badgeColor, badgeIcon, opLabel) = when {
        isIssue -> Triple(FoxyAmber500, Icons.Default.CardGiftcard, strings.filterIssue)
        isRedeem -> Triple(FoxySuccess, Icons.Default.Payment, strings.filterRedeem)
        isProlong -> Triple(FoxyPurple, Icons.Default.Update, strings.filterProlong)
        else -> Triple(Graphite400, Icons.Default.Receipt, tx.operationType)
    }

    val formattedAmount = when {
        isIssue -> "+${formatCents(tx.amountCents.toLong())} $currency"
        isRedeem -> "-${formatCents(tx.amountCents.toLong())} $currency"
        else -> "${formatCents(tx.amountCents.toLong())} $currency"
    }

    val displayCard = DecimalUidConverter.formatForDisplay(tx.cardNumberDec)
    val timeFormatted = formatIsoTime(tx.timestamp)

    Surface(
        onClick = onClick,
        color = Graphite800,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                            Text(
                                text = opLabel,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = "#${tx.receiptNumber.toString().padStart(6, '0')}",
                        color = Graphite300,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Text(
                        text = "•  $timeFormatted",
                        color = Graphite400,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = displayCard,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Graphite100,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        isIssue -> FoxyAmber400
                        isRedeem -> FoxySuccess
                        else -> Graphite200
                    },
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = FoxyError),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text(
                        text = strings.opVoid,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCardStep(
    selectedTx: TransactionEntity?,
    currency: String,
    isProcessing: Boolean,
    strings: AppStrings,
    onBack: () -> Unit,
) {
    if (selectedTx == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Selected Transaction Summary Header
        Surface(
            color = Graphite800,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FoxyError.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "АННУЛИРОВАНИЕ СДЕЛИ",
                    style = MaterialTheme.typography.labelMedium,
                    color = FoxyError,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Чек #${selectedTx.receiptNumber.toString().padStart(6, '0')}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Карта: ${DecimalUidConverter.formatForDisplay(selectedTx.cardNumberDec)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite300,
                )
                Text(
                    text = "Сумма: ${formatCents(selectedTx.amountCents.toLong())} $currency",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxyAmber400,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Radar NFC Scan Prompt
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            NfcRadarAnimation(phase = if (isProcessing) NfcPhase.WRITING else NfcPhase.WAITING)

            Text(
                text = String.format(
                    Locale.getDefault(),
                    strings.cancelTapCardPrompt,
                    selectedTx.receiptNumber.toString().padStart(6, '0')
                ),
                style = MaterialTheme.typography.titleMedium,
                color = FoxyAmber300,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = strings.nfcDoNotRemove,
                style = MaterialTheme.typography.labelMedium,
                color = Graphite400,
            )
        }

        // Cancel / Back Button
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.cancel, color = Graphite200)
        }
    }
}

@Composable
private fun ErrorStep(
    selectedTx: TransactionEntity?,
    errorMessage: String,
    strings: AppStrings,
    onRetry: () -> Unit,
    onSelectOther: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = Color(0xFF7F1D1D),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, FoxyError, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = strings.cancelImpossibleComment,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFEE2E2),
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                if (selectedTx != null) {
                    Text(
                        text = "Чек #${selectedTx.receiptNumber.toString().padStart(6, '0')} • Карта ${DecimalUidConverter.formatForDisplay(selectedTx.cardNumberDec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFCA5A5),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSelectOther,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.back, color = Graphite200)
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = FoxyError),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Повторить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SuccessStep(
    selectedTx: TransactionEntity?,
    successMessage: String,
    strings: AppStrings,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = Color(0xFF064E3B),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, FoxySuccess, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7),
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        strings.cancelSuccess,
                        selectedTx?.receiptNumber?.toString()?.padStart(6, '0') ?: ""
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                if (successMessage.isNotEmpty()) {
                    Text(
                        text = successMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA7F3D0),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = FoxySuccess),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("К списку сделок", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

private fun formatCents(cents: Long): String {
    return String.format(Locale.US, "%.2f", cents / 100.0)
}

private fun formatIsoTime(isoString: String): String {
    return try {
        if (isoString.contains("T")) {
            isoString.substringAfter("T").take(8)
        } else {
            isoString
        }
    } catch (_: Exception) {
        isoString
    }
}
