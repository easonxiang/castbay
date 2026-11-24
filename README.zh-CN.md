<p align="right"><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/weenas/castbay/main/branding/castbay-signal-final/inline-dark.png">
    <img src="https://raw.githubusercontent.com/weenas/castbay/main/branding/castbay-signal-final/inline-light.png" alt="CastBay 映湾" width="480">
  </picture>
</p>

<p align="center">
  <a href="https://github.com/weenas/castbay/actions/workflows/android.yml"><img src="https://github.com/weenas/castbay/actions/workflows/android.yml/badge.svg" alt="Android 构建"></a>
  <a href="https://github.com/weenas/castbay/releases/latest"><img src="https://img.shields.io/github/v/release/weenas/castbay?filter=v*&amp;label=Android" alt="最新 Android 版本"></a>
  <a href="https://github.com/weenas/castbay/releases"><img src="https://img.shields.io/github/downloads/weenas/castbay/total" alt="下载量"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/weenas/castbay" alt="GPL-3.0 许可证"></a>
  <a href="https://castbay.weenas.com/compatibility"><img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 6.0 及以上"></a>
  <a href="https://github.com/weenas/castbay/commits/main"><img src="https://img.shields.io/github/last-commit/weenas/castbay" alt="最近提交"></a>
</p>

# 映湾 CastBay：适用于 Android TV、车载屏幕和平板的 AirPlay 与 DLNA 接收器

**一投即达，自在映湾。** · *Cast it, it's there. Kick back with CastBay.*

