// =============================================================================================
//  llama_jni.cpp - JNI bridge between com.ailauncher.data.llm.LlamaNative (Kotlin) and the
//  platform-neutral LlmEngine (llm_engine.cpp).
//
//  Exposed to Kotlin:
//    nativeInit(nativeLibDir)                 one-time backend discovery + Android logging
//    loadModel(...)          -> handle        mmap the GGUF and create the inference context
//    generateStream(handle, prompt, ..., cb)  blocking; streams UTF-8 chunks to cb.onToken()
//    stopGeneration()                         thread-safe cancellation (prompt eval + decoding)
//    unloadModel(handle)                      frees the model, context and KV cache
//    countTokens / setThreads / clearCache / describe / systemInfo / enableGpu
//
//  Strings that may contain arbitrary Unicode (prompts, grammars) cross the boundary as UTF-8
//  byte arrays: JNI's GetStringUTFChars produces *modified* UTF-8, which mangles emoji and other
//  supplementary characters.
// =============================================================================================
#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <exception>
#include <memory>
#include <string>

#include "llm_engine.h"

using ailauncher::GenerationResult;
using ailauncher::LlmEngine;
using ailauncher::LoadParams;
using ailauncher::ProgressSink;
using ailauncher::SamplingParams;
using ailauncher::TokenSink;

#define JNI_FN(name) Java_com_ailauncher_data_llm_LlamaNative_##name

