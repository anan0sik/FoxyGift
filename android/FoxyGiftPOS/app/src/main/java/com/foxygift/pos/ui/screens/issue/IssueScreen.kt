package com.foxygift.pos.ui.screens.issue

import android.nfc.Tag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Nfc
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
import androidx.navigation.NavController
import com.foxygift.pos.ui.components.PosNumpad
import com.foxygift.pos.ui.components.rememberAmountInputState
import com.foxygift.pos.ui.screens.nfc.NfcReadScreen
import com.foxygift.pos.ui.theme.*

/**
 * Issue / Top-Up Gift Card screen with support for decimal cents and Google Stitch styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueScreen(
    navController:           NavController,
    onRegisterNfcListener:   ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
) {
    val s = LocalStrings.current
    val amountState = rememberAmountInputState("0.00")
    var validityMonths by remember { mutableIntStateOf(6) }
    var showNfcOverlay by remember { mutableStateOf(false) }

    val amountCents = amountState.amountCents

    androidx.activity.compose.BackHandler {
        if (showNfcOverlay) {
            showNfcOverlay = false
        } else {
            navController.popBackStack()
        }
    }

    if (showNfcOverlay) {
        NfcReadScreen(
            mode                    = "issue",
            navController           = navController,
            onRegisterNfcListener   = onRegisterNfcListener,
            onUnregisterNfcListener = {
                showNfcOverlay = false
                onUnregisterNfcListener()
            },
            onDismiss               = {
                showNfcOverlay = false
            },
            amountCents    = amountCents,
            validityMonths = validityMonths,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CardGiftcard, null, tint = FoxyAmber400)
                        Spacer(Modifier.width(10.dp))
                        Text(s.issueTitle, color = FoxyAmber300, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, s.back, tint = Graphite400)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite900),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Large Amount Display with cents
                Surface(
                    color = Graphite800,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = s.issueNominalValue,
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite400,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = amountState.formattedWithCurrency(s.currency),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (amountCents > 0) FoxyAmber400 else Graphite500,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }

                // Quick preset amounts with cents
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("10", "20", "50", "100").forEach { amt ->
                        OutlinedButton(
                            onClick = { amountState.setQuickAmount(amt) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Graphite800.copy(alpha = 0.6f),
                                contentColor = FoxyAmber400,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(Graphite700, Graphite600))
                            ),
                        ) {
                            Text(
                                text = "€$amt.00",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Validity selector
                Surface(
                    color = Graphite800,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = s.issueValidity,
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite400,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(3, 6, 12, 24).forEach { months ->
                                val isSelected = validityMonths == months
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { validityMonths = months },
                                    label = {
                                        Text(
                                            text = "$months ${s.months}",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Graphite700.copy(alpha = 0.5f),
                                        labelColor = Graphite300,
                                        selectedContainerColor = FoxyAmber700,
                                        selectedLabelColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                    }
                }

                // Polished Numpad with dot key for cents
                PosNumpad(
                    isEnabled = true,
                    onDigit = { d -> amountState.onDigit(d) },
                    onDot = { amountState.onDot() },
                    onDelete = { amountState.onDelete() },
                )

                // Symmetrical Bottom Action Button
                Button(
                    onClick = { showNfcOverlay = true },
                    enabled = amountCents > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxyAmber700,
                        disabledContainerColor = Graphite800,
                        disabledContentColor = Graphite600,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Nfc, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = s.issueTapButton.format(amountState.formattedWithCurrency(s.currency)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
