---
layout: ../layouts/Doc.astro
lang: en
page: faq
toc: true
faq: true
title: 'FAQ – CastBay'
description: 'CastBay FAQ: supported devices, installing, phones that don''t find the TV, casting and playback, privacy and security.'
heading: 'FAQ'
intro: 'Not answered here? See the <a href="/guide">user guide</a>, or <a href="/feedback">ask us</a>.'
---

## Getting started

### Which devices are supported?

Android devices on Android 6.0 or later receive: TVs, TV boxes, car displays and tablets, worked with a remote or a touch screen. They receive from iPhone, iPad and Mac (AirPlay), from the cast button of apps (DLNA) and from Windows and Linux computers (CastBay Desktop). Tested on Xiaomi (Android 6), TCL (Android 9) and Sony (Android 12) TVs, a Google TV Streamer and a BYD DiLink 5.0 car display (Android 12); see the [compatibility list](/compatibility).

### Can I cast from a Windows or Linux computer?

Yes, with **CastBay Desktop**, a separate program for Windows 10/11 and Ubuntu or Debian. It casts the whole screen or a single window, with its sound, to a device running CastBay; turn on **Desktop casting** in that device's CastBay settings first. It uses CastBay's own encrypted connection rather than AirPlay, DLNA or Miracast, so it casts to CastBay receivers only, and the delay is low: about a quarter of a second on a home network. Get it on the [download page](/download); the [Desktop guide](/desktop-guide) explains the rest. A Mac needs nothing extra: it casts with AirPlay.

### How do I install the APK on a TV?

From the TV's browser (`castbay.weenas.com/apk`), a USB drive, your phone, an app on the TV or a computer with adb. The first time, the TV asks you to allow installing apps from that source. The [user guide](/guide#install-and-update) has each way step by step, where the setting is on Xiaomi, TCL, Sony and Google TVs, and what to do if it won't install.

### Can I trust the APK I download?

Each release is built by GitHub from the tagged source, not on anyone's computer, and signed with CastBay's own key; the build checks the signature before publishing. The complete source of that exact version is attached to every release. Android also only installs an update over CastBay if it carries the same signature, so an altered copy can't replace the one you have.

### Where can I get CastBay, and can I switch between sources?

Everything is on the [download page](/download), from two places:

