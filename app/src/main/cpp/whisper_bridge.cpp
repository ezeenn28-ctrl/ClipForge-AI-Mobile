#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "whisper.h"

namespace {
std::mutex model_mutex;
whisper_context* cached_context = nullptr;
std::string cached_path;

struct ProgressTarget {
    JavaVM* vm;
    jobject listener;
    jmethodID method;
};

void report_progress(whisper_context*, whisper_state*, int progress, void* data) {
    auto* target = static_cast<ProgressTarget*>(data);
    JNIEnv* env = nullptr;
    bool attached = false;
    const auto status = target->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
#ifdef __ANDROID__
        if (target->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
#else
        if (target->vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
#endif
        attached = true;
    } else if (status != JNI_OK) return;
    env->CallVoidMethod(target->listener, target->method, static_cast<jint>(progress));
    // A UI progress notification must never tear down the native inference loop.
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) target->vm->DetachCurrentThread();
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_clipforge_mobile_WhisperNative_releaseModelNative(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(model_mutex);
    if (cached_context) whisper_free(cached_context);
    cached_context = nullptr;
    cached_path.clear();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_clipforge_mobile_WhisperNative_transcribeSegmentsNative(JNIEnv* env, jobject, jstring modelPath, jfloatArray audio, jobject listener) {
    std::lock_guard<std::mutex> guard(model_mutex);
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return nullptr;
    std::string requested_path(path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!cached_context || cached_path != requested_path) {
        if (cached_context) whisper_free(cached_context);
        cached_context = nullptr;
        cached_path.clear();
        whisper_context_params cp = whisper_context_default_params();
        cp.use_gpu = false;
        cached_context = whisper_init_from_file_with_params(requested_path.c_str(), cp);
        if (cached_context) cached_path = requested_path;
    }
    whisper_context* ctx = cached_context;
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
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    jclass listener_class = env->GetObjectClass(listener);
    jmethodID method = env->GetMethodID(listener_class, "onProgress", "(I)V");
    env->DeleteLocalRef(listener_class);
    if (!method) return nullptr;
    ProgressTarget target{vm, env->NewGlobalRef(listener), method};
    params.progress_callback = report_progress;
    params.progress_callback_user_data = &target;
    int result = whisper_full(ctx, params, samples.data(), count);
    env->DeleteGlobalRef(target.listener);
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
    return env->NewStringUTF(transcript.c_str());
}
