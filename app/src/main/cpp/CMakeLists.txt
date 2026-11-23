# =============================================================================================
#  AI Launcher - native inference library
#
#  libailauncher_llm.so = llama.cpp (pinned release) + a small C++ engine (llm_engine.cpp) + the
#  JNI bridge (llama_jni.cpp) used by com.ailauncher.data.llm.LlamaNative.
#
#  llama.cpp source resolution order:
#    1. -DLLAMA_CPP_DIR=<path>      (Gradle: llama.cpp.dir in local.properties)
#    2. ./llama.cpp                  (git submodule pinned to LLAMA_CPP_GIT_TAG)
#    3. download of LLAMA_CPP_GIT_TAG from GitHub (e.g. for "Download ZIP" checkouts)
# =============================================================================================
cmake_minimum_required(VERSION 3.24)

project(ailauncher_llm VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Keep in sync with the commit of the app/src/main/cpp/llama.cpp submodule.
set(LLAMA_CPP_GIT_TAG "b12651" CACHE STRING "llama.cpp release tag")
set(LLAMA_CPP_DIR "" CACHE PATH "Optional path to a llama.cpp source tree")

# ---------------------------------------------------------------------------------------------
#  llama.cpp / ggml configuration (must be set before the sources are added)
# ---------------------------------------------------------------------------------------------
set(BUILD_SHARED_LIBS     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)  # the bridge only uses the public C API
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)  # cross-compiling: never tune for the build host
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI     OFF CACHE BOOL "" FORCE)  # only accelerates Q4_0/Q8_0, not Q4_K_M
# ggml's own thread pool is used instead of OpenMP: no libomp.so to ship and no spinning worker
# threads left behind while the launcher sits idle on the home screen.
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Build every Android CPU variant (armv8.0 baseline … armv8.2 dotprod/fp16 … armv8.6 i8mm …
    # armv9 SVE2/SME) as a separate module. ggml scores them against the SoC at runtime and loads
    # the fastest supported one, so newer phones get i8mm/SVE kernels while older ones never hit an
    # illegal instruction inside the launcher process.
    set(GGML_BACKEND_DL       ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
elseif(ANDROID_ABI STREQUAL "x86_64")
    # x86_64 only targets the Android Emulator: a single CPU build using the host-class
    # instruction sets every emulator-capable machine provides.
    set(GGML_BACKEND_DL       OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
    set(GGML_SSE42 ON  CACHE BOOL "" FORCE)
    set(GGML_AVX   ON  CACHE BOOL "" FORCE)
    set(GGML_AVX2  ON  CACHE BOOL "" FORCE)
    set(GGML_FMA   ON  CACHE BOOL "" FORCE)
    set(GGML_F16C  ON  CACHE BOOL "" FORCE)
    set(GGML_BMI2  ON  CACHE BOOL "" FORCE)
elseif(DEFINED ANDROID_ABI)
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI} (expected arm64-v8a or x86_64)")
endif()

# ggml builds dynamically loadable backends as MODULE libraries, but the Android Gradle Plugin only
# packages SHARED_LIBRARY targets into the APK. Without this shim the CPU variants are compiled yet
# silently left out of the app, and ggml finds no CPU backend at runtime. Building them as SHARED
# keeps the exact same file names (libggml-cpu-<variant>.so) that ggml_backend_load_all_from_path()
# scans for.
function(add_library name)
    set(args ${ARGN})
    if(args AND name MATCHES "^ggml-cpu")
        list(GET args 0 lib_type)
        if(lib_type STREQUAL "MODULE")
            list(REMOVE_AT args 0)
            _add_library(${name} SHARED ${args})
            return()
        endif()
    endif()
    _add_library(${name} ${ARGN})
endfunction()

# ---------------------------------------------------------------------------------------------
#  Add llama.cpp
# ---------------------------------------------------------------------------------------------
if(NOT LLAMA_CPP_DIR AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")
endif()

if(LLAMA_CPP_DIR)
    if(NOT EXISTS "${LLAMA_CPP_DIR}/include/llama.h")
        message(FATAL_ERROR "LLAMA_CPP_DIR=${LLAMA_CPP_DIR} does not look like a llama.cpp checkout")
    endif()
    message(STATUS "llama.cpp: using sources at ${LLAMA_CPP_DIR}")
    add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")
else()
    message(STATUS "llama.cpp: submodule not initialised, downloading ${LLAMA_CPP_GIT_TAG}. "
                   "Run `git submodule update --init --depth 1` to build offline.")
    include(FetchContent)
    FetchContent_Declare(llama_cpp
        URL "https://github.com/ggml-org/llama.cpp/archive/refs/tags/${LLAMA_CPP_GIT_TAG}.tar.gz"
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
    FetchContent_MakeAvailable(llama_cpp)
endif()

# ---------------------------------------------------------------------------------------------
#  JNI bridge
# ---------------------------------------------------------------------------------------------
add_library(ailauncher_llm SHARED
    llm_engine.cpp
    llama_jni.cpp)

target_compile_options(ailauncher_llm PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -fvisibility=hidden -fvisibility-inlines-hidden)

target_link_libraries(ailauncher_llm PRIVATE
    llama
    ggml
    android
    log)

# Explicit 16 KB ELF alignment (Android 15+ devices with 16 KB pages). NDK r28+ already defaults to
# this; it is kept explicit so an older NDK cannot silently produce a non-compliant library.
target_link_options(ailauncher_llm PRIVATE "-Wl,-z,max-page-size=16384")
