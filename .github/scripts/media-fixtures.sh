#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/androidTest/assets
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 8 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac app/src/androidTest/assets/landscape.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=180x320:rate=24' -f lavfi -i 'sine=frequency=660:sample_rate=44100' -t 8 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac app/src/androidTest/assets/portrait.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=880:sample_rate=44100' -t 3 -c:a pcm_s16le app/src/androidTest/assets/music.wav
