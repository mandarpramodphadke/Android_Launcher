// Minimal host stand-in for <android/log.h> so llama_jni.cpp can be built as a desktop JNI library
// (used by LlamaNativeHostTest to exercise the real JNI bindings on the JVM).
#pragma once
#include <stdarg.h>
#include <stdio.h>

enum { ANDROID_LOG_DEBUG = 3, ANDROID_LOG_INFO = 4, ANDROID_LOG_WARN = 5, ANDROID_LOG_ERROR = 6 };

static inline int __android_log_write(int prio, const char* tag, const char* text) {
    if (prio >= ANDROID_LOG_WARN) fprintf(stderr, "[%s] %s", tag, text);
    return 0;
}

static inline int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    if (prio < ANDROID_LOG_INFO) return 0;
    va_list args;
    va_start(args, fmt);
    fprintf(stderr, "[%s] ", tag);
    vfprintf(stderr, fmt, args);
    fputc('\n', stderr);
    va_end(args);
    return 0;
}
