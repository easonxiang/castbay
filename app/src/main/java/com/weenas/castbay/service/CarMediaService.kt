package com.weenas.castbay.service

import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.weenas.castbay.receiver.ReceiverStarter
import com.weenas.castbay.util.Diagnostics

/**
 * Makes CastBay a media app to the system, as music players are: car systems and System UI bind to a
 * media browser service to list media apps, show what they play and resume the last one. Cars
 * (BYD's) show the media of apps they know; this is what an app can offer. It lists nothing to
 * browse; each visit goes to Diagnostics, so a report shows who asks and what for.
 */
class CarMediaService : MediaBrowserService() {

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        Diagnostics.record("media", "Browser service created")
        sessionToken = AirPlayManager.getInstance(this).mediaSessionToken()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        val recent = rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true
        Diagnostics.record("media", "Browser: $clientPackageName (uid $clientUid) asks for the root" +
            (if (recent) " (recent)" else "") + ", hints ${rootHints?.keySet()?.joinToString()}")
        return BrowserRoot(if (recent) RECENT else ROOT, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowser.MediaItem>>) {
        Diagnostics.record("media", "Browser: children of $parentId")
        val description = MediaDescription.Builder()
            .setMediaId(RESUME)
            .setTitle(getString(com.weenas.castbay.R.string.app_name))
            .setSubtitle(getString(com.weenas.castbay.R.string.notification_waiting))
            .build()
        result.sendResult(listOf(MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)))
        // A system that resumes the last media app asks for this root: the receiver should be up.
        if (parentId == RECENT) ReceiverStarter.start(this, "media browser (recent)")
    }

    private companion object {
        const val ROOT = "castbay_root"
        const val RECENT = "castbay_recent"
        const val RESUME = "castbay_resume"
    }
}
