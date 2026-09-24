package com.foxygift.pos.ui.screens.zreport

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZReportScreen(
    navController: NavController,
    viewModel: ZReportViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    fun formatCents(cents: Long): String {
        val eur = cents / 100
        val frac = (cents % 100).toString().padStart(2, '0')
        return "$eur.$frac ${uiState.currency}"
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
                        Icon(Icons.Default.Assessment, null, tint = FoxyAmber400)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = s.zReportShiftTitle.format(uiState.shiftDate),
                            color = FoxyAmber300,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, s.back, tint = Graphite400)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportCsv() },
                        enabled = !uiState.isExporting,
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = FoxyAmber400)
                        } else {
                            Icon(Icons.Default.Share, s.exportCsv, tint = FoxyAmber400)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite900),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Shift Closed Status Banner
                if (uiState.isShiftClosed) {
                    item {
                        Surface(
                            color = FoxySuccess.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FoxySuccess.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = FoxySuccess, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    s.shiftClosedSuccess,
                                    color = FoxySuccess,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // Row 1: Issued & Redeemed
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KpiCard(
                            label = s.kpiIssued,
                            count = "${uiState.issueCount}",
                            value = formatCents(uiState.issueTotalCents),
                            icon = Icons.Default.CardGiftcard,
                            accentColor = FoxyAmber500,
                            opCountLabel = s.operationsCount,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            label = s.kpiRedeemed,
                            count = "${uiState.redeemCount}",
                            value = formatCents(uiState.redeemTotalCents),
                            icon = Icons.Default.Payment,
                            accentColor = FoxySuccess,
                            opCountLabel = s.operationsCount,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Row 2: Net Liability & Voids
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KpiCard(
                            label = s.kpiLiability,
                            count = "",
                            value = formatCents(uiState.liabilityCents),
                            icon = Icons.Default.AccountBalance,
                            accentColor = FoxyInfo,
                            opCountLabel = "",
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            label = s.kpiVoids,
                            count = "${uiState.voidCount}",
                            value = "${uiState.voidCount} ${s.filterVoid.lowercase(Locale.getDefault())}",
                            icon = Icons.Default.Cancel,
                            accentColor = FoxyError,
                            opCountLabel = s.operationsCount,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Total Transactions Card
                item {
                    Surface(
                        color = Graphite800,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = s.totalTransactions,
                                color = Graphite200,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${uiState.totalTxCount}",
                                color = FoxyAmber400,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }

                // Telegram Summary Button
                item {
                    OutlinedButton(
                        onClick = { viewModel.sendTelegramSummary() },
                        enabled = !uiState.isSendingTg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Graphite800,
                            contentColor = FoxyInfo,
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(Graphite700, Graphite600))
                        ),
                    ) {
                        if (uiState.isSendingTg) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FoxyInfo)
                        } else {
                            Icon(Icons.Default.Send, null, tint = FoxyInfo, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.sendToTelegram, color = FoxyInfo, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Close Shift Button
                item {
                    Button(
                        onClick = { viewModel.closeShift() },
                        enabled = !uiState.isClosingShift,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isShiftClosed) Color(0xFF1E3A2F) else FoxyAmber700
                        ),
                    ) {
                        if (uiState.isClosingShift) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(if (uiState.isShiftClosed) Icons.Default.CheckCircle else Icons.Default.Lock, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (uiState.isShiftClosed) s.shiftClosedSuccess else s.closeShift,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (uiState.isShiftClosed) FoxySuccess else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label:        String,
    count:        String,
    value:        String,
    icon:         androidx.compose.ui.graphics.vector.ImageVector,
    accentColor:  Color,
    opCountLabel: String,
    modifier:     Modifier = Modifier,
) {
    Surface(
        color = Graphite800,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Graphite400,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (count.isNotEmpty()) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = opCountLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Graphite500,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite100,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
