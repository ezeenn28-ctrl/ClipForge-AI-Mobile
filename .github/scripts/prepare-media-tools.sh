#!/usr/bin/env bash
set -euo pipefail
# CI fixtures only: avoid slow/unreachable Ubuntu package mirrors.
tools="$RUNNER_TEMP/clipforge-media-tools"
mkdir -p "$tools"
if ! command -v ffmpeg >/dev/null || ! command -v ffprobe >/dev/null; then
  curl --fail --location --retry 2 --connect-timeout 20 --max-time 180 \
    https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/autobuild-2026-11-11-12-39/ffmpeg-n8.1.3-35-gf1acb0a266-linux64-gpl-8.1.tar.xz -o "$tools/ffmpeg.tar.xz"
  echo "32430dc6642d835511470728811e1555ec7ec09d4d27638b059f0e577a98d4e7  $tools/ffmpeg.tar.xz" | sha256sum --check
  tar -xJf "$tools/ffmpeg.tar.xz" -C "$tools"
  binary="$(find "$tools" -type f -name ffmpeg | head -1)"
  probe="$(find "$tools" -type f -name ffprobe | head -1)"
  test -n "$binary" && test -n "$probe"
  mkdir -p "$tools/bin"
  cp "$binary" "$probe" "$tools/bin/"
  echo "$tools/bin" >> "$GITHUB_PATH"
  export PATH="$tools/bin:$PATH"
fi
ffmpeg -version | head -3
ffprobe -version | head -3
if ! python3 -c 'from PIL import Image'; then
  python3 -m venv "$tools/python"
  "$tools/python/bin/python" -m pip install --disable-pip-version-check --timeout 30 pillow==11.3.0
  echo "$tools/python/bin" >> "$GITHUB_PATH"
fi

