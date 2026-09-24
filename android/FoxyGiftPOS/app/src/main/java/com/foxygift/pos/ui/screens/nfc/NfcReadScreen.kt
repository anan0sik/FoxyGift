package com.foxygift.pos.ui.screens.nfc

import android.nfc.Tag
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.ui.theme.*

/**
 * Full-screen NFC card read overlay with pulsating radar wave animation.
 *
 * Tear-off Protection UX:
 *   - Full-screen modal blocks navigation during WRITING phase
 *   - High-contrast amber banner: "DO NOT REMOVE CARD"
 *   - Confirms success only after 100% write + verification by NtagDriver
 *
 * [mode]: "issue" | "redeem" | "balance" | "prolong" | "void"
 *
 * IMPORTANT: [mode], [amountCents], and [validityMonths] must be passed as composable
 * parameters. They are forwarded to the ViewModel via [NfcOperationViewModel.prepare]
 * in a LaunchedEffect, ensuring the correct operation is performed regardless of
 * whether this screen is reached via NavHost (SavedStateHandle) or embedded inline.
 */
@Composable
fun NfcReadScreen(
    mode:                    String,
    navController:           NavController,
    onRegisterNfcListener:   ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
    onDismiss:               (() -> Unit)? = null,
    amountCents:             Long = 0L,
    validityMonths:          Int  = 6,
    prolongMonths:           Int  = 3,
    viewModel:               NfcOperationViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val uiState by viewModel.uiState.collectAsState()

    val handleDismiss: () -> Unit = {
        viewModel.reset()
        if (onDismiss != null) {
            onDismiss()
        } else {
            navController.popBackStack()
        }
    }

    // Intercept back button to prevent dropping into an empty black screen
    BackHandler(enabled = true) {
        if (uiState.phase != NfcPhase.WRITING) {
            handleDismiss()
        }
    }

    // ── KEY FIX: always call prepare() so ViewModel uses the correct mode/params ──
    // This runs before any NFC tag can arrive because DisposableEffect runs after.
    LaunchedEffect(mode, amountCents, validityMonths, prolongMonths) {
        viewModel.prepare(
            mode           = mode,
            amountCents    = amountCents,
            validityMonths = validityMonths,
            prolongMonths  = prolongMonths,
        )
    }

    // Register NFC listener with MainActivity
    DisposableEffect(Unit) {
        onRegisterNfcListener { tag -> viewModel.onTagDetected(tag) }
        onDispose { onUnregisterNfcListener() }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Graphite950.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            // Dismiss button — only when not writing
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (uiState.phase == NfcPhase.WAITING || uiState.phase == NfcPhase.FAILED) {
                    IconButton(onClick = handleDismiss) {
                        Icon(Icons.Default.Close, s.cancel, tint = Graphite500)
                    }
                }
            }

            // Operation label
            Text(
                text       = modeLabel(mode, s),
                style      = MaterialTheme.typography.headlineSmall,
                color      = FoxyAmber400,
                fontWeight = FontWeight.Bold,
            )

            // Radar animation
            NfcRadarAnimation(phase = uiState.phase)

            // Amount hint for issue/redeem
            if (uiState.phase == NfcPhase.WAITING && amountCents > 0) {
                val formattedAmount = String.format(java.util.Locale.US, "%.2f", amountCents / 100.0)
                Surface(color = Graphite800, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text     = "$formattedAmount ${s.currency}",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style    = MaterialTheme.typography.headlineMedium,
                        color    = FoxyAmber300,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Duration hint for prolong
            if (uiState.phase == NfcPhase.WAITING && mode == "prolong" && prolongMonths > 0) {
                Surface(color = Graphite800, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text     = "+$prolongMonths ${s.months}",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style    = MaterialTheme.typography.headlineMedium,
                        color    = Color(0xFFD8B4FE),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Status text
            Text(
                text      = when (uiState.phase) {
                    NfcPhase.WAITING -> s.nfcTapCard
                    NfcPhase.READING -> s.nfcReading
                    NfcPhase.WRITING -> s.nfcWriting
                    NfcPhase.SUCCESS -> s.nfcSuccess
                    NfcPhase.FAILED  -> s.nfcFailed
                },
                style     = MaterialTheme.typography.titleMedium,
                color     = when (uiState.phase) {
                    NfcPhase.SUCCESS -> FoxySuccess
                    NfcPhase.FAILED  -> FoxyError
                    NfcPhase.WRITING -> FoxyWarning
                    else             -> Graphite200
                },
                textAlign = TextAlign.Center,
            )

            // Result info on success
            AnimatedVisibility(visible = uiState.phase == NfcPhase.SUCCESS) {
                Surface(color = Graphite800, shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (uiState.balanceCents >= 0) {
                            val formattedBalance = String.format(java.util.Locale.US, "%.2f", uiState.balanceCents / 100.0)
                            Text(
                                s.nfcCardBalance,
                                style = MaterialTheme.typography.labelSmall,
                                color = Graphite400,
                            )
                            Text(
                                "$formattedBalance ${s.currency}",
                                style      = MaterialTheme.typography.displaySmall,
                                color      = FoxyAmber400,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        if (uiState.message.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                resolveSuccessMessage(uiState.message, s),
                                style     = MaterialTheme.typography.bodySmall,
                                color     = Graphite400,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (uiState.cardUid.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "UID: ${uiState.cardUid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Graphite600,
                            )
                        }
                    }
                }
            }

            // Tear-off warning during write
            AnimatedVisibility(visible = uiState.phase == NfcPhase.WRITING) {
                TearOffWarningBanner(s.nfcDoNotRemove, s.nfcKeepOnReader)
            }

            // Error box with full localization
            val errorMsg = resolveErrorMessage(uiState, s)
            if (errorMsg != null) {
                Surface(color = Color(0xFF7F1D1D), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        errorMsg,
                        modifier  = Modifier.padding(16.dp),
                        color     = Color(0xFFFEE2E2),
                        style     = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = handleDismiss,
                    colors  = ButtonDefaults.buttonColors(containerColor = FoxyError),
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    Text(s.nfcClose, fontWeight = FontWeight.Bold)
                }
            }

            // Auto-dismiss on success
            if (uiState.phase == NfcPhase.SUCCESS) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2200)
                    handleDismiss()
                }
            }
        }
    }

    // ── Existing Balance Overwrite Warning Dialog ────────────────────────────
    if (uiState.existingBalanceWarning != null) {
        val formattedBalance = String.format(java.util.Locale.US, "%.2f %s", uiState.existingBalanceWarning!! / 100.0, s.currency)
        AlertDialog(
            onDismissRequest = { viewModel.dismissBalanceWarning() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = FoxyWarning, modifier = Modifier.size(32.dp)) },
            title = {
                Text(
                    text       = s.overwriteBalanceTitle,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = FoxyAmber400,
                    textAlign  = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text      = String.format(s.overwriteBalanceMessage, formattedBalance),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Graphite200,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmOverwriteIssue() },
                    colors  = ButtonDefaults.buttonColors(containerColor = FoxyError),
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    Text(s.overwriteBalanceConfirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissBalanceWarning() },
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    Text(s.cancel)
                }
            },
            containerColor = Graphite800,
            shape          = RoundedCornerShape(18.dp),
        )
    }
}

