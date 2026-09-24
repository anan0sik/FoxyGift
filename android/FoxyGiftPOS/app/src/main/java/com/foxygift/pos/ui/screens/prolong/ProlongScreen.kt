package com.foxygift.pos.ui.screens.prolong

import android.nfc.Tag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavController
import com.foxygift.pos.ui.screens.nfc.NfcReadScreen
import com.foxygift.pos.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProlongScreen(
    navController:           NavController,
    onRegisterNfcListener:   ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
) {
    val s = LocalStrings.current
    var selectedMonths by remember { mutableIntStateOf(3) }
    var showNfcOverlay by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        if (showNfcOverlay) {
            showNfcOverlay = false
        } else {
            navController.popBackStack()
        }
    }

    if (showNfcOverlay) {
        NfcReadScreen(
            mode                    = "prolong",
            prolongMonths           = selectedMonths,
            navController           = navController,
            onRegisterNfcListener   = onRegisterNfcListener,
            onUnregisterNfcListener = { showNfcOverlay = false; onUnregisterNfcListener() },
            onDismiss               = { showNfcOverlay = false },
        )
        return
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
                        Icon(Icons.Default.Update, null, tint = FoxyPurple)
                        Spacer(Modifier.width(10.dp))
                        Text(s.prolongCardTitle, color = Color(0xFFD8B4FE), fontWeight = FontWeight.Bold)
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
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header Card
                Surface(
                    color = Graphite800,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = s.extendCardBy,
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite400,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "+$selectedMonths ${s.months}",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color(0xFFD8B4FE),
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Extension period selector
                Surface(
                    color = Graphite800,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = s.extensionPeriod,
                            style = MaterialTheme.typography.labelMedium,
                            color = Graphite400,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 3, 6, 12).forEach { months ->
                                val isSelected = selectedMonths == months
                                FilterChip(
                                    selected = isSelected,
                                    onClick  = { selectedMonths = months },
                                    label    = {
                                        Text(
                                            text = "$months ${s.months}",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        containerColor = Graphite700.copy(alpha = 0.5f),
                                        labelColor = Graphite300,
                                        selectedContainerColor = FoxyPurple,
                                        selectedLabelColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                    }
                }

                // Instruction Card
                Surface(
                    color = Graphite800,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = FoxyPurple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = s.prolongCardInstruction.format(selectedMonths),
                            style = MaterialTheme.typography.bodySmall,
                            color = Graphite300,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Bottom Action Button
                Button(
                    onClick  = { showNfcOverlay = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = FoxyPurple),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Nfc, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = s.prolongTapButton.format(selectedMonths),
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