- **This website or GitHub** ([CastBay.apk](/apk), [releases](https://github.com/weenas/castbay/releases)): signed with CastBay's own key. CastBay checks for new versions and installs them from About. [Obtainium](https://github.com/ImranR98/Obtainium) can also follow the GitHub releases (same key, so they update each other).
- **[F-Droid](https://f-droid.org/packages/com.weenas.castbay/)**: F-Droid builds CastBay from its source code itself and signs it with its own key; updates come through the F-Droid client, usually a few days after each release, and CastBay doesn't check for updates there.

Android only installs an update with the same signature as the app already installed, so **the F-Droid build and the others can't replace each other**: to switch between them, uninstall CastBay first (its settings and allowed devices are removed with it), then install from the other source. Switching between the website and GitHub needs no uninstall.

## Connecting

### My phone doesn't find CastBay. What now?

Make sure the TV and phone are on the same Wi-Fi and CastBay is open, waiting for a connection. Some routers enable "AP isolation", which stops devices finding each other; turn it off in the router's settings. Turning the phone's Wi-Fi off and on can also help.

### Why can't YouTube on my Android phone find CastBay?

YouTube for Android uses Google Cast for this casting workflow, which CastBay currently does not support. Use the YouTube app on the TV itself, or cast from an iPhone or iPad, whose YouTube app uses AirPlay. Apps with a DLNA cast button (Bilibili, iQiyi, NetEase Cloud Music and others) work from Android phones.

### Does it support Google Cast (Chromecast) or Miracast?

CastBay currently does not support Google Cast (Chromecast) media casting or screen mirroring, or Miracast. Use AirPlay or DLNA instead. See [Compatibility](/compatibility) for the specific features and devices tested.

### Can I send music from a Mac?

Mac screen mirroring works. Music from the Mac's Music app doesn't: it uses an encryption the open-source AirPlay library CastBay is built on can't handle.

## Casting and playback

### Why can't I drag the progress bar of AirPlay music?

Because AirPlay doesn't let the TV choose where music plays from. The phone plays the song itself and sends it to the TV a moment ahead, stamped with when each part is to be heard; the TV only plays what arrives. What the TV can send back is a short list of remote commands (play, pause, previous and next track), with no "go to a time" among them, which is also why an Apple TV's remote can't seek AirPlay music. So the bar shows where the song is, and you move it on the phone.

DLNA music is different: the TV fetches and plays the file itself, so its bar can be dragged, and so can the bar of any video cast from an app (AirPlay or DLNA).

### Why does music start about two seconds late?

To stay in step with the phone. An iPhone sends music about two seconds ahead, stamped with when each part should be heard, and CastBay plays it then, as an Apple TV does. That keeps lyrics in sync and lets pausing and resuming pick up where they left off. See [How it works](/tech).

### Screen mirroring lags a little. Is that normal?

Some delay is, as with an Apple TV: the sender has to capture and compress its screen before the TV can show it. Two things shorten it. First, switch the TV's picture mode to **Game**: TVs process the picture to make films look better, and on our Sony that alone took 160 ms. Second, if you mirror a Mac to a 4K TV, choose 1080p: a Mac encodes 1080p much faster than 4K. Set **Maximum Resolution** to 1080p in CastBay, or pick the resolution for CastBay in the Mac's System Settings → Displays while mirroring (the Mac remembers it). In our test (MacBook to a Sony 4K TV) mirroring lagged about 350 ms in the standard picture mode, 190 ms in Game mode, and under 100 ms in Game mode at 1080p. The stats overlay shows how much of it the TV's decoding takes (decode, usually 10–20 ms).

### A video won't play. Why?

Videos cast from apps are fetched by the TV itself. If your phone needs a proxy or VPN to open them (YouTube, for example), the TV needs one too. Screen mirroring isn't affected: the picture comes from the phone.

If the TV says it can't play the video's format, the video's codec or container is one this TV can't decode (an older TV and H.265, for example), or it is copy-protected. [Compatibility](/compatibility) lists the formats CastBay plays over DLNA; pictures aren't among them.

### Why is Bilibili limited to 720p?

Bilibili keeps 1080p and above for its own TV app; casting to other devices tops out at 720p. Use screen mirroring for 1080p.

### How do I stop a cast from the TV or car?

Press Back twice (the remote's Back key, or a car display's or tablet's Back button or gesture). Pressing Home keeps the cast playing in the background.

### Do I have to open CastBay each time I start the car?

On a BYD car display, yes. Switching the car off doesn't shut the display down: it goes to sleep and force-stops every third-party app (CastBay's Diagnostics shows "stop … due to quickboot"). Android doesn't let a force-stopped app hear about anything, or run anything in the background, until it is opened again, so CastBay can't come back by itself when the car starts. Put CastBay on the car's home screen or app bar and tap it once you're in; it then receives until the car is switched off.

On TVs, and on devices that don't force-stop apps, CastBay starts receiving by itself after booting or waking.

### Why does a TV in its screen saver stay black when I cast?

CastBay only wakes the TV and opens itself when **Open When Casting** is on (Settings → Playback; it is off by default). While it is off, a cast that starts while the TV shows its screen saver, the home screen or another app plays "behind" it: the sender says it is casting, the TV shows nothing new.

1. On the TV, turn on **Settings → Playback → Open When Casting**. It jumps to Android's "Display over other apps" page: allow CastBay there.
2. If your TV has no such page, allow it from a computer: `adb shell appops set com.weenas.castbay SYSTEM_ALERT_WINDOW allow`.

From then on, a cast wakes the TV from its screen saver and opens CastBay by itself. This needs CastBay 1.8.10 or later on the TV (it applies to AirPlay, DLNA and computer casts alike).

### Why did changing a setting stop my cast?

Connection and screen-mirroring settings (the device name, casting verification, resolution and so on) only take effect when the receiver restarts, so the device that was casting has to connect again. Playback settings, such as picture fit, stats and lyrics, apply at once without interrupting anything.

## Privacy and security

### Can I stop other people from casting to my TV?

Yes. In Settings → Connection → Casting verification, choose Confirm (the TV asks before a new device casts), PIN (a new device enters a PIN shown on the TV, once) or Password. Any device that has cast can be blocked there too. Casting from apps' own cast button (DLNA) is confirmed on the TV under any of the three.

### Does CastBay collect my data?

Not unless you choose to. There's no account, no ads and no third-party analytics. What leaves the TV only does when you ask for it: with lyrics turned on, the song's title and artist go to lrclib.net to look them up; and when you upload a problem report from About → Diagnostics, or agree to send one after CastBay stopped unexpectedly (or turned on Send Error Reports), the app's recent log goes to this website, with titles, device names, links and internet addresses taken out first. Anonymous usage statistics (counts of casts and failures, never names or titles) are sent only if you turn them on in Settings → General. The daily update check sends nothing about you. See the [privacy policy](/privacy).
