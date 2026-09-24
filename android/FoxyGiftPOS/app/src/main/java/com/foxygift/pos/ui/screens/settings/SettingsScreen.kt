package com.foxygift.pos.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.ui.navigation.Routes
import com.foxygift.pos.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController:    NavController,
    onLanguageChange: (AppLanguage) -> Unit = {},
    viewModel:        SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var darkTheme by remember { mutableStateOf(true) }
    var tgEnabled by remember { mutableStateOf(true) }
    var kioskMode by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }
    var showTelegramConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.exportCount) {
        uiState.exportCount?.let { count ->
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
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(s.settingsTitle, color = Graphite200, fontWeight = FontWeight.Bold)
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Language selector ─────────────────────────────────────────
                SettingsSection(s.settingsLanguage) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Language, null,
                                tint     = Graphite400,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${currentLang.flag} ${currentLang.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Graphite200,
                            )
                        }
                        Box {
                            OutlinedButton(
                                onClick = { showLangMenu = true },
                                shape   = RoundedCornerShape(8.dp),
                            ) {
                                Text("▾", color = FoxyAmber400)
                            }
                            DropdownMenu(
                                expanded        = showLangMenu,
                                onDismissRequest = { showLangMenu = false },
                                containerColor  = Graphite800,
                            ) {
                                AppLanguage.entries.forEach { lang ->
                                    DropdownMenuItem(
                                        text    = {
                                            Text(
                                                "${lang.flag} ${lang.displayName}",
                                                color      = if (lang == currentLang) FoxyAmber400 else Graphite200,
                                                fontWeight = if (lang == currentLang) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            showLangMenu = false
                                            onLanguageChange(lang)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Terminal info (loaded from ProvisionRepository) ────────────
                SettingsSection(s.settingsTerminalInfo) {
                    SettingsRow(s.settingsTerminalId, uiState.terminalId)
                    SettingsRow(s.settingsLocation,   uiState.locationName)
                    SettingsRow(s.settingsMerchantId, uiState.merchantId)
                    SettingsRow(s.settingsCurrency,   uiState.currency)
                    SettingsRow(s.settingsNetwork,    uiState.networkName)
                }

                // ── Database Export (CSV to email) ─────────────────────────────
                SettingsSection(s.settingsDatabase) {
                    Button(
                        onClick  = { viewModel.exportDatabaseCsv() },
                        enabled  = !uiState.isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = FoxyAmber800.copy(alpha = 0.8f),
                            contentColor = FoxyAmber100,
                        ),
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = FoxyAmber300)
                        } else {
                            Icon(Icons.Default.Share, null, tint = FoxyAmber300)
                            Spacer(Modifier.width(10.dp))
                            Text(s.settingsExportCsv, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Alerts & Notifications ────────────────────────────────────
                SettingsSection(s.settingsAlerts) {
                    SettingsToggle(s.settingsTelegram, tgEnabled, Icons.Default.Notifications) { tgEnabled = it }
                    SettingsRow(
                        label = s.settingsBotToken,
                        value = if (uiState.hasTelegram) "configured ••••••••" else "not configured",
                        onClick = { showTelegramConfigDialog = true },
                    )
                }

                // ── Display ───────────────────────────────────────────────────
                SettingsSection(s.settingsDisplay) {
                    SettingsToggle(s.settingsDarkMode, darkTheme, Icons.Default.DarkMode) { darkTheme = it }
                }

                // ── Kiosk Mode ────────────────────────────────────────────────
                SettingsSection(s.settingsKiosk) {
                    SettingsToggle(s.settingsKioskLock, kioskMode, Icons.Default.Lock) { kioskMode = it }
                    Surface(
                        color    = Graphite700,
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Info, null, tint = FoxyInfo, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                s.settingsKioskInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = Graphite400,
                            )
                        }
                    }
                }

                // ── Re-Provisioning ───────────────────────────────────────────
                SettingsSection(s.settingsProvision) {
                    OutlinedButton(
                        onClick  = { navController.navigate(Routes.PROVISION) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = FoxyInfo)
                        Spacer(Modifier.width(8.dp))
                        Text(s.settingsScanQr, color = FoxyInfo)
                    }
                }

                // Version info
                Spacer(Modifier.height(8.dp))
                Text(
                    "FoxyGift POS v1.0.0 · com.foxygift.pos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite600,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    // ── Telegram Configuration & Test Dialog ─────────────────────────────────
    if (showTelegramConfigDialog) {
        var tokenInput by remember { mutableStateOf(uiState.tgToken) }
        var chatInput by remember { mutableStateOf(uiState.tgChatId) }
        var isTesting by remember { mutableStateOf(false) }
        var testMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showTelegramConfigDialog = false },
            icon = { Icon(Icons.Default.Send, null, tint = FoxyInfo, modifier = Modifier.size(28.dp)) },
            title = {
                Text(
                    text       = s.settingsTelegram,
                    fontWeight = FontWeight.Bold,
                    color      = FoxyAmber400,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = tokenInput,
                        onValueChange = { tokenInput = it },
                        label         = { Text(s.settingsTgTokenPrompt, style = MaterialTheme.typography.bodySmall) },
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FoxyAmber400,
                            unfocusedBorderColor = Graphite600,
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value          = chatInput,
                        onValueChange  = { chatInput = it },
                        label          = { Text(s.settingsTgChatPrompt, style = MaterialTheme.typography.bodySmall) },
                        supportingText = { Text(s.settingsTgChannelHint, style = MaterialTheme.typography.labelSmall, color = Graphite400) },
                        singleLine     = true,
                        colors         = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FoxyAmber400,
                            unfocusedBorderColor = Graphite600,
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    testMsg?.let {
                        val isOk = it.contains("успешно") || it.contains("success", ignoreCase = true)
                        Text(
                            text  = it,
                            color = if (isOk) FoxySuccess else FoxyError,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isTesting = true
                        testMsg   = "Проверка связи с Telegram..."
                        viewModel.testTelegramConnection(tokenInput, chatInput) { res ->
                            isTesting = false
                            res.onSuccess {
                                testMsg = "Связь с Telegram установлена успешно! Настройки сохранены."
                            }.onFailure { err ->
                                testMsg = "Ошибка: ${err.localizedMessage ?: err.message}"
                            }
                        }
                    },
                    enabled = !isTesting && tokenInput.isNotBlank() && chatInput.isNotBlank(),
                    colors  = ButtonDefaults.buttonColors(containerColor = FoxyAmber500),
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(s.settingsSave, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showTelegramConfigDialog = false },
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

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style    = MaterialTheme.typography.labelMedium,
            color    = FoxyAmber500,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Surface(
            color    = Graphite800,
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
        ) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    label:   String,
    value:   String,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        onClick  = { onClick?.invoke() },
        enabled  = onClick != null,
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Graphite300)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onClick != null) FoxyAmber400 else Graphite400,
                )
                if (onClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector        = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint               = FoxyAmber400,
                        modifier           = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label:     String,
    checked:   Boolean,
    icon:      androidx.compose.ui.graphics.vector.ImageVector,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Graphite400, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Graphite300)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChanged,
            colors          = SwitchDefaults.colors(
                checkedThumbColor = FoxyAmber500,
                checkedTrackColor = FoxyAmber800,
            ),
        )
    }
}
