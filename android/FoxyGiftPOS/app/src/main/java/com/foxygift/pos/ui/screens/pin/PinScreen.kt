package com.foxygift.pos.ui.screens.pin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.ui.components.PosNumpad
import com.foxygift.pos.ui.navigation.Routes
import com.foxygift.pos.ui.theme.*

@Composable
fun PinScreen(
    navController: NavController,
    viewModel: PinViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onResumeScreen()
    }

    // Navigate to Home on successful PIN
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.PIN) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Graphite950, Graphite900)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            // Logo / Brand header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = FoxyAmber500,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.appName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoxyAmber400,
                )
                Text(
                    text = s.pinUnlockPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite400,
                )
            }

            // Lockout banner
            AnimatedVisibility(visible = uiState.isLocked || uiState.isPermanentlyLocked) {
                LockoutBanner(
                    isPermanent  = uiState.isPermanentlyLocked,
                    remainingMs  = uiState.lockoutRemainingMs,
                    onScanUnlock = { navController.navigate(Routes.PROVISION) }
                )
            }

            // PIN dots
            PinDots(filledCount = uiState.pinDigits.length, maxCount = 6, hasError = uiState.hasError)

            // Error message
            AnimatedVisibility(visible = uiState.remainingAttempts != null) {
                val attempts = uiState.remainingAttempts ?: 0
                val msg = if (attempts > 0) {
                    s.wrongPinRemaining.format(attempts)
                } else {
                    s.wrongPinNextLocks
                }
                Text(
                    text = msg,
                    color = FoxyError,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            // Polished Numpad with Google Stitch styling
            PosNumpad(
                isEnabled = !uiState.isLocked && !uiState.isPermanentlyLocked,
                onDigit = { d -> viewModel.onDigit(d) },
                onSubmit = { viewModel.onSubmitManual() },
                onDelete = { viewModel.onDelete() },
            )

            // QR Provision button (bottom)
            TextButton(onClick = { navController.navigate(Routes.PROVISION) }) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Graphite400, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.provisionTerminal, color = Graphite400, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PinDots(filledCount: Int, maxCount: Int, hasError: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxCount) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            hasError && filled -> FoxyError
                            filled             -> FoxyAmber500
                            else               -> Graphite700
                        }
                    )
            )
        }
    }
}

@Composable
private fun LockoutBanner(isPermanent: Boolean, remainingMs: Long, onScanUnlock: () -> Unit) {
    val s = LocalStrings.current
    val remainingSec = (remainingMs / 1000).toInt()

    Surface(
        color = if (isPermanent) Color(0xFF7F1D1D) else Graphite800,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPermanent) s.permanentlyLocked else s.keyboardLocked.format(remainingSec),
                color = if (isPermanent) FoxyError else FoxyWarning,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isPermanent) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.scanQrToUnlock,
                    color = Graphite400,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onScanUnlock) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.scanQr)
                }
            }
        }
    }
}