private fun resolveErrorMessage(state: NfcUiState, s: AppStrings): String? {
    return when (state.errorCode) {
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.CLONE_DETECTED -> s.errCloneDetected
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.WRONG_MERCHANT -> s.errWrongMerchant
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.INSUFFICIENT_BALANCE ->
            "${s.errInsufficientBalance}. ${s.nfcCardBalance}: ${state.errorExtra} ${s.currency}"
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.EXPIRED ->
            "${s.errExpired}: ${state.errorExtra}"
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.EXHAUSTED -> s.errExhausted
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.AUTH_FAILED -> s.errAuthFailed
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.NFC_ERROR ->
            if (state.errorMessage.isNullOrBlank()) s.errNfcRead else "${s.errNfcRead}: ${state.errorMessage}"
        com.foxygift.pos.data.repository.CardRepository.ErrorCode.NONE -> state.errorMessage
    }
}

private fun resolveSuccessMessage(message: String, s: AppStrings): String {
    if (message.startsWith("Card extended. New expiry:")) {
        val date = message.removePrefix("Card extended. New expiry:").trim()
        return "${s.prolongCardTitle}: $date"
    }
    return message
}

// ─────────────────────── Radar Animation ─────────────────────────────────────

@Composable
fun NfcRadarAnimation(phase: NfcPhase) {
    val isActive = phase == NfcPhase.WAITING || phase == NfcPhase.READING || phase == NfcPhase.WRITING
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_radar")

    val scales = (1..3).map { i ->
        infiniteTransition.animateFloat(
            initialValue  = 1f,
            targetValue   = 2.4f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1800, easing = EaseOut, delayMillis = i * 300),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring_$i",
        ).value
    }
    val alphas = (1..3).map { i ->
        infiniteTransition.animateFloat(
            initialValue  = 0.6f,
            targetValue   = 0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1800, delayMillis = i * 300),
                repeatMode = RepeatMode.Restart,
            ),
            label = "alpha_$i",
        ).value
    }

    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        if (isActive) {
            scales.forEachIndexed { i, scale ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .border(2.dp, FoxyAmber500.copy(alpha = alphas[i]), CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            when (phase) {
                                NfcPhase.SUCCESS -> FoxySuccess.copy(0.3f)
                                NfcPhase.FAILED  -> FoxyError.copy(0.3f)
                                NfcPhase.WRITING -> FoxyWarning.copy(0.2f)
                                else             -> FoxyAmber500.copy(0.15f)
                            },
                            Color.Transparent,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Nfc,
                contentDescription = "NFC",
                tint               = when (phase) {
                    NfcPhase.SUCCESS -> FoxySuccess
                    NfcPhase.FAILED  -> FoxyError
                    NfcPhase.WRITING -> FoxyWarning
                    else             -> FoxyAmber500
                },
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun TearOffWarningBanner(doNotRemove: String, keepOnReader: String) {
    Surface(
        color    = Color(0xFF92400E),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, null, tint = FoxyAmber300, modifier = Modifier.size(28.dp))
            Column {
                Text(
                    doNotRemove,
                    fontWeight = FontWeight.ExtraBold,
                    color      = FoxyAmber200,
                    style      = MaterialTheme.typography.titleSmall,
                )
                Text(
                    keepOnReader,
                    color = FoxyAmber300,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun modeLabel(mode: String, s: AppStrings) = when (mode) {
    "issue"   -> s.issueTitle
    "redeem"  -> s.redeemTitle
    "balance" -> s.balanceTitle
    "prolong" -> s.prolongTitle
    "void"    -> s.opVoid
    else      -> mode.replaceFirstChar { it.uppercase() }
}

enum class NfcPhase { WAITING, READING, WRITING, SUCCESS, FAILED }
