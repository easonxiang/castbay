---
layout: ../layouts/Doc.astro
lang: en
toc: true
page: desktop-guide
title: 'CastBay for PC guide – CastBay'
description: 'How to use CastBay for PC: casting a Windows or Linux computer''s screen or a window to a CastBay receiver, the tray menu, every setting, updating, and what it sends over the network.'
heading: 'CastBay for PC guide'
intro: 'CastBay for PC casts a computer''s screen or a window, with its sound, to a device running CastBay. Get it on the <a href="/download">download page</a>. For the receiver side, see the <a href="/guide">user guide</a>.'
---

CastBay for PC uses its own encrypted connection, not AirPlay or DLNA, so the delay is low: about a quarter of a second on a home network.

## What you need

- **Windows 10 or 11, 64-bit** (with the Microsoft Edge WebView2 runtime, already part of Windows 11 and recent Windows 10), or **Ubuntu or Debian, 64-bit**, on Wayland with PipeWire.
- A receiver: a device with CastBay on Android 6.0 or later (a TV, TV box, car display or tablet), on the same network, with **Desktop casting** turned on in its settings. Not a guest network.

## Install

- **Windows installer** (the .msi, in Chinese or English): double-click, next, finish. No administrator rights needed. It adds a Start menu entry and an entry in Settings → Apps for uninstalling. A newer installer updates in place and keeps your pairings.
- **Windows portable** (the .zip): unzip anywhere and run **CastBay Desktop.exe**. Nothing is installed; delete the folder to remove it.
- **Linux** (the .deb): `sudo apt install ./castbay-desktop_<version>_amd64.deb`, then start **CastBay for PC** from the applications menu. It was tested on Ubuntu 26.04 with GNOME.

All of them are on the <a href="/download">download page</a>.

## The first cast

1. Run it. It is not code-signed yet, so Windows SmartScreen may say it protected your PC: choose **More info**, then **Run anyway**.
2. If the Windows firewall asks, allow **private networks**. Without it receivers cannot be found.
3. Pick your receiver in the list. Choose **Entire screen** or **Single window** (on Linux the system asks what to share when the cast starts, see below). Press **Start casting**.
4. The first time, the receiver shows a 6-digit code: type it in the window. Later casts need no code.

The list says what each receiver asks of a new computer: **Open** (nothing), approval on the receiver's screen, or a code. A receiver this computer has paired with says only **Paired**, in green. A lock means it also wants a password, which you type in the window.

## While casting

- The casting page says where the cast goes and its size. **Details** (time to decode, frame rate, dropped pictures, bitrate) is folded away. **Stop casting** ends it.
- **Switch what is cast** (Windows): choose another window or screen from the casting page, or from the tray menu, without ending the cast. If the window being cast is closed, the picture stays as it was and the cast waits for a new choice; the window comes back to ask.
- By default the window **minimises when a cast starts**, and closing it keeps the cast running in the tray.
- While the PC is locked Windows gives nothing to capture, so the picture stays; the window says so, and the cast goes on when you unlock. While casting, the PC does not fall asleep or switch its screen off by itself.
- The bitrate is the most that is used: when the connection cannot keep up it goes down and creeps back up after twenty calm seconds.

## The tray menu

Click the tray icon to open the window; right-click it for the menu:

- **Open CastBay for PC** and **Settings**.
- **Start casting** (when nothing is cast, Windows): the screens and windows to choose from. It casts to the receiver you used last without opening the window. If that receiver is not found or wants a password, the window opens and says why; the first pairing's code is typed in the window too.
- **Switch what is cast** (while casting, Windows): the same list, kept up to date as windows open and close.
- **Stop casting**, **About** and **Quit** (which ends a cast in progress).

## Settings

Press the sliders icon at the top right. Changes apply to the next cast.

| Setting | What it does |
| --- | --- |
| Bitrate | A slider from 2 to 20 Mbit/s (8 by default). A higher bitrate means a sharper picture and more network use. |
| Frame rate | 30 or 60 fps, the most that is sent. The cast follows the screen: pictures go out as fast as the screen changes, up to this rate, and a screen that stays still is sent at about 10 a second (the casting page says so under Details) until it moves again. 60 makes games and dragged windows follow more closely and wants 8 Mbit/s or more. |
| Send sound too | The sound the PC plays is sent as well (and still plays on the PC). When a window is cast on Windows, only that window's sound is sent (a browser's included), not the music playing in another program. Casting a whole screen, and Linux, send everything the PC plays. |
| Encoder | Automatic tries AMD, then NVIDIA, then the CPU (x264). The list shows only what works on this computer. A graphics card encodes with its own video unit and hardly uses the CPU. |
| Start with Windows / Start at login | Starts with the computer and waits in the tray. |
| Check for updates at start | Looks for a newer version when the program starts (and again when its window is brought back, at most once an hour). |
| Minimise the window when a cast starts | On by default. |
| Language | Follow the system, Chinese or English (the tray menu too). |
| Send anonymous usage statistics | Off by default. See below. |
| Send error reports | Off by default. See below. |
| Paired receivers | The receivers paired before and when each was last connected. **Forget** removes one: the next cast pairs again. |
| Restore default settings | Puts the choices above back (what you picked last, and your pairings, stay). |
| About | See below. |

