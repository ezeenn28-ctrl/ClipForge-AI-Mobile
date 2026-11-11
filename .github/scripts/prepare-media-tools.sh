#!/usr/bin/env bash
set -euo pipefail
# CI fixtures only: avoid slow/unreachable Ubuntu package mirrors.
tools="$RUNNER_TEMP/clipforge-media-tools"
mkdir -p "$tools"
if ! command -v ffmpeg >/dev/null || ! command -v ffprobe >/dev/null; then
  curl --fail --location --retry 2 --connect-timeout 20 --max-time 180 \
    https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz -o "$tools/ffmpeg.tar.xz"
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
