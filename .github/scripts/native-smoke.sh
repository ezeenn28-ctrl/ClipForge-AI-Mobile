#!/usr/bin/env bash
set -euo pipefail
MODEL_DIR=".ci-models"
mkdir -p "$MODEL_DIR"
MODEL="$MODEL_DIR/ggml-tiny.bin"
if [ ! -s "$MODEL" ]; then
  curl --fail --location --retry 3 --output "$MODEL" 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin'
fi
printf '%s  %s\n' 'be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21' "$MODEL" | sha256sum --check
cmake -S app/src/main/cpp -B build/native-smoke -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF
cmake --build build/native-smoke --target clipforge_whisper --parallel 2
mkdir -p build/native-smoke/classes
javac -d build/native-smoke/classes .github/tests/com/clipforge/mobile/WhisperNative.java
NATIVE_LIBRARY_PATH="$(find "$PWD/build/native-smoke" -name '*.so' -printf '%h\n' | sort -u | paste -sd:)"
LD_LIBRARY_PATH="$NATIVE_LIBRARY_PATH${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" java -Xcheck:jni -Djava.library.path="$NATIVE_LIBRARY_PATH" -cp build/native-smoke/classes com.clipforge.mobile.WhisperNative "$MODEL" app/src/main/cpp/whisper.cpp/samples/jfk.wav "${CLIPFORGE_NATIVE_STRESS_WINDOWS:-0}"
