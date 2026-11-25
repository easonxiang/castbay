#!/usr/bin/env python3
"""Regenerate standalone CastBay signal-mark SVG assets."""

from pathlib import Path


OUT = Path(__file__).resolve().parent

PALETTES = {
    "light": {
        "outer": ("#C9B7EF", "#AA92DE"),
        "middle": ("#A98EE0", "#8663C8"),
        "inner": ("#8762C5", "#623CA6"),
        "base": ("#4F298F", "#6440A7"),
        "primary_text": "#4F298F",
        "secondary_text": "#6744A5",
        "divider": "#B6A2DC",
        "app_background": "#F7F3FF",
    },
    "dark": {
        "outer": ("#E5DAFF", "#CDBDF5"),
        "middle": ("#D0B6F5", "#AE8BE8"),
        "inner": ("#B286E2", "#9264CF"),
        "base": ("#9E74DB", "#BD99ED"),
        "primary_text": "#F6F0FF",
        "secondary_text": "#D7C4F5",
        "divider": "#7D699F",
        "app_background": "#181225",
    },
}


def gradients(palette):
    names = ("outer", "middle", "inner", "base")
    entries = []
    for name in names:
        start, end = palette[name]
        entries.append(
            f'<linearGradient id="{name}" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="{start}"/>'
            f'<stop offset="1" stop-color="{end}"/>'
            "</linearGradient>"
        )
    return "<defs>" + "".join(entries) + "</defs>"


def mark(transform=None):
    transformed = f' transform="{transform}"' if transform else ""
    return f"""<g{transformed} fill="none" stroke-linecap="round" stroke-linejoin="round">
  <path d="M17 28C42-3 86-3 111 28" stroke="url(#outer)" stroke-width="11"/>
  <path d="M32 44C48 23 80 23 96 44" stroke="url(#middle)" stroke-width="11"/>
  <path d="M49 57C56 47 72 47 79 57" stroke="url(#inner)" stroke-width="10"/>
  <path d="M18 60c13 11 28 18 46 18s33-7 46-18" stroke="url(#base)" stroke-width="11"/>
  <path d="M18 60v22c0 8 6 14 14 14h64c8 0 14-6 14-14V60" stroke="url(#base)" stroke-width="11"/>
</g>"""


def svg(view_box, label, content, palette):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}" '
        f'role="img" aria-label="{label}">\n'
        + gradients(palette)
        + "\n"
        + content
        + "\n</svg>\n"
    )


def vertical_zh(palette):
    return f"""{mark('translate(269 116) scale(3.8)')}
<text x="512" y="690" text-anchor="middle" fill="{palette['primary_text']}" font-family="PingFang SC, Noto Sans SC, sans-serif" font-size="200" font-weight="700" letter-spacing="8">映湾投屏</text>
"""


def vertical_en(palette):
    return f"""{mark('translate(269 116) scale(3.8)')}
<text x="512" y="750" text-anchor="middle" fill="{palette['primary_text']}" font-family="Avenir Next, Inter, sans-serif" font-size="112" font-weight="650" letter-spacing="3">CASTBAY</text>"""


def vertical_zh_tw(palette):
    return f"""{mark('translate(269 116) scale(3.8)')}
<text x="512" y="690" text-anchor="middle" fill="{palette['primary_text']}" font-family="PingFang TC, Noto Sans TC, sans-serif" font-size="200" font-weight="700" letter-spacing="8">映灣投屏</text>"""


def horizontal_en(palette):
    return f"""{mark('translate(10 14) scale(1.25)')}
<text x="177" y="105" fill="{palette['primary_text']}" font-family="Avenir Next, Inter, sans-serif" font-size="70" font-weight="650" letter-spacing="1">CASTBAY</text>"""


def horizontal_zh(palette):
    return f"""{mark('translate(10 14) scale(1.25)')}
<text x="177" y="105" fill="{palette['primary_text']}" font-family="PingFang SC, Noto Sans SC, sans-serif" font-size="48" font-weight="700" letter-spacing="1">映湾投屏</text>"""


def horizontal_zh_tw(palette):
    return f"""{mark('translate(10 14) scale(1.25)')}
<text x="177" y="105" fill="{palette['primary_text']}" font-family="PingFang TC, Noto Sans TC, sans-serif" font-size="48" font-weight="700" letter-spacing="1">映灣投屏</text>"""


def app_icon(palette):
    return (
        f'<rect width="1024" height="1024" rx="224" '
        f'fill="{palette["app_background"]}"/>\n'
        + mark("translate(128 220) scale(6)")
    )


LAYOUTS = {
    "symbol": ("0 -12 128 128", lambda palette: mark()),
    "vertical-zh": ("0 0 1024 1024", vertical_zh),
    "vertical-en": ("0 0 1024 1024", vertical_en),
    "vertical-zh-TW": ("0 0 1024 1024", vertical_zh_tw),
    "horizontal-zh": ("0 0 610 146", horizontal_zh),
    "horizontal-en": ("0 0 525 146", horizontal_en),
    "horizontal-zh-TW": ("0 0 610 146", horizontal_zh_tw),
    "app-icon": ("0 0 1024 1024", app_icon),
    "adaptive-foreground": ("0 0 1024 1024", lambda palette: mark("translate(128 220) scale(6)")),
}


def main():
    for theme, palette in PALETTES.items():
        for name, (view_box, layout) in LAYOUTS.items():
            if name == "symbol":
                brand_name = "CastBay symbol"
            elif "zh-TW" in name:
                brand_name = "映灣投屏"
            elif "zh" in name:
                brand_name = "映湾投屏"
            else:
                brand_name = "CastBay"
            label = f"{brand_name} {theme} logo"
            content = svg(view_box, label, layout(palette), palette)
            (OUT / f"{name}-{theme}.svg").write_text(content, encoding="utf-8")


if __name__ == "__main__":
    main()
