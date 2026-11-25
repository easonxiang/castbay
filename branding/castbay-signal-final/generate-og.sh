#!/usr/bin/env bash
# Regenerate the website's Open Graph image from the current hero and English-first wordmark.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

magick "$ROOT/web/public/assets/hero.jpg" -resize '1200x630^' -gravity center -extent 1200x630 "$TMP_DIR/background.png"
rsvg-convert -w 610 -h 215 "$ROOT/branding/castbay-signal-final/horizontal-en-dark.svg" > "$TMP_DIR/wordmark.png"
magick "$TMP_DIR/background.png" "$TMP_DIR/wordmark.png" -geometry +65+205 -composite -quality 92 "$ROOT/web/public/assets/og.jpg"
