# 映湾投屏 / CASTBAY 柔和渐变品牌资源

基于选定的「海湾托起三道信号」方向制作。三道信号从内向外由较深紫色过渡到浅紫色；深色背景版另设更亮的紫色与文字，避免直接反白造成层次丢失。`light` 表示适用于浅色背景，`dark` 表示适用于深色背景。除 `app-icon-*` 和预览图外，SVG 与 PNG 背景透明。

| 文件前缀 | 用途 | 导出 |
| --- | --- | --- |
| `symbol-{light,dark}` | 独立图标，网页 favicon / 小图标源文件 | SVG，1024 / 512 / 192 / 64 / 32 px PNG |
| `vertical-{light,dark}` | 图标在上，映湾投屏与 CASTBAY 在下；启动页、介绍页 | SVG，1024 px PNG |
| `horizontal-zh-{light,dark}` | 图标在左，中文主标题在上 | SVG，1600 px 宽 PNG |
| `horizontal-en-{light,dark}` | 图标在左，英文主标题在上 | SVG，1600 px 宽 PNG |
| `inline-{light,dark}` | 图标、CASTBAY、映湾投屏同一行；网页导航栏 | SVG，1600 px 宽 PNG |
| `app-icon-{light,dark}` | 带圆角背景的应用图标预览/导出 | SVG，1024 / 512 / 192 px PNG |
| `adaptive-foreground-{light,dark}` | Android 自适应图标透明前景层 | SVG，1024 / 432 px PNG |

`preview-light-dark.png` 展示两种主题的竖排版。Android 自适应图标的建议背景色：浅色 `#F7F3FF`，深色 `#181225`；前景层已留出安全边距。本站/APP 尚未自动替换为这些资源。

文字 SVG 使用 PingFang SC / Noto Sans SC 与 Avenir Next / Inter 字体栈，其他系统的字形或宽度可能略有不同；PNG 可用于固定外观。正式发布前如需跨平台完全一致，建议将最终文字转为路径。`generate.py` 可重新生成全部 SVG；PNG 是从这些 SVG 导出的。
