package com.foxygift.pos.core.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.foxygift.pos.MainActivity

/**
 * Restarts FoxyGift POS automatically on device reboot (Kiosk mode).
 * Requires RECEIVE_BOOT_COMPLETED permission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
