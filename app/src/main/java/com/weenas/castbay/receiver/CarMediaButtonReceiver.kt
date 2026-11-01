package com.weenas.castbay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.weenas.castbay.service.AirPlayManager
import com.weenas.castbay.util.Diagnostics

/**
 * Where a media button goes when no media session is active or CastBay was stopped: a steering
 * wheel's key, a media notification's button. It starts the receiver, so pressing play on a car
 * that just woke up can bring CastBay up; Diagnostics shows whether Android delivers it.
 */
class CarMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.init(context)
        val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        Diagnostics.record("media", "Button receiver: ${intent.action} ${key?.let { KeyEvent.keyCodeToString(it.keyCode) + " action " + it.action }}")
        if (key == null || key.action != KeyEvent.ACTION_DOWN) return
        ReceiverStarter.start(context, "media button")
        AirPlayManager.getInstance(context).mediaKey(key.keyCode)
    }
}
