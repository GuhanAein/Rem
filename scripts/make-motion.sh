#!/usr/bin/env bash
# Builds docs/motion/showreel.mp4 (+ a small GIF) from frames recorded by MotionRecordTest.
set -euo pipefail
cd "$(dirname "$0")/.."
FRAMES=app/build/motion
OUT=docs/motion
mkdir -p "$OUT"
list=$(mktemp)
for scene in $(ls "$FRAMES" | grep -v mp4 | sort); do
  ffmpeg -y -loglevel error -framerate 30 -i "$FRAMES/$scene/f%04d.png" \
    -vf "scale=400:-2,format=yuv420p" -c:v libx264 -crf 20 "$FRAMES/$scene.mp4"
  echo "file '$PWD/$FRAMES/$scene.mp4'" >> "$list"
done
ffmpeg -y -loglevel error -f concat -safe 0 -i "$list" -c copy "$OUT/showreel.mp4"
ffmpeg -y -loglevel error -i "$OUT/showreel.mp4" \
  -vf "fps=15,scale=270:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer" "$OUT/showreel.gif"
rm -f "$list"
ls -la "$OUT"