## About, updates and problems

**About** (Settings, or the tray menu) is a window with the version and whether a newer one is out, with **What's new** (this site's changelog) and **Update**, then:

- **Check for updates**, **Diagnostics**, **Report a problem**, **Open log folder**, **Licence** (the text of the GPL-3.0) and **Source code** (the repository).
- **Diagnostics** shows facts about this computer and the end of the log. **Upload log** asks first, then gives a report ID to put in your problem report. Network addresses, receivers' names and your user folder are taken out before anything is sent, and only you pressing Upload sends it.

**Updating**: the notice on the main page, and About, have **Update**. On Windows it downloads the new installer, checks its SHA-256 against the one the website publishes, installs it and starts the program again. On Linux it asks for your password in the system's own box and installs the new .deb. The portable version opens the release page instead. **Skip this version** on the notice keeps that version's notice away; a newer version brings it back.

## Linux notes

- **What to share**: there is no list of screens and windows in the window. When you press **Start casting** the system asks which to share, a screen or a window. Tick **remember this choice** and it does not ask again; **Choose again** on the main page makes it ask at the next cast. If the remembered choice goes stale, CastBay for PC forgets it after 15 seconds and the system's dialog comes back; choose again. (It is kept in `~/.local/share/castbay-desktop/screencast-token`; deleting that file does the same.)
- **Changing what is cast**: **Change what is cast…** on the casting page opens the system's dialog again, without ending the cast. If you cancel it, the cast goes on as it was. The tray menu's switch entry stays grey on Linux, because only the system's dialog can choose.
- **Tray**: closing the window during a cast keeps the cast running in the tray. GNOME shows no tray icons by default (install the AppIndicator extension to see one); without it, open **CastBay for PC** from the applications menu again and the running window comes back.
- **Start at login** writes `~/.config/autostart/castbay-desktop.desktop`.
- **Encoders**: NVIDIA graphics cards use NVENC; everything else uses x264 on the CPU (AMD and Intel hardware encoders are not built in yet).

<a id="linux-fullscreen"></a>

### A fullscreen video stops moving on the TV

When a **whole screen** is cast and a video is played **fullscreen** (in a browser or a player), the picture on the TV can freeze while the computer's own screen plays on. It is not the connection and not the video format.

- **Why**: while a fullscreen window covers the screen, GNOME sends that window straight to the display ("direct scanout") and stops giving new pictures to programs that record the screen, until something else is redrawn. It is GNOME's behaviour (mutter), seen with NVIDIA graphics on Wayland, and CastBay for PC cannot change it from outside; the report is [mutter issue 3074](https://gitlab.gnome.org/GNOME/mutter/-/issues/3074), and KDE has a similar one ([KDE bug 495287](https://bugs.kde.org/show_bug.cgi?id=495287)).
- **What to do**: cast the **window** instead of the screen. When the system asks what to share, choose the browser's or the player's window; its picture keeps moving fullscreen. If the system does not ask, press **Choose again** on the main page first. Already casting a screen: **Change what is cast…** on the casting page.

<a id="linux-sound"></a>

### The whole computer's sound is sent, not one window's

On **Windows**, casting a window sends only that window's sound. On **Linux** the sound of the whole computer is sent, also when a window is cast: music from another program goes to the TV too.

- **Why**: on Wayland the system's share dialog gives the program a picture, and does not say which program the window belongs to, so CastBay for PC cannot tell whose sound goes with it. (Windows says which program owns a window, and can hand over one program's sound.)
- **What to do**: stop or mute the other sound while casting, or turn off **Send sound too** in the settings and let the TV stay silent.

## If something goes wrong

- **No receiver in the list**: both devices on the same network (not a guest network); Desktop casting turned on at the receiver; the firewall allows private networks.
- **Needs someone to allow it**: the receiver asks on its own screen the first time.
- **Another device is using it**: stop the other cast first, or allow takeover at the receiver.
- **The receiver was reset**: if the window says its key changed, choose **Forget this receiver** and pair again.
- **The picture gets worse the longer it casts** (AMD graphics): fixed in 0.1.5. Update.
- **A TV in its screen saver stays black**: needs CastBay 1.8.10 on the TV with **Open When Casting** on ([how](/faq#why-does-a-tv-in-its-screen-saver-stay-black-when-i-cast)).
- **Log files**: **Open log folder** in About.

## Privacy and security

The connection is encrypted (TLS) and both sides prove who they are with their own keys. A receiver's key is remembered after the first pairing, and a changed key is refused until you forget the receiver. There is no account.

Nothing about your screen leaves your network except to the receiver you chose. The program also asks this website, GitHub and a relay for the newest version. Two things are **off unless you turn them on**: an anonymous summary once a day (**usage statistics**), and a report when the program ended badly or a cast failed (**error reports**). The <a href="/privacy">privacy policy</a> lists every field.

## Uninstall

Windows: Settings → Apps → CastBay for PC → Uninstall; it asks whether to delete your pairings and logs too. Portable: delete the folder (the settings stay in `%APPDATA%\com.weenas.castbay.desktop`). Linux: `sudo apt remove castbay-desktop`.
