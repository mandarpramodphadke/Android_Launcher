// =============================================================================================
//  llm_engine.h - platform-neutral wrapper around the llama.cpp C API.
//
//  Kept free of JNI so the exact same code can be exercised on a desktop host
//  (tools/llm-host-harness) against a real GGUF before it ships inside the launcher.
//
//  Threading contract
//    * One LlmEngine instance is driven by a single thread (Load/Generate/CountTokens/...).
//    * LlmEngine::RequestStop() may be called from any thread; it aborts both prompt evaluation
//      (via the llama abort callback) and token generation.
// =============================================================================================
#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "llama.h"

namespace ailauncher {

struct LoadParams {
    std::string model_path;
    int32_t n_ctx = 2048;
    int32_t n_threads = 4;
    int32_t n_batch = 512;     // logical batch (prompt tokens per llama_process call)
    int32_t n_ubatch = 256;    // physical batch; smaller = smaller compute buffer on mobile
    bool use_mmap = true;      // weights stay file-backed (reclaimable) instead of anonymous RAM
    bool quantize_kv_cache = true;  // q8_0 K/V halves KV-cache RAM; falls back to f16 if unsupported
};

struct SamplingParams {
    float temperature = 0.7f;
    float top_p = 0.8f;
    int32_t top_k = 20;
    float min_p = 0.0f;
    float presence_penalty = 0.0f;
    float repeat_penalty = 1.0f;
    int32_t penalty_last_n = 64;
    int32_t max_tokens = 512;
    uint32_t seed = LLAMA_DEFAULT_SEED;
    std::string grammar;  // optional GBNF grammar (start rule "root"); empty = unconstrained
};

enum class StopReason : int32_t {
    kEndOfGeneration = 0,  // model emitted an end-of-generation token (<|im_end|>, ...)
    kMaxTokens = 1,        // SamplingParams::max_tokens reached
    kCancelled = 2,        // RequestStop() or the token sink returned false
    kContextFull = 3,      // prompt or generation hit n_ctx
    kError = 4,
};

struct GenerationResult {
    StopReason stop_reason = StopReason::kEndOfGeneration;
    int32_t prompt_tokens = 0;
    int32_t cached_tokens = 0;      // prompt tokens reused from the KV cache (prefix caching)
    int32_t generated_tokens = 0;
    int64_t prompt_eval_us = 0;
    int64_t generation_us = 0;
    std::string error;
};

// Receives text in complete UTF-8 sequences only. Return false to stop generation.
using TokenSink = std::function<bool(const char* utf8, size_t length)>;
// Model loading progress in [0, 1]. Return false to abort loading.
using ProgressSink = std::function<bool(float progress)>;

class LlmEngine {
public:
    // One-time process initialisation. With GGML_BACKEND_DL builds, `backend_dir` is scanned for
    // the CPU backend variants and the best one for this CPU is loaded. May be empty.
    static void InitBackends(const std::string& backend_dir);
    static std::string SystemInfo();
    // Thread-safe: cancels the generation / prompt evaluation running in this process, if any.
    static void RequestStop();

    static std::unique_ptr<LlmEngine> Load(const LoadParams& params,
                                           const ProgressSink& progress,
                                           std::string* error);

    ~LlmEngine();
    LlmEngine(const LlmEngine&) = delete;
    LlmEngine& operator=(const LlmEngine&) = delete;

    // Evaluates `prompt` (reusing the longest common token prefix already in the KV cache) and
    // streams the completion to `sink`.
    GenerationResult Generate(const std::string& prompt,
                              const SamplingParams& params,
                              const TokenSink& sink);

    int32_t CountTokens(const std::string& text) const;
    void SetThreads(int32_t n_threads);
    void ClearCache();
    int32_t ContextSize() const;
    // Small JSON object describing the loaded model and context.
    std::string Describe() const;

private:
    LlmEngine() = default;

    std::vector<llama_token> Tokenize(const std::string& text, bool add_special) const;
    void AppendPiece(llama_token token, std::string* out) const;
    bool DecodePrompt(const std::vector<llama_token>& tokens, size_t start, GenerationResult* result);
    bool DecodeToken(llama_token token, GenerationResult* result);
    void DropUncommittedCache();

    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    const llama_vocab* vocab_ = nullptr;
    llama_batch_ext* batch_ = nullptr;
    int32_t n_batch_ = 0;
    bool kv_quantized_ = false;
    // Tokens currently materialised in the KV cache for sequence 0, in position order.
    std::vector<llama_token> kv_tokens_;
};

// Length of the longest prefix of `bytes` that does not end inside a UTF-8 multi-byte sequence.
size_t Utf8CompletePrefixLength(const std::string& bytes);

}  // namespace ailauncher
