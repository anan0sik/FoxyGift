package com.foxygift.pos.ui.screens.dailyoperations

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.core.nfc.DecimalUidConverter
import com.foxygift.pos.data.db.entities.TransactionEntity
import com.foxygift.pos.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyOperationsScreen(
    navController: NavController,
    viewModel: DailyOperationsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.exportSuccessCount) {
        uiState.exportSuccessCount?.let { count ->
            Toast.makeText(context, "${s.exportSuccess} ($count)", Toast.LENGTH_LONG).show()
            viewModel.clearExportMessages()
        }
    }

    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let { err ->
            Toast.makeText(context, "${s.exportError}: $err", Toast.LENGTH_LONG).show()
            viewModel.clearExportMessages()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = FoxyAmber400,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = s.dailyOperationsTitle,
                                color = FoxyAmber300,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = uiState.shiftDate,
                                color = Graphite400,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s.back, tint = Graphite400)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.exportCsv() },
                        enabled = !uiState.isExporting,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = FoxyAmber800.copy(alpha = 0.6f),
                            contentColor = FoxyAmber100,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = FoxyAmber300,
                            )
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.exportCsv, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite900),
            )

            // Summary KPI Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryCard(
                    title = s.dailyTotalOperations,
                    value = "${uiState.totalCount}",
                    color = FoxyAmber400,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = s.filterIssue,
                    value = formatCurrency(uiState.issueTotalCents, uiState.currency),
                    color = FoxyAmber400,
                    modifier = Modifier.weight(1.3f),
                )
                SummaryCard(
                    title = s.filterRedeem,
                    value = formatCurrency(uiState.redeemTotalCents, uiState.currency),
                    color = FoxySuccess,
                    modifier = Modifier.weight(1.3f),
                )
            }

            // Filter Chips
            val filterOptions = listOf(
                "ALL" to s.filterAll,
                "ISSUE" to s.filterIssue,
                "REDEEM" to s.filterRedeem,
                "PROLONG" to s.filterProlong,
                "VOID" to s.filterVoid,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filterOptions.forEach { (key, label) ->
                    val isSelected = uiState.selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(key) },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Graphite800,
                            labelColor = Graphite300,
                            selectedContainerColor = FoxyAmber700,
                            selectedLabelColor = Color.White,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) FoxyAmber500 else Graphite700,
                            enabled = true,
                            selected = isSelected,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Transactions List
            if (uiState.filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = Graphite600,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text = s.noOperationsToday,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Graphite400,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = uiState.filteredTransactions,
                        key = { it.id }
                    ) { tx ->
                        TransactionItemCard(tx = tx, currency = uiState.currency)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Graphite800,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Graphite400,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TransactionItemCard(
    tx: TransactionEntity,
    currency: String,
) {
    val isVoid = tx.operationType == "VOID" || tx.status == "VOID"
    val isIssue = tx.operationType == "ISSUE"
    val isRedeem = tx.operationType.startsWith("REDEEM")
    val isProlong = tx.operationType == "PROLONG"

    val (badgeColor, badgeIcon, opLabel) = when {
        isVoid -> Triple(FoxyError, Icons.Default.Cancel, "VOID")
        isIssue -> Triple(FoxyAmber500, Icons.Default.CardGiftcard, "ISSUE")
        isRedeem -> Triple(FoxySuccess, Icons.Default.Payment, "REDEEM")
        isProlong -> Triple(FoxyPurple, Icons.Default.Update, "PROLONG")
        else -> Triple(Graphite400, Icons.Default.Receipt, tx.operationType)
    }

    val formattedAmount = when {
        isIssue -> "+${formatCurrency(tx.amountCents.toLong(), currency)}"
        isRedeem -> "-${formatCurrency(tx.amountCents.toLong(), currency)}"
        else -> formatCurrency(tx.amountCents.toLong(), currency)
    }

    val displayCard = DecimalUidConverter.formatForDisplay(tx.cardNumberDec)
    val timeFormatted = formatIsoTime(tx.timestamp)

    Surface(
        color = Graphite800,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Row: Operation Badge + Receipt Number + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                            Text(
                                text = opLabel,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Text(
                        text = "#${tx.receiptNumber.toString().padStart(6, '0')}",
                        color = Graphite300,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = timeFormatted,
                    color = Graphite400,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Bottom Row: Card decimal number + Amount + Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = displayCard,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Graphite100,
                    )
                    Text(
                        text = "Balance: ${formatCurrency(tx.balanceAfterCents.toLong(), currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Graphite400,
                    )
                }

                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isVoid -> FoxyError
                        isIssue -> FoxyAmber400
                        isRedeem -> FoxySuccess
                        else -> Graphite200
                    },
                )
            }
        }
    }
}

private fun formatCurrency(cents: Long, currency: String): String {
    val whole = cents / 100
    val frac = (cents % 100).toString().padStart(2, '0')
    return "$whole.$frac $currency"
}

private fun formatIsoTime(isoString: String): String {
    return try {
        if (isoString.contains("T")) {
            val timePart = isoString.substringAfter("T").take(8)
            timePart
        } else {
            isoString
        }
    } catch (_: Exception) {
        isoString
    }
}
