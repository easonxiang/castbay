package com.weenas.castbay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weenas.castbay.util.Diagnostics

/**
 * Starts the receiver when the device boots, so it is ready like an Apple TV, and when the
 * system clock is set, which is how cars (which sleep rather than boot) usually wake: they
 * sync the time. Both broadcasts reach apps that aren't running. Unless "Start on boot" is
 * off in Settings — then nothing starts until the app is opened ([ReceiverStarter]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.init(context)
        Diagnostics.record("boot", "Broadcast ${intent.action}")
        if (intent.action !in START_ACTIONS) return
        ReceiverStarter.start(context, intent.action ?: "broadcast")
    }

    private companion object {
        val START_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Fast-boot variants some TV and phone vendors send instead.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            // A waking car syncs its clock.
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
