package com.foxygift.pos.ui.navigation

import android.nfc.Tag
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.foxygift.pos.ui.screens.home.HomeScreen
import com.foxygift.pos.ui.screens.pin.PinScreen
import com.foxygift.pos.ui.screens.nfc.NfcReadScreen
import com.foxygift.pos.ui.screens.issue.IssueScreen
import com.foxygift.pos.ui.screens.redeem.RedeemScreen
import com.foxygift.pos.ui.screens.prolong.ProlongScreen
import com.foxygift.pos.ui.screens.settings.SettingsScreen
import com.foxygift.pos.ui.screens.zreport.ZReportScreen
import com.foxygift.pos.ui.screens.provision.ProvisionScreen
import com.foxygift.pos.ui.screens.dailyoperations.DailyOperationsScreen
import com.foxygift.pos.ui.screens.cancel.CancelTransactionScreen
import com.foxygift.pos.ui.theme.AppLanguage

object Routes {
    const val PIN          = "pin"
    const val HOME         = "home"
    const val NFC_READ     = "nfc_read/{mode}"
    const val ISSUE        = "issue"           // ← Enter amount first, then NFC
    const val REDEEM       = "redeem"          // ← Enter amount first, then NFC
    const val PROLONG      = "prolong"         // ← Choose months first, then NFC
    const val CANCEL_TRANSACTION = "cancel_transaction" // ← 5-step cancellation flow
    const val SETTINGS     = "settings"
    const val Z_REPORT     = "zreport"
    const val DAILY_OPERATIONS = "daily_operations"
    const val PROVISION    = "provision"

    /** Direct NFC mode route — only used for balance/void (no amount needed). */
    fun nfcReadForMode(mode: String) = "nfc_read/$mode"
}

@Composable
fun FoxyNavigation(
    onRegisterNfcListener:   ((Tag) -> Unit) -> Unit,
    onUnregisterNfcListener: () -> Unit,
    onLanguageChange:        (AppLanguage) -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.PIN) {
        composable(Routes.PIN) {
            PinScreen(navController = navController)
        }
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }

        // ── Operations with amount-entry screen first ───────────────────────
        composable(Routes.ISSUE) {
            IssueScreen(navController, onRegisterNfcListener, onUnregisterNfcListener)
        }
        composable(Routes.REDEEM) {
            RedeemScreen(navController, onRegisterNfcListener, onUnregisterNfcListener)
        }
        composable(Routes.PROLONG) {
            ProlongScreen(navController, onRegisterNfcListener, onUnregisterNfcListener)
        }
        composable(Routes.CANCEL_TRANSACTION) {
            CancelTransactionScreen(navController, onRegisterNfcListener, onUnregisterNfcListener)
        }

        // ── Direct NFC overlay — for balance/void (no amount input needed) ─
        composable(Routes.NFC_READ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "balance"
            NfcReadScreen(
                mode                    = mode,
                navController           = navController,
                onRegisterNfcListener   = onRegisterNfcListener,
                onUnregisterNfcListener = onUnregisterNfcListener,
            )
        }

        composable(Routes.SETTINGS)         { SettingsScreen(navController, onLanguageChange) }
        composable(Routes.Z_REPORT)         { ZReportScreen(navController) }
        composable(Routes.DAILY_OPERATIONS) { DailyOperationsScreen(navController) }
        composable(Routes.PROVISION)        { ProvisionScreen(navController) }
    }
}
