#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/androidTest/assets
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 8 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac app/src/androidTest/assets/landscape.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=180x320:rate=24' -f lavfi -i 'sine=frequency=660:sample_rate=44100' -t 8 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac app/src/androidTest/assets/portrait.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=880:sample_rate=44100' -t 3 -c:a pcm_s16le app/src/androidTest/assets/music.wav


ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=black:size=180x320:rate=12' -f lavfi -i 'anullsrc=r=44100:cl=mono' -t 3 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac app/src/androidTest/assets/caption-canvas.mp4

# Small, synthetic-only fixtures for boundary transitions and long-source seeking.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=cyan:size=180x320:rate=30' -t 1 -c:v libx264 -pix_fmt yuv420p -preset ultrafast app/src/androidTest/assets/transition-cyan.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=red:size=180x320:rate=30' -t 1 -c:v libx264 -pix_fmt yuv420p -preset ultrafast app/src/androidTest/assets/transition-red.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=gray:size=16x16:rate=1' -f lavfi -i 'anullsrc=r=16000:cl=mono' -t 7200 -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac -b:a 8k app/src/androidTest/assets/two-hours.mp4

ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=1200:sample_rate=44100' -t 0.4 -c:a libmp3lame -b:a 128k app/src/androidTest/assets/sfx-import.mp3
