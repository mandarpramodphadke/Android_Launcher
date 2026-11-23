# =============================================================================================
#  Desktop harness for app/src/main/cpp/llm_engine.cpp.
#
#  Runs the exact engine code that ships in the APK (prefix KV caching, grammar-constrained
#  sampling, UTF-8 streaming, cancellation) on a Linux/macOS host against a real GGUF file.
#
#    cmake -S tools/llm-host-harness -B build/host -G Ninja -DLLAMA_CPP_DIR=<llama.cpp checkout>
#    cmake --build build/host
#    build/host/llm_host_harness --model qwen3-1.7b-q4_k_m.gguf prompt.txt
# =============================================================================================
cmake_minimum_required(VERSION 3.24)
project(llm_host_harness LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(APP_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp")
set(LLAMA_CPP_DIR "${APP_CPP_DIR}/llama.cpp" CACHE PATH "Path to a llama.cpp checkout")

set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# The static llama/ggml libraries are also linked into the host JNI library below.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

add_executable(llm_host_harness main.cpp "${APP_CPP_DIR}/llm_engine.cpp")
target_include_directories(llm_host_harness PRIVATE "${APP_CPP_DIR}")
target_link_libraries(llm_host_harness PRIVATE llama ggml)

# ---------------------------------------------------------------------------------------------
#  Host build of the JNI bridge (libailauncher_llm.so) for LlamaNativeHostTest:
#    ./gradlew :app:testDebugUnitTest -Pailauncher.test.nativeDir=<build dir> -Pailauncher.test.model=<gguf>
# ---------------------------------------------------------------------------------------------
find_package(JNI)
if(JNI_FOUND OR JAVA_INCLUDE_PATH)
    add_library(ailauncher_llm SHARED "${APP_CPP_DIR}/llama_jni.cpp" "${APP_CPP_DIR}/llm_engine.cpp")
    target_include_directories(ailauncher_llm PRIVATE
        "${APP_CPP_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/stubs"
        ${JAVA_INCLUDE_PATH}
        ${JAVA_INCLUDE_PATH2})
    target_link_libraries(ailauncher_llm PRIVATE llama ggml)
else()
    message(STATUS "JDK headers not found: skipping the host JNI library")
endif()
