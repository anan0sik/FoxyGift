package com.foxygift.pos

import android.nfc.NfcAdapter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around the nullable [NfcAdapter] so Hilt can inject it into
 * classes that need to check for NFC availability at runtime.
 *
 * Instead of injecting [NfcAdapter?] directly (which Hilt doesn't support
 * as a @Inject field), inject this holder and call [adapter].
 */
@Singleton
class NfcAdapterHolder @Inject constructor(val adapter: NfcAdapter?)
