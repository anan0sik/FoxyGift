package com.foxygift.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxygift.pos.ui.theme.*

/**
 * Modern POS Numpad with Google Stitch inspired aesthetics:
 * Sleek graphite surfaces, subtle golden accents, symmetrical keys, haptic response.
 */
@Composable
fun PosNumpad(
    isEnabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onDot: (() -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(
            when {
                onDot != null -> "."
                onSubmit != null -> "✓"
                else -> ""
            },
            "0",
            "⌫"
        ),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> {
                            Spacer(Modifier.size(width = 96.dp, height = 64.dp))
                        }
                        "." -> {
                            NumpadKey(
                                label = "•",
                                isEnabled = isEnabled,
                                isAccent = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDot?.invoke()
                                }
                            )
                        }
                        "✓" -> {
                            NumpadKey(
                                label = "✓",
                                isEnabled = isEnabled,
                                isAction = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSubmit?.invoke()
                                }
                            )
                        }
                        "⌫" -> {
                            NumpadKey(
                                label = "⌫",
                                isEnabled = isEnabled,
                                isDelete = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDelete()
                                }
                            )
                        }
                        else -> {
                            NumpadKey(
                                label = key,
                                isEnabled = isEnabled,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDigit(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    isEnabled: Boolean,
    isDelete: Boolean = false,
    isAction: Boolean = false,
    isAccent: Boolean = false,
    onClick: () -> Unit,
) {
    val keyShape = RoundedCornerShape(16.dp)

    val containerGradient = when {
        isAction -> Brush.verticalGradient(listOf(FoxyAmber600, FoxyAmber700))
        isDelete -> Brush.verticalGradient(listOf(Graphite800, Graphite800))
        isAccent -> Brush.verticalGradient(listOf(Graphite800, Graphite800))
        else     -> Brush.verticalGradient(listOf(Color(0xFF1E222D), Color(0xFF161A22)))
    }

    val borderColor = when {
        isAction -> FoxyAmber500.copy(alpha = 0.6f)
        isDelete -> Graphite600.copy(alpha = 0.4f)
        isAccent -> FoxyAmber500.copy(alpha = 0.4f)
        else     -> Color.White.copy(alpha = 0.07f)
    }

    val contentColor = when {
        isAction -> Color.White
        isDelete -> FoxyAmber400
        isAccent -> FoxyAmber300
        else     -> Graphite100
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = keyShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(width = 96.dp, height = 64.dp)
            .border(1.dp, borderColor, keyShape)
            .clip(keyShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerGradient),
            contentAlignment = Alignment.Center,
        ) {
            if (isDelete) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = label,
                    fontSize = if (label == "•") 32.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
