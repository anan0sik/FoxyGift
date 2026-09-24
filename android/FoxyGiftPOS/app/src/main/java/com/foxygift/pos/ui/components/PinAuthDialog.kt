package com.foxygift.pos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foxygift.pos.core.security.AuthSessionManager
import com.foxygift.pos.core.security.UserRole
import com.foxygift.pos.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Access-control PIN prompt dialog for elevated operations (Prolongation, Void, Settings, Z-Report).
 * Allows a Cashier to have a Supervisor/Admin authorize a specific operation on the spot.
 */
@Composable
fun PinAuthDialog(
    requiredRole: UserRole,
    authSessionManager: AuthSessionManager,
    onDismiss: () -> Unit,
    onAuthorized: () -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    val titleText = when (requiredRole) {
        UserRole.ADMIN -> s.adminPinRequired
        UserRole.PROLONG -> s.prolongPinRequired
        UserRole.CASHIER -> s.enterPinToAuthorize
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Graphite900,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = FoxyAmber400,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = s.accessDenied,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FoxyAmber300,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, s.cancel, tint = Graphite400)
                    }
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite200,
                    textAlign = TextAlign.Center,
                )

                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    repeat(6) { idx ->
                        val filled = idx < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        errorMessage != null && filled -> FoxyError
                                        filled -> FoxyAmber500
                                        else -> Graphite700
                                    }
                                )
                        )
                    }
                }

                // Error message
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = FoxyError,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                // Compact Numpad
                PosNumpad(
                    isEnabled = !isChecking,
                    onDigit = { digit ->
                        if (enteredPin.length < 6) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            errorMessage = null

                            if (newPin.length >= 4) {
                                scope.launch {
                                    val role = authSessionManager.resolveRoleForPin(newPin)
                                    val isAllowed = when (requiredRole) {
                                        UserRole.CASHIER -> role != null
                                        UserRole.PROLONG -> role == UserRole.PROLONG || role == UserRole.ADMIN
                                        UserRole.ADMIN   -> role == UserRole.ADMIN
                                    }
                                    if (isAllowed) {
                                        onAuthorized()
                                    } else if (newPin.length >= 6) {
                                        errorMessage = s.invalidPin
                                        enteredPin = ""
                                    }
                                }
                            }
                        }
                    },
                    onDelete = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            errorMessage = null
                        }
                    },
                    onSubmit = {
                        if (enteredPin.length >= 4) {
                            scope.launch {
                                val role = authSessionManager.resolveRoleForPin(enteredPin)
                                val isAllowed = when (requiredRole) {
                                    UserRole.CASHIER -> role != null
                                    UserRole.PROLONG -> role == UserRole.PROLONG || role == UserRole.ADMIN
                                    UserRole.ADMIN   -> role == UserRole.ADMIN
                                }
                                if (isAllowed) {
                                    onAuthorized()
                                } else {
                                    errorMessage = s.invalidPin
                                    enteredPin = ""
                                }
                            }
                        }
                    },
                )

                // Cancel Button
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Graphite300),
                ) {
                    Text(s.cancel)
                }
            }
        }
    }
}
