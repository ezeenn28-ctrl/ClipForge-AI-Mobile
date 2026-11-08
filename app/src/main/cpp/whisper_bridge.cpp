#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_clipforge_mobile_WhisperNative_transcribeSegmentsNative(JNIEnv* env, jobject, jstring modelPath, jfloatArray audio) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    whisper_context* ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) return env->NewStringUTF("ERROR: Could not load Whisper model");
    jsize count = env->GetArrayLength(audio);
    std::vector<float> samples(static_cast<size_t>(count));
    env->GetFloatArrayRegion(audio, 0, count, samples.data());
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    // Short, word-boundary cues avoid hiding the tail of long spoken segments.
    params.token_timestamps = true;
    params.max_len = 42;
    params.split_on_word = true;
    params.translate = false;
    params.language = "auto";
    params.n_threads = 4;
    int result = whisper_full(ctx, params, samples.data(), count);
    std::string transcript;
    if (result == 0) {
        int n = whisper_full_n_segments(ctx);
        for (int i = 0; i < n; ++i) {
            transcript += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10);
            transcript += "\t";
            transcript += std::to_string(whisper_full_get_segment_t1(ctx, i) * 10);
            transcript += "\t";
            transcript += whisper_full_get_segment_text(ctx, i);
            transcript += "\n";
        }
    } else transcript = "ERROR: Whisper inference failed";
    whisper_free(ctx);
    return env->NewStringUTF(transcript.c_str());
}