**官网：[castbay.weenas.com](https://castbay.weenas.com)** · [使用指南](https://castbay.weenas.com/zh/guide) · [下载页面](https://castbay.weenas.com/zh/download) · [工作原理](https://castbay.weenas.com/zh/tech) · **下载：[CastBay.apk](https://github.com/weenas/castbay/releases/latest/download/CastBay.apk)**（[全部版本](https://github.com/weenas/castbay/releases)） · [隐私政策](https://castbay.weenas.com/zh/privacy)

<p align="center">
  <a href="https://github.com/weenas/castbay/releases/latest/download/CastBay.apk"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="从 GitHub 下载" height="60"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/weenas/castbay"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="通过 Obtainium 获取更新" height="60"></a>
</p>

CastBay（中文名：映湾）可以把 Android TV、电视盒子、车载屏幕或平板变成接收端，让 iPhone、iPad 和 Mac 投屏、播放音乐和视频，也能接收视频及音乐 App 内置的投屏按钮（DLNA）。**CastBay 桌面版**还可以把 Windows 或 Linux 电脑的屏幕或窗口投到电视上（[下载](https://castbay.weenas.com/zh/download) · [使用指南](https://castbay.weenas.com/zh/desktop-guide) · [源码](desktop/)）。免费、开源、无广告。

<p align="center"><img src="https://raw.githubusercontent.com/weenas/castbay/main/web/src/assets/screens/home.webp" alt="CastBay 电视主界面" width="100%"></p>
<p align="center"><em>主界面：随时等待投屏，显示手机上看到的名称和网络。</em></p>

<p align="center"><img src="https://raw.githubusercontent.com/weenas/castbay/main/web/src/assets/screens/music.webp" alt="CastBay 正在播放 AirPlay 音乐" width="100%"></p>
<p align="center"><em>音乐：模糊的封面背景和同步歌词。（歌曲：Jonathan Coulton《Code Monkey》，CC BY-NC 授权。）</em></p>

<p align="center"><img src="https://raw.githubusercontent.com/weenas/castbay/main/web/src/assets/screens/mirror.webp" alt="CastBay 正在接收 iPhone 屏幕镜像" width="100%"></p>
<p align="center"><em>屏幕镜像（H.265），打开了播放统计：电视端和手机端的数据。</em></p>

## 功能

- **屏幕镜像**：接收 iPhone、iPad 和 Mac 的屏幕镜像，最高 60 帧/秒；支持 H.265 的 4K 电视可使用硬件 HEVC 解码，最高可达 4K。
- **音乐播放**：显示模糊专辑封面背景，可选同步歌词（来自 lrclib.net）和圆形播放控制。AirPlay 音乐以 ALAC 无损格式接收；实际格式和音质还取决于发送端及音频输出路径。
- **视频投放**：兼容的 AirPlay 视频（例如 YouTube、爱奇艺）由电视直接从来源播放，并可选择音轨和字幕。
- **DLNA**：接收哔哩哔哩、爱奇艺、网易云音乐、QQ 音乐等 App 内置的投屏按钮，支持 iPhone 和 Android 手机；可投放视频（MP4、MKV、WebM、FLV、HLS、DASH 等）和音乐（MP3、AAC、FLAC 等），不支持图片。详见[兼容性说明](https://castbay.weenas.com/zh/compatibility)。
- **遥控器或触摸屏操作**：播放时按遥控器下键或触摸屏幕打开快捷菜单，可调整画面比例、查看播放统计、选择音轨和字幕、暂停或快进视频。媒体按键（例如方向盘上的按键）可暂停和切换曲目；连按两次返回键可结束投放，按 Home 可让播放继续。
- **注重隐私**：可选择允许任何设备、仅允许电视上已授权的设备、首次输入电视显示的 PIN 码或输入密码；也可单独允许或屏蔽设备，以及决定是否接受另一台设备接管。无需账号；只有用户主动选择时才会发送问题报告（发送前会移除个人信息）或匿名使用统计。
- **多语言**：应用支持英语、简体中文和繁体中文；CastBay 桌面版支持英语和简体中文。欢迎在 [Hosted Weblate](https://hosted.weblate.org/engage/castbay/) 参与翻译。新语言达到较完整的翻译进度后会加入应用的语言列表；尚未翻译的文本会显示为英语。

## 系统要求与兼容性

- Android 6.0（API 23）或更高版本的设备：电视、电视盒子、车载屏幕或平板。
- 发送设备和 CastBay 接收设备需要处于同一个本地网络。

已在 TCL（Android 9）、Sony BRAVIA（Android 12）电视和比亚迪车载屏幕上与 iPhone 测试。更多设备和 App 的测试结果见[兼容性列表](https://castbay.weenas.com/zh/compatibility)。

Mac 屏幕镜像可用，但 Mac「音乐」App 的音乐无法投到 CastBay：该 App 使用 UxPlay 尚不支持的 FairPlay type 2（详见 [UxPlay#570](https://github.com/FDH2/UxPlay/issues/570)）。

AirPlay 和 DLNA 视频由电视直接从网络获取（类似 Apple TV），因此电视本身必须能够访问视频源。以 YouTube 为例，需要电视能连接 `googlevideo.com`；如果手机只能通过代理或 VPN 访问，电视也需要相同的网络条件。

## 技术实现

- **AirPlay 协议**：基于 UxPlay 的 `lib/`，实现 RAOP、RTSP、RTP、配对、FairPlay 和带 FCUP 的 AirPlay 视频。
- **JNI 桥接**：原生 C 代码与 Android Java/Kotlin 层交互。
- **视频解码**：使用 Android MediaCodec 硬件加速解码。
- **音频输出**：使用 AudioTrack；AirPlay 音乐按每个音频帧的 NTP 时间播放，镜像音频则在收到后播放。
- **视频播放**：通过 Media3/ExoPlayer 播放 AirPlay（HLS）视频、DLNA 视频和音乐。
- **DLNA**：应用内置 UPnP 媒体渲染器，包含 SSDP、AVTransport/RenderingControl 和 GENA 事件。
- **mDNS 发现**：使用 Android NsdManager 发布 UxPlay 生成的 TXT 记录。

## 从源码构建

```bash
# 克隆仓库，并初始化固定版本的 UxPlay、libplist、ALAC 和 OpenSSL 子模块
git clone https://github.com/weenas/castbay.git
cd castbay
git submodule update --init   # 只初始化顶层子模块，不需要 OpenSSL 自己的子模块

# 构建需要 JDK 17–21、Android SDK Platform 37、NDK 27.0.12077973、CMake 3.22.1、Perl 和 make。
# 首次构建会为各 ABI 编译 OpenSSL 的 libcrypto（tools/build-openssl.sh，约一分钟），之后会复用。
./gradlew assembleDebug
```

已有的克隆仓库也需要先初始化固定版本的依赖：

```bash
git submodule update --init
```

## 不用手机进行测试

Debug 版本使用独立包名 `com.weenas.castbay.debug`，应用名称为「CastBay Dev」，可与正式版同时安装，并内置模拟 AirPlay 发送端。它通过与协议库相同的回调向应用发送数据，因此接收许可、解码、定时音乐播放、页面、菜单和统计都是真实运行；只有网络、配对和加密环节被模拟（这些仍需用真实 iPhone 或 Mac 测试）。`tools/sim` 可通过 adb 控制测试流程和电视遥控器：

```bash
tools/sim install              # 构建、安装并打开 Debug 版
tools/sim mirror 30            # 显示 30 秒测试画面：时钟、帧计数器和移动方块
tools/sim music 60 "My Song"   # 生成带有曲名、艺人、专辑和封面的测试音乐
tools/sim pause                # 模拟发送端暂停或恢复
tools/sim video                # 播放 HLS 视频示例（包含音轨和字幕）
tools/sim stop                 # 模拟发送端断开
tools/sim crash                # 模拟应用崩溃，以测试错误报告
tools/sim key down right ok    # 模拟遥控器按键：up/down/left/right/ok/back/home/menu/playpause
tools/sim shot screen.png      # 截取屏幕
tools/sim log                  # 查看 CastBay 最近日志
```

使用 `-s SERIAL`（或设置 `ANDROID_SERIAL`）选择电视或模拟器。Debug 版不会开机自启，只会在测试时运行。

## 版本与 CI

版本号遵循[语义化版本](https://semver.org)：增加功能时提升中间位（如 `1.1.0`），仅修复问题时提升末位（如 `1.1.1`），大幅变更时提升首位。只有发布版本时才更新 `app/build.gradle.kts` 中的 `versionName` 和 `versionCode`（每次递增）；Debug 版会用构建来源的提交标识命名，例如 `1.1.0-dev+6228385`。

CI 会根据改动范围构建：修改 `app/`、`airplay/`、`third_party/` 或 Gradle 配置时运行 Android 工作流；修改 `web/` 时运行 Website 工作流。无关工作流会显示为跳过。

## 项目结构

```text
castbay/
├── app/                    # Android 应用
│   ├── src/main/
│   │   ├── java/com/weenas/castbay/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/       # 接收器生命周期、AirPlay 桥接、播放器和歌词
│   │   │   │   └── desktop/   # CastBay 桌面版投屏接收端
│   │   │   ├── dlna/          # DLNA 渲染器
│   │   │   ├── ui/            # Compose 页面
│   │   │   ├── viewmodel/     # MVVM ViewModel
│   │   │   ├── receiver/      # 开机、唤醒和启动辅助逻辑
│   │   │   └── util/          # 日志、诊断、报告和统计
│   │   └── res/              # Android 资源
│   ├── src/debug/          # tools/sim 使用的模拟 AirPlay 发送端
│   └── build.gradle.kts
├── airplay/                # Android 库、JNI 桥接和原生协议构建
├── desktop/                # CastBay 桌面版（Rust；Windows 和 Linux）
├── third_party/            # 固定版本的 UxPlay、libplist、ALAC 和 OpenSSL 子模块
├── tools/                  # sim、link-send、device-stats.mjs、build-openssl.sh、ci 等工具
├── web/                    # Astro 官网 castbay.weenas.com，由 Cloudflare 构建和发布
├── fastlane/               # F-Droid 商店文案和更新日志
├── docs/                   # 设计文档和商店发布说明
├── branding/               # Logo 和字标源文件（SVG 和 PNG）
├── README.md               # 英文说明
└── README.zh-CN.md         # 简体中文说明
```

## 官网

网站由 `web/` 下的 [Astro](https://astro.build) 构建，需要 Node.js 22.12 或更新版本。

- `web/src/pages/`：页面。用户指南、工作原理和隐私政策使用 Markdown（`guide.md`、`tech.md`、`privacy.md`，以及 `zh/` 下的对应页面）；首页使用 `index.astro`。
- `web/src/layouts/`：所有页面共用的 `<head>`、页眉、导航和页脚（`Page.astro`），以及 Markdown 页面布局（`Doc.astro`）。
- `web/src/i18n.ts`：共享页面组件使用的各语言文本。
- `web/src/content/releases/<en|zh>/<version>.md`：更新日志页面、GitHub Release 文案和应用内「关于 → 更新内容」共用的内容。发布版本前先为中英文各添加一份，再打 `vX.Y.Z` 标签；没有更新日志时发布工作流会失败。还需在 `fastlane/metadata/android/<en-US|zh-CN>/changelogs/<versionCode>.txt` 添加相同要点的精简版（最多 500 字符），供 F-Droid 显示。网站会从 GitHub 获取每个 APK 的 SHA-256；设置 `CLOUDFLARE_DEPLOY_HOOK` Secret 后，发布工作流会触发网站重新构建。每个 Release 还附有 `CastBay-<version>-mapping.txt`（R8 名称映射文件），可用 Android SDK 命令行工具 `retrace` 还原正式版混淆堆栈。
- `web/public/`：直接发布的网站样式、图片、脚本和 `robots.txt`。

```bash
cd web
npm install        # 首次安装依赖
npm run dev        # 本地预览：http://localhost:4321，修改后自动更新
npm run build      # 构建到 dist/，与 Cloudflare 的构建方式相同
npm test           # 运行 CI 中对 Worker /api/stats 的检查
```

`main` 分支更新后，Cloudflare 会依据 `web/wrangler.jsonc` 构建和发布网站；CI 同时检查构建。新增页面时，还要在 `web/src/i18n.ts` 的 `PAGES` 中登记，站点地图由 `web/src/pages/sitemap.xml.ts` 生成。

`web/worker/index.js` 处理 `/api/*`：错误报告存放在 R2（`castbay-reports`），用户选择加入的使用统计存放在 D1（`castbay-stats`）。维护者可通过 Cloudflare Access（邮箱登录）访问：

- **[https://castbay.weenas.com/stats](https://castbay.weenas.com/stats)**：使用统计汇总，数据接口为 `/api/stats/summary`；也可通过 Cloudflare Access 应用启动器访问：<https://easonxiang.cloudflareaccess.com>。
- 按报告 ID 下载问题报告：登录 Wrangler 后执行 `cd web && npx wrangler r2 object get castbay-reports/reports/CB-XXXXXX.txt --remote --pipe`。
- 页面未覆盖的问题可用 D1 查询：`npx wrangler d1 execute castbay-stats --remote --command "SELECT …"`；详见 `docs/design/usage-stats-and-reports.md`。

## 应用商店

`fastlane/metadata/android/` 保存英语和中文商店页面所需的标题、描述、图标、电视截图和各版本更新日志，F-Droid 会读取这些内容。商店构建使用 `-Pcastbay.selfUpdate=false`，不包含应用内更新器和更新检查，因为应用更新由商店管理。

## 参与贡献

欢迎贡献，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。欢迎通过 [Hosted Weblate](https://hosted.weblate.org/engage/castbay/) 参与翻译。问题反馈、功能建议和设备测试报告可以从[社区欢迎帖](https://github.com/weenas/castbay/discussions/205)开始，也可以浏览[全部讨论](https://github.com/weenas/castbay/discussions)。安全问题请按照 [SECURITY.md](SECURITY.md) 私下报告。所有参与者均应遵守[行为准则](CODE_OF_CONDUCT.md)。

## 许可证

本项目采用 GNU 通用公共许可证第 3 版（GPL-3.0），详见 [LICENSE](LICENSE)。

## 致谢

- [UxPlay](https://github.com/FDH2/UxPlay)：AirPlay 协议库（镜像、音频和 HLS 视频）
- [RPiPlay](https://github.com/FD-/RPiPlay)：UxPlay 库最初发展所基于的项目
- [libplist](https://github.com/libimobiledevice/libplist)：二进制 plist 支持
- [Apple ALAC](https://github.com/macosforge/alac)：AirPlay 音乐的 ALAC 解码器
- [Media3 ExoPlayer](https://github.com/androidx/media)：视频播放
- [LRCLIB](https://lrclib.net)：同步歌词
- Android 开源项目（AOSP）：基础平台

## 免责声明

本项目与 Apple Inc. 或 Google LLC 无关，也未获得其认可。AirPlay、iPhone、iPad 和 Mac 是 Apple Inc. 的商标；Android TV 和 Google TV 是 Google LLC 的商标。
