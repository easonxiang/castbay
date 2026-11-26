package com.weenas.castbay.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Whether this device is a phone, for keeping the TV receiver off one. CastBay is a
 * receiver for a TV, a TV box, a car display or a tablet: a phone is the sender, not
 * the receiver, and a problem report from one is noise. A phone is a handheld with
 * mobile telephony, a phone-sized screen (under 600 dp smallest width, Android's
 * tablet threshold) and neither TV nor car mode — so a TV (Leanback), a TV box (no
 * telephony) and a car head unit (car mode, or a large screen) are not phones.
 */
object DeviceType {
    fun isPhone(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return false
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION || uiMode == Configuration.UI_MODE_TYPE_CAR) return false
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false
        return context.resources.configuration.smallestScreenWidthDp < 600
    }
}
