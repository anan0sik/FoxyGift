package com.foxygift.pos.ui.screens.redeem

import android.nfc.Tag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foxygift.pos.ui.components.PosNumpad
import com.foxygift.pos.ui.components.rememberAmountInputState
import com.foxygift.pos.ui.screens.nfc.NfcReadScreen
import com.foxygift.pos.ui.theme.*

/**
 * Redeem Gift Card screen with support for decimal cents and Google Stitch styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemScreen(
    navController:           NavController,
    onRegisterNfcListener:   ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
) {
    val s = LocalStrings.current
    val amountState = rememberAmountInputState("0.00")
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
            mode                    = "redeem",
            navController           = navController,
            onRegisterNfcListener   = onRegisterNfcListener,
            onUnregisterNfcListener = {
                showNfcOverlay = false
                onUnregisterNfcListener()
            },
            onDismiss               = {
                showNfcOverlay = false
            },
            amountCents             = amountCents,
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
                        Icon(Icons.Default.Payment, null, tint = FoxySuccess)
                        Spacer(Modifier.width(10.dp))
                        Text(s.redeemTitle, color = FoxySuccess, fontWeight = FontWeight.Bold)
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = s.redeemAmountLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite400,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = amountState.formattedWithCurrency(s.currency),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (amountCents > 0) FoxySuccess else Graphite500,
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
                    listOf("5", "10", "20", "50").forEach { amt ->
                        OutlinedButton(
                            onClick = { amountState.setQuickAmount(amt) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Graphite800.copy(alpha = 0.6f),
                                contentColor = FoxySuccess,
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
                        containerColor = FoxySuccess.copy(alpha = 0.85f),
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
                            text = s.redeemTapButton.format(amountState.formattedWithCurrency(s.currency)),
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
