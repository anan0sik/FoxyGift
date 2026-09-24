package com.foxygift.pos

import android.app.ActivityManager
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foxygift.pos.ui.navigation.FoxyNavigation
import com.foxygift.pos.ui.theme.AppLanguage
import com.foxygift.pos.ui.theme.FoxyGiftTheme
import com.foxygift.pos.ui.theme.LocaleManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * NfcAdapter is nullable — on devices without NFC hardware this is null.
     * All NFC operations check for null before proceeding.
     */
    @Inject
    lateinit var nfcAdapterHolder: NfcAdapterHolder

    @Inject
    lateinit var localeManager: LocaleManager

    private var pendingNfcTag: Tag? = null
    private var nfcTagListener: ((Tag) -> Unit)? = null

    /** Mutable language state — changing it recomposes the entire UI. */
    private var currentLanguage by mutableStateOf(AppLanguage.EN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle NFC from launch intent
        handleNfcIntent(intent)

        // Restore saved language
        currentLanguage = localeManager.getLanguage()

        setContent {
            FoxyGiftTheme(appLanguage = currentLanguage) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    FoxyNavigation(
                        onRegisterNfcListener   = { listener -> nfcTagListener = listener },
                        onUnregisterNfcListener = { nfcTagListener = null },
                        onLanguageChange        = { lang ->
                            currentLanguage = lang
                            localeManager.setLanguage(lang)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapterHolder.adapter ?: return
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_MUTABLE,
        )
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapterHolder.adapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        val tag = intent?.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        pendingNfcTag = tag
        nfcTagListener?.invoke(tag)
    }

    /** Consume the pending NFC tag (e.g., after a screen transition). */
    fun consumePendingTag(): Tag? = pendingNfcTag?.also { pendingNfcTag = null }

    /** Enter Kiosk / Lock Task mode. Requires Device Owner policy. */
    fun enterKioskMode() {
        val am = getSystemService(ActivityManager::class.java)
        if (am.isInLockTaskMode) return
        try { startLockTask() } catch (_: Exception) { /* Not device owner */ }
    }

    fun exitKioskMode() {
        try { stopLockTask() } catch (_: Exception) {}
    }
}