namespace {

constexpr const char* kTag = "LlamaNative";
JavaVM* g_vm = nullptr;

void AndroidLogCallback(ggml_log_level level, const char* text, void* /*user_data*/) {
    int priority;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN: priority = ANDROID_LOG_WARN; break;
        case GGML_LOG_LEVEL_INFO: priority = ANDROID_LOG_INFO; break;
        default: priority = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(priority, "llama.cpp", text);
}

std::string JStringToStd(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string JBytesToStd(JNIEnv* env, jbyteArray value) {
    if (value == nullptr) return {};
    const jsize n = env->GetArrayLength(value);
    std::string out(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(value, 0, n, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

void ThrowJava(JNIEnv* env, const char* class_name, const std::string& message) {
    if (env->ExceptionCheck()) return;  // never mask an exception raised by a Kotlin callback
    jclass cls = env->FindClass(class_name);
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

void ThrowIllegalState(JNIEnv* env, const std::string& message) {
    ThrowJava(env, "java/lang/IllegalStateException", message);
}

LlmEngine* FromHandle(JNIEnv* env, jlong handle) {
    auto* engine = reinterpret_cast<LlmEngine*>(handle);
    if (engine == nullptr) ThrowIllegalState(env, "Model is not loaded");
    return engine;
}

// Resolves the JNIEnv of the calling thread (llama.cpp invokes callbacks on the loading thread,
// but this keeps the bridge correct even if that ever changes).
JNIEnv* CurrentEnv(bool* attached) {
    *attached = false;
    if (g_vm == nullptr) return nullptr;
    JNIEnv* env = nullptr;
    const jint rc = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        // The NDK's jni.h declares JNIEnv**, the JDK's (used by the host test build) void**.
#if defined(__ANDROID__)
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
#else
        if (g_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return nullptr;
#endif
        *attached = true;
    }
    return env;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_FN(nativeInit)(JNIEnv* env, jclass /*clazz*/, jstring native_lib_dir) {
    try {
        llama_log_set(AndroidLogCallback, nullptr);
        LlmEngine::InitBackends(JStringToStd(env, native_lib_dir));
        __android_log_print(ANDROID_LOG_INFO, kTag, "llama.cpp backends initialised");
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("Backend initialisation failed: ") + e.what());
    }
}

extern "C" JNIEXPORT jstring JNICALL
JNI_FN(enableGpu)(JNIEnv* env, jclass /*clazz*/) {
    try {
        return env->NewStringUTF(LlmEngine::EnableGpuBackend().c_str());
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "GPU backend unavailable: %s", e.what());
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jstring JNICALL
JNI_FN(systemInfo)(JNIEnv* env, jclass /*clazz*/) {
    try {
        return env->NewStringUTF(LlmEngine::SystemInfo().c_str());
    } catch (const std::exception& e) {
        return env->NewStringUTF(e.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
JNI_FN(loadModel)(JNIEnv* env, jclass /*clazz*/, jstring model_path, jint n_ctx, jint n_threads,
                  jint n_batch, jboolean use_mmap, jboolean quantize_kv_cache, jint n_gpu_layers,
                  jobject progress_callback) {
    try {
        LoadParams params;
        params.model_path = JStringToStd(env, model_path);
        params.n_ctx = n_ctx;
        params.n_threads = n_threads;
        params.n_batch = n_batch;
        params.n_ubatch = std::min<int32_t>(256, n_batch);
        params.use_mmap = use_mmap == JNI_TRUE;
        params.quantize_kv_cache = quantize_kv_cache == JNI_TRUE;
        params.n_gpu_layers = std::max<jint>(0, n_gpu_layers);

        ProgressSink sink;
        jobject callback_ref = nullptr;
        jmethodID on_progress = nullptr;
        if (progress_callback != nullptr) {
            callback_ref = env->NewGlobalRef(progress_callback);
            jclass cls = env->GetObjectClass(progress_callback);
            on_progress = env->GetMethodID(cls, "onProgress", "(F)Z");
            env->DeleteLocalRef(cls);
            if (on_progress == nullptr) {
                env->DeleteGlobalRef(callback_ref);
                return 0;  // NoSuchMethodError pending
            }
            sink = [callback_ref, on_progress](float progress) -> bool {
                bool attached = false;
                JNIEnv* cb_env = CurrentEnv(&attached);
                if (cb_env == nullptr) return true;
                const jboolean keep = cb_env->CallBooleanMethod(callback_ref, on_progress, progress);
                const bool failed = cb_env->ExceptionCheck();
                if (attached) {
                    if (failed) cb_env->ExceptionClear();  // cannot propagate from a foreign thread
                    g_vm->DetachCurrentThread();
                }
                return !failed && keep == JNI_TRUE;
            };
        }

        std::string error;
        std::unique_ptr<LlmEngine> engine = LlmEngine::Load(params, sink, &error);
        if (callback_ref != nullptr) env->DeleteGlobalRef(callback_ref);
        if (!engine) {
            ThrowIllegalState(env, error.empty() ? "Failed to load model" : error);
            return 0;
        }
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("Failed to load model: ") + e.what());
        return 0;
    }
}

// Returns long[6] = {stopReason, promptTokens, cachedTokens, generatedTokens, promptEvalUs,
// generationUs}. Throws IllegalStateException when generation fails with an error.
extern "C" JNIEXPORT jlongArray JNICALL
JNI_FN(generateStream)(JNIEnv* env, jclass /*clazz*/, jlong handle, jbyteArray prompt_utf8,
                       jfloat temperature, jfloat top_p, jint top_k, jfloat min_p,
                       jfloat presence_penalty, jfloat repeat_penalty, jint max_tokens, jint seed,
                       jbyteArray grammar_utf8, jobject token_callback) {
    LlmEngine* engine = FromHandle(env, handle);
    if (engine == nullptr) return nullptr;
    try {
        SamplingParams params;
        params.temperature = temperature;
        params.top_p = top_p;
        params.top_k = top_k;
        params.min_p = min_p;
        params.presence_penalty = presence_penalty;
        params.repeat_penalty = repeat_penalty;
        params.max_tokens = max_tokens;
        params.seed = seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed);
        params.grammar = JBytesToStd(env, grammar_utf8);

        jclass cls = env->GetObjectClass(token_callback);
        jmethodID on_token = env->GetMethodID(cls, "onToken", "([B)Z");
        env->DeleteLocalRef(cls);
        if (on_token == nullptr) return nullptr;  // NoSuchMethodError pending

        // Generation runs synchronously on the calling (inference) thread, so `env` is valid.
        TokenSink sink = [env, token_callback, on_token](const char* data, size_t length) -> bool {
            jbyteArray chunk = env->NewByteArray(static_cast<jsize>(length));
            if (chunk == nullptr) return false;  // OutOfMemoryError pending
            env->SetByteArrayRegion(chunk, 0, static_cast<jsize>(length),
                                    reinterpret_cast<const jbyte*>(data));
            const jboolean keep = env->CallBooleanMethod(token_callback, on_token, chunk);
            env->DeleteLocalRef(chunk);  // long generations would otherwise exhaust local refs
            if (env->ExceptionCheck()) return false;
            return keep == JNI_TRUE;
        };

        const GenerationResult result = engine->Generate(JBytesToStd(env, prompt_utf8), params, sink);
        if (env->ExceptionCheck()) return nullptr;  // surface the callback's exception as-is
        if (result.stop_reason == ailauncher::StopReason::kError) {
            ThrowIllegalState(env, result.error.empty() ? "Generation failed" : result.error);
            return nullptr;
        }

        const jlong values[6] = {
            static_cast<jlong>(result.stop_reason),
            result.prompt_tokens,
            result.cached_tokens,
            result.generated_tokens,
            result.prompt_eval_us,
            result.generation_us,
        };
        jlongArray out = env->NewLongArray(6);
        if (out != nullptr) env->SetLongArrayRegion(out, 0, 6, values);
        return out;
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("Generation failed: ") + e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
JNI_FN(stopGeneration)(JNIEnv* /*env*/, jclass /*clazz*/) {
    LlmEngine::RequestStop();
}

extern "C" JNIEXPORT void JNICALL
JNI_FN(unloadModel)(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    delete reinterpret_cast<LlmEngine*>(handle);
}

extern "C" JNIEXPORT jint JNICALL
JNI_FN(countTokens)(JNIEnv* env, jclass /*clazz*/, jlong handle, jbyteArray text_utf8) {
    LlmEngine* engine = FromHandle(env, handle);
    if (engine == nullptr) return -1;
    return engine->CountTokens(JBytesToStd(env, text_utf8));
}

extern "C" JNIEXPORT void JNICALL
JNI_FN(setThreads)(JNIEnv* env, jclass /*clazz*/, jlong handle, jint n_threads) {
    LlmEngine* engine = FromHandle(env, handle);
    if (engine != nullptr) engine->SetThreads(n_threads);
}

extern "C" JNIEXPORT void JNICALL
JNI_FN(clearCache)(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    LlmEngine* engine = FromHandle(env, handle);
    if (engine != nullptr) engine->ClearCache();
}

extern "C" JNIEXPORT jstring JNICALL
JNI_FN(describe)(JNIEnv* env, jclass /*clazz*/, jlong handle) {
    LlmEngine* engine = FromHandle(env, handle);
    if (engine == nullptr) return nullptr;
    try {
        return env->NewStringUTF(engine->Describe().c_str());
    } catch (const std::exception& e) {
        ThrowIllegalState(env, e.what());
        return nullptr;
    }
}
