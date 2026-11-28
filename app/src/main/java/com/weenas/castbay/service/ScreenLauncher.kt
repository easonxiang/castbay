package com.weenas.castbay.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.weenas.castbay.MainActivity
import com.weenas.castbay.R
import com.weenas.castbay.util.Log

/**
 * Brings the app's screen up when a picture starts (mirroring, video) while the app is not on
 * screen: the TV on its home screen or in another app. Without it the sender shows "casting" and
 * the TV stays as it was. Android 10 and later only let an app start a screen from the background
 * with "Display over other apps" granted, so without it the person is told to open the app.
 */
object ScreenLauncher {
    private const val TAG = "CastBayScreen"
    private const val WAKE_MS = 5_000L

    /** MainActivity is on screen (between onStart and onStop). */
    @Volatile var visible = false

    /** Whether Android lets this app start its screen from the background. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

    /** Screen savers remain interactive; only doze/sleep should trigger a music wake-up. */
    fun isInteractive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true

    /**
     * Wakes the TV if it is asleep or showing its screen saver: an activity started over a screen saver
     * does not end it (the cast would play behind it), and a TV in standby shows nothing at all.
     */
    @Suppress("DEPRECATION")
    private fun wake(context: Context) {
        try {
            val power = context.getSystemService(PowerManager::class.java) ?: return
            // Only the acquire's side effect is wanted (it wakes the screen); the lock lets go after a few seconds.
            power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "CastBay:castWake")
                .acquire(WAKE_MS)
            Log.i(TAG, "Woke the screen for a cast")
        } catch (e: Exception) {
            Log.w(TAG, "Could not wake the screen: ${e.message}")
        }
    }

    /**
     * A screen saver stays on top of every activity that is started, and a wake lock does not end it;
     * a window with FLAG_TURN_SCREEN_ON does (the system wakes the screen for it), and a window of the
     * "Display over other apps" kind needs no activity. It is one pixel, invisible, and removed at once.
     */
    private fun wakeWithWindow(context: Context) {
        if (!allowed(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val windows = context.getSystemService(android.view.WindowManager::class.java) ?: return
            val view = android.view.View(context)
            val params = android.view.WindowManager.LayoutParams(
                1, 1,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            windows.addView(view, params)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { windows.removeView(view) } catch (_: Exception) { }
            }, 1500)
            Log.i(TAG, "Asked the system to turn the screen on")
        } catch (e: Exception) {
            Log.w(TAG, "Could not turn the screen on with a window: ${e.message}")
        }
    }

    /** "Open When Casting" is off and nothing of CastBay is on screen: say where the switch is (a TV in its screen saver shows nothing, but one on the home screen or in an app does). */
    fun hintSetting(context: Context) {
        if (visible) return
        Log.i(TAG, "A cast started while CastBay is not on screen and Open When Casting is off")
        Toast.makeText(context, R.string.open_on_cast_off_toast, Toast.LENGTH_LONG).show()
    }

    /** Opens the screen if it is not showing; must run on the main thread (the toast). */
    fun show(context: Context) {
        if (visible) return
        wake(context)
        wakeWithWindow(context)
        if (!allowed(context)) {
            Log.i(TAG, "Not on screen and not allowed to open it (Display over other apps)")
            Toast.makeText(context, R.string.open_on_cast_toast, Toast.LENGTH_LONG).show()
            return
        }
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    // The screen saver does not give way to an activity on its own: this one asks the system to turn the screen on.
                    .putExtra(MainActivity.EXTRA_WAKE, true)
            )
            Log.i(TAG, "Opened the screen for a cast")
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the screen: ${e.message}")
        }
    }
}
