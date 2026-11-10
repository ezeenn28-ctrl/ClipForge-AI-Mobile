#!/usr/bin/env bash
set -euo pipefail
qa_sdk="${ANDROID_HOME:?Android SDK required}"
qa_platform=$(find "$qa_sdk/platforms" -name android.jar | sort -V | tail -n 1)
qa_d8=$(find "$qa_sdk/build-tools" -name d8 -type f | sort -V | tail -n 1)
test -n "$qa_platform"
test -n "$qa_d8"
mkdir -p build/ci-touch/classes build/ci-touch/dex
javac -source 8 -target 8 -classpath "$qa_platform" -d build/ci-touch/classes .github/tests/ClipForgeTouch.java
"$qa_d8" --lib "$qa_platform" --min-api 26 --output build/ci-touch/dex build/ci-touch/classes/ClipForgeTouch.class
jar cf build/ci-touch/clipforge-touch.jar -C build/ci-touch/dex classes.dex
adb push build/ci-touch/clipforge-touch.jar /data/local/tmp/clipforge-touch.jar
