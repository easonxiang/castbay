# CastBay / 映湾投屏 brand assets

The CastBay mark: an open C that is both a sheltered bay and a signal arc, a B whose two bowls are boats entering the bay, and two ripples of water.

| File | Use |
| --- | --- |
| `cb-monogram.svg` / `.png` | The mark on light backgrounds |
| `cb-monogram-dark.svg` / `.png` | The mark on dark backgrounds (the app and the website) |
| `cb-monogram-64px.png` | Small-size check |
| `cb-wordmark-inline-dark` / `-light` | Mark, CastBay and 映湾投屏 on one line (README header, social image) |
| `cb-wordmark-en-first-dark` | CastBay above 映湾投屏 (the TV banner for English) |
| `cb-wordmark-zh-first-dark` | 映湾投屏 above castbay (the TV banner for Chinese) |

The SVGs are the masters; the PNGs are 1024 px (mark) or 1200 px wide (wordmarks). The wordmark SVGs set their names in Avenir Next and PingFang SC, so they render as designed only where those fonts are installed; use the PNGs elsewhere.

Colors: harbor `#6750A4` (dark `#A991FF`), boats `#C7B8FF` (dark `#E2D8FF`), water `#9D85E8` (dark `#B59CFF`), on the app's night `#0B0910` to `#2A2040`.

## Where they are used

- **App**: `app/src/main/res/drawable/ic_castbay_mark.xml` (home and About screens), the adaptive launcher icon (`ic_launcher_foreground.xml` on `ic_launcher_background.xml`), the notification icon (`ic_notification.xml`), and the Android TV banners in `drawable-xhdpi`, `drawable-xxhdpi` and their `-zh` variants.
- **Website**: `web/public/assets/logo.svg` (header and favicon), `favicon-32.png`, `apple-touch-icon.png` and the social preview `og.jpg`.
- **README**: the header wordmark.

The vector drawables copy the mark's SVG paths; if the mark changes, update them and regenerate the PNGs (for example with `rsvg-convert` and ImageMagick).
