package com.foxygift.pos.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.core.security.UserRole
import com.foxygift.pos.ui.components.PinAuthDialog
import com.foxygift.pos.ui.navigation.Routes
import com.foxygift.pos.ui.theme.*

data class HomeActionItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val requiredRole: UserRole,
    val gradient: Brush,
    val iconColor: Color,
    val borderColor: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val currentRole by viewModel.currentRole.collectAsState()

    var pendingRoleAuth by remember { mutableStateOf<Pair<UserRole, String>?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back button to confirm logout/exit
    BackHandler {
        showExitConfirmDialog = true
    }

    fun onActionClicked(route: String, requiredRole: UserRole) {
        if (viewModel.hasRole(requiredRole)) {
            navController.navigate(route)
        } else {
            pendingRoleAuth = Pair(requiredRole, route)
        }
    }

    // Role elevation dialog
    pendingRoleAuth?.let { (role, route) ->
        PinAuthDialog(
            requiredRole = role,
            authSessionManager = viewModel.authSessionManager,
            onDismiss = { pendingRoleAuth = null },
            onAuthorized = {
                pendingRoleAuth = null
                navController.navigate(route)
            }
        )
    }

    // Exit / Logout Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = FoxyAmber400,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = s.exitConfirmTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FoxyAmber400,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = s.exitConfirmMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite200,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        viewModel.logout()
                        navController.navigate(Routes.PIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FoxyError),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(s.exitConfirmButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmDialog = false },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(s.cancel)
                }
            },
            containerColor = Graphite800,
            shape = RoundedCornerShape(20.dp),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Top Header Bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Brand, role, location
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = s.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = FoxyAmber400,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        RoleBadge(role = currentRole ?: UserRole.CASHIER, strings = s)
                    }
                    Text(
                        text = "${viewModel.getLocationName()} • ${viewModel.getTerminalId()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Graphite400,
                    )
                }

                // Logout Button with confirmation
                OutlinedButton(
                    onClick = { showExitConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Graphite800,
                        contentColor = FoxyAmber400,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(Graphite600, Graphite700))
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = s.logout,
                        modifier = Modifier.size(18.dp),
                        tint = FoxyAmber400,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.logout,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Graphite100,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Primary Actions Hero Section (Google Stitch Design) ───────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Issue / Activate Card Hero
                HeroPrimaryCard(
                    title = s.opIssue,
                    subtitle = s.opIssueSubtitle,
                    badgeText = s.primaryOperationBadge,
                    icon = Icons.Default.CardGiftcard,
                    accentColor = Color(0xFF6EE7B7),
                    gradient = Brush.verticalGradient(listOf(Color(0xFF047857), Color(0xFF064E3B))),
                    borderColor = Color(0xFF10B981).copy(alpha = 0.6f),
                    isLocked = !viewModel.hasRole(UserRole.CASHIER),
                    onClick = { onActionClicked(Routes.ISSUE, UserRole.CASHIER) },
                    modifier = Modifier.weight(1f),
                )

                // Redeem / Pay Card Hero
                HeroPrimaryCard(
                    title = s.opRedeem,
                    subtitle = s.opRedeemSubtitle,
                    badgeText = s.primaryOperationBadge,
                    icon = Icons.Default.Payment,
                    accentColor = FoxyAmber300,
                    gradient = Brush.verticalGradient(listOf(Color(0xFFB45309), Color(0xFF78350F))),
                    borderColor = FoxyAmber400.copy(alpha = 0.6f),
                    isLocked = !viewModel.hasRole(UserRole.CASHIER),
                    onClick = { onActionClicked(Routes.REDEEM, UserRole.CASHIER) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Secondary 6-Action Grid with Symmetrical Stitch Cards ─────────
            val secondaryOperations = listOf(
                HomeActionItem(
                    title = s.opBalance,
                    icon = Icons.Default.AccountBalanceWallet,
                    route = Routes.nfcReadForMode("balance"),
                    requiredRole = UserRole.CASHIER,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF172554))),
                    iconColor = Color(0xFF93C5FD),
                    borderColor = Color(0xFF3B82F6).copy(alpha = 0.4f),
                ),
                HomeActionItem(
                    title = s.opProlong,
                    icon = Icons.Default.Update,
                    route = Routes.PROLONG,
                    requiredRole = UserRole.PROLONG,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF581C87), Color(0xFF3B0764))),
                    iconColor = Color(0xFFD8B4FE),
                    borderColor = Color(0xFF8B5CF6).copy(alpha = 0.4f),
                ),
                HomeActionItem(
                    title = s.opVoid,
                    icon = Icons.Default.Cancel,
                    route = Routes.CANCEL_TRANSACTION,
                    requiredRole = UserRole.ADMIN,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF7F1D1D), Color(0xFF450A0A))),
                    iconColor = Color(0xFFFCA5A5),
                    borderColor = Color(0xFFEF4444).copy(alpha = 0.4f),
                ),
                HomeActionItem(
                    title = s.opDailyOperations,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    route = Routes.DAILY_OPERATIONS,
                    requiredRole = UserRole.ADMIN,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))),
                    iconColor = FoxyAmber400,
                    borderColor = FoxyAmber400.copy(alpha = 0.3f),
                ),
                HomeActionItem(
                    title = s.opZReport,
                    icon = Icons.Default.Assessment,
                    route = Routes.Z_REPORT,
                    requiredRole = UserRole.ADMIN,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF27272A), Color(0xFF18181B))),
                    iconColor = Graphite200,
                    borderColor = Graphite600.copy(alpha = 0.4f),
                ),
                HomeActionItem(
                    title = s.settingsTitle.uppercase(),
                    icon = Icons.Default.Settings,
                    route = Routes.SETTINGS,
                    requiredRole = UserRole.ADMIN,
                    gradient = Brush.verticalGradient(listOf(Color(0xFF27272A), Color(0xFF18181B))),
                    iconColor = Graphite200,
                    borderColor = Graphite600.copy(alpha = 0.4f),
                ),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(secondaryOperations) { item ->
                    val isLocked = !viewModel.hasRole(item.requiredRole)
                    OperationGridCard(
                        item = item,
                        isLocked = isLocked,
                        onClick = { onActionClicked(item.route, item.requiredRole) },
                    )
                }
            }
        }
    }
}

/**
 * Prominent Hero Card for Primary POS operations (Activation & Payment).
 */
@Composable
private fun HeroPrimaryCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    gradient: Brush,
    borderColor: Color,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier
            .height(134.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(10.dp),
        ) {
            // Star Badge top-left
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Lock badge top-right if locked
            if (isLocked) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Graphite400,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            // Central content with squircle icon
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    maxLines = 1,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Graphite300,
                    textAlign = TextAlign.Center,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(role: UserRole, strings: AppStrings) {
    val (label, bg, textCol) = when (role) {
        UserRole.ADMIN -> Triple(strings.roleAdmin, Color(0xFF78350F), FoxyAmber300)
        UserRole.PROLONG -> Triple(strings.roleProlong, Color(0xFF4C1D95), Color(0xFFC4B5FD))
        UserRole.CASHIER -> Triple(strings.roleCashier, Color(0xFF064E3B), Color(0xFF6EE7B7))
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, textCol.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = textCol,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textCol,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OperationGridCard(
    item: HomeActionItem,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val cardHeight = 100.dp
    val borderWidth = 1.dp

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .border(borderWidth, item.borderColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(item.gradient)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Lock badge at top-right
            if (isLocked) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Graphite400,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            // Symmetrical Centered Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.iconColor,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}
