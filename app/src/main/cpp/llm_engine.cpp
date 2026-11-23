// =============================================================================================
//  llm_engine.cpp - see llm_engine.h
// =============================================================================================
#include "llm_engine.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <exception>
#include <mutex>
#include <sstream>
#include <stdexcept>

#include "ggml-backend.h"

namespace ailauncher {
namespace {

std::atomic<bool> g_stop_requested{false};

// llama.cpp polls this between graph nodes, so long prompt evaluations stop promptly too.
bool AbortCallback(void* /*user_data*/) {
    return g_stop_requested.load(std::memory_order_relaxed);
}

std::string JsonEscape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

// ---------------------------------------------------------------------------------------------
//  Sampler: llama_sampler chain + optional grammar, using rejection sampling for the grammar so
//  the expensive grammar check over the full 150k vocabulary only runs when the unconstrained
//  choice would violate it (the same strategy as llama.cpp's common_sampler).
// ---------------------------------------------------------------------------------------------
class Sampler {
public:
    Sampler(const llama_vocab* vocab, const SamplingParams& p)
        : vocab_(vocab), n_vocab_(llama_vocab_n_tokens(vocab)) {
        chain_ = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (p.top_k > 0) {
            llama_sampler_chain_add(chain_, llama_sampler_init_top_k(p.top_k));
        }
        // Penalties run after top-k: scanning the full vocabulary for repeats is slow.
        if (p.presence_penalty != 0.0f || p.repeat_penalty != 1.0f) {
            llama_sampler_chain_add(chain_, llama_sampler_init_penalties(
                n_vocab_, p.penalty_last_n, p.repeat_penalty, 0.0f, p.presence_penalty));
        }
        if (p.top_p > 0.0f && p.top_p < 1.0f) {
            llama_sampler_chain_add(chain_, llama_sampler_init_top_p(p.top_p, 1));
        }
        if (p.min_p > 0.0f) {
            llama_sampler_chain_add(chain_, llama_sampler_init_min_p(p.min_p, 1));
        }
        if (p.temperature > 0.0f) {
            llama_sampler_chain_add(chain_, llama_sampler_init_temp(p.temperature));
            llama_sampler_chain_add(chain_, llama_sampler_init_dist(p.seed));
        } else {
            llama_sampler_chain_add(chain_, llama_sampler_init_greedy());
        }
        if (!p.grammar.empty()) {
            grammar_ = llama_sampler_init_grammar(vocab, p.grammar.c_str(), "root");
            grammar_failed_ = grammar_ == nullptr;
        }
    }

    ~Sampler() {
        if (grammar_) llama_sampler_free(grammar_);
        if (chain_) llama_sampler_free(chain_);
    }

    Sampler(const Sampler&) = delete;
    Sampler& operator=(const Sampler&) = delete;

    bool grammar_failed() const { return grammar_failed_; }

    // Returns LLAMA_TOKEN_NULL if no token could be selected.
    llama_token Sample(llama_context* ctx) {
        const float* logits = llama_get_logits_ith(ctx, -1);
        if (logits == nullptr) return LLAMA_TOKEN_NULL;

        Fill(logits);
        llama_token_data_array cur = {candidates_.data(), candidates_.size(), -1, false};
        llama_sampler_apply(chain_, &cur);
        llama_token id = Selected(cur);

        if (grammar_ != nullptr && id != LLAMA_TOKEN_NULL) {
            llama_token_data single = {id, 1.0f, 0.0f};
            llama_token_data_array single_arr = {&single, 1, -1, false};
            llama_sampler_apply(grammar_, &single_arr);
            const bool allowed = !(std::isinf(single_arr.data[0].logit) && single_arr.data[0].logit < 0);
            if (!allowed) {
                // Slow path: constrain the whole distribution, then sample again.
                Fill(logits);
                cur = {candidates_.data(), candidates_.size(), -1, false};
                llama_sampler_apply(grammar_, &cur);
                llama_sampler_apply(chain_, &cur);
                id = Selected(cur);
            }
        }
        if (id == LLAMA_TOKEN_NULL) return id;

        // The grammar only admits end-of-generation once it is complete; generation stops at EOG,
        // so it is never fed to the grammar (which would abort on an incomplete grammar).
        if (grammar_ != nullptr && !llama_vocab_is_eog(vocab_, id)) {
            llama_sampler_accept(grammar_, id);
        }
        llama_sampler_accept(chain_, id);
        return id;
    }

private:
    void Fill(const float* logits) {
        candidates_.resize(static_cast<size_t>(n_vocab_));
        for (llama_token i = 0; i < n_vocab_; ++i) {
            candidates_[static_cast<size_t>(i)] = {i, logits[i], 0.0f};
        }
    }

    static llama_token Selected(const llama_token_data_array& cur) {
        if (cur.selected < 0 || static_cast<size_t>(cur.selected) >= cur.size) return LLAMA_TOKEN_NULL;
        const llama_token_data& d = cur.data[cur.selected];
        if (std::isinf(d.logit) && d.logit < 0) return LLAMA_TOKEN_NULL;
        return d.id;
    }

    const llama_vocab* vocab_;
    const int32_t n_vocab_;
    llama_sampler* chain_ = nullptr;
    llama_sampler* grammar_ = nullptr;
    bool grammar_failed_ = false;
    std::vector<llama_token_data> candidates_;
};

}  // namespace

// =============================================================================================
//  UTF-8
// =============================================================================================
size_t Utf8CompletePrefixLength(const std::string& bytes) {
    const size_t n = bytes.size();
    // Walk back over at most 3 continuation bytes to find the lead byte of the last sequence.
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const auto c = static_cast<unsigned char>(bytes[n - back]);
        if ((c & 0xC0) == 0x80) continue;  // continuation byte
        size_t needed = 1;
        if ((c & 0xE0) == 0xC0) needed = 2;
        else if ((c & 0xF0) == 0xE0) needed = 3;
        else if ((c & 0xF8) == 0xF0) needed = 4;
        return back >= needed ? n : n - back;
    }
    // Only continuation bytes (malformed): flush as-is, the Kotlin decoder substitutes U+FFFD.
    return n;
}

// =============================================================================================
//  Process-wide
// =============================================================================================
void LlmEngine::InitBackends(const std::string& backend_dir) {
    static std::once_flag once;
    std::call_once(once, [&backend_dir] {
        if (!backend_dir.empty()) {
            ggml_backend_load_all_from_path(backend_dir.c_str());
        } else {
            ggml_backend_load_all();
        }
        llama_backend_init();
    });
}

std::string LlmEngine::SystemInfo() {
    std::string info = llama_print_system_info();
    std::string devices;
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        if (!devices.empty()) devices += ", ";
        devices += ggml_backend_dev_name(dev);
        devices += " (";
        devices += ggml_backend_dev_description(dev);
        devices += ")";
    }
    return info + "\nDevices: " + devices;
}

void LlmEngine::RequestStop() {
    g_stop_requested.store(true, std::memory_order_relaxed);
}

// =============================================================================================
//  Lifecycle
// =============================================================================================
std::unique_ptr<LlmEngine> LlmEngine::Load(const LoadParams& params,
                                           const ProgressSink& progress,
                                           std::string* error) {
    g_stop_requested.store(false, std::memory_order_relaxed);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;  // CPU inference; ggml selects the best CPU variant at runtime
    mp.load_mode = params.use_mmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;
    if (progress) {
        mp.progress_callback = [](float value, void* user_data) -> bool {
            return (*static_cast<const ProgressSink*>(user_data))(value);
        };
        mp.progress_callback_user_data = const_cast<ProgressSink*>(&progress);
    }

    llama_model* model = llama_model_load_from_file(params.model_path.c_str(), mp);
    if (model == nullptr) {
        if (error) *error = "Failed to load model from " + params.model_path;
        return nullptr;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(std::max(256, params.n_ctx));
    cp.n_batch = static_cast<uint32_t>(std::max(32, params.n_batch));
    cp.n_ubatch = static_cast<uint32_t>(std::max(32, std::min(params.n_ubatch, params.n_batch)));
    cp.n_seq_max = 1;
    cp.n_threads = std::max(1, params.n_threads);
    cp.n_threads_batch = cp.n_threads;
    cp.abort_callback = AbortCallback;
    cp.abort_callback_data = nullptr;
    cp.no_perf = true;

    bool kv_quantized = false;
    llama_context* ctx = nullptr;
    if (params.quantize_kv_cache) {
        // A quantised V cache requires flash attention.
        llama_context_params qp = cp;
        qp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        qp.type_k = GGML_TYPE_Q8_0;
        qp.type_v = GGML_TYPE_Q8_0;
        ctx = llama_init_from_model(model, qp);
        kv_quantized = ctx != nullptr;
    }
    if (ctx == nullptr) {
        ctx = llama_init_from_model(model, cp);
    }
    if (ctx == nullptr) {
        llama_model_free(model);
        if (error) *error = "Failed to create inference context (n_ctx=" + std::to_string(cp.n_ctx) + ")";
        return nullptr;
    }

    std::unique_ptr<LlmEngine> engine(new LlmEngine());
    engine->model_ = model;
    engine->ctx_ = ctx;
    engine->vocab_ = llama_model_get_vocab(model);
    engine->batch_ = llama_batch_ext_init(ctx);
    engine->n_batch_ = static_cast<int32_t>(llama_n_batch(ctx));
    engine->kv_quantized_ = kv_quantized;
    if (engine->batch_ == nullptr) {
        if (error) *error = "Failed to allocate batch";
        return nullptr;
    }
    return engine;
}

LlmEngine::~LlmEngine() {
    if (batch_ != nullptr) llama_batch_ext_free(batch_);
    if (ctx_ != nullptr) llama_free(ctx_);
    if (model_ != nullptr) llama_model_free(model_);
}

// =============================================================================================
//  Generation
// =============================================================================================
GenerationResult LlmEngine::Generate(const std::string& prompt,
                                     const SamplingParams& params,
                                     const TokenSink& sink) {
    GenerationResult result;
    g_stop_requested.store(false, std::memory_order_relaxed);
    const int64_t t_start = llama_time_us();

    try {
        const std::vector<llama_token> tokens = Tokenize(prompt, /*add_special=*/true);
        result.prompt_tokens = static_cast<int32_t>(tokens.size());
        if (tokens.empty()) {
            result.stop_reason = StopReason::kError;
            result.error = "Prompt is empty";
            return result;
        }

        const auto n_ctx = static_cast<int32_t>(llama_n_ctx(ctx_));
        if (result.prompt_tokens >= n_ctx - 8) {
            result.stop_reason = StopReason::kContextFull;
            result.error = "Prompt has " + std::to_string(result.prompt_tokens) +
                           " tokens but the context window is " + std::to_string(n_ctx);
            return result;
        }

        // ---- Prefix caching: keep the longest common prefix that is already in the KV cache ---
        size_t n_common = 0;
        while (n_common < kv_tokens_.size() && n_common < tokens.size() &&
               kv_tokens_[n_common] == tokens[n_common]) {
            ++n_common;
        }
        // The last prompt token is always re-evaluated so fresh logits are available.
        if (n_common == tokens.size()) --n_common;
        if (n_common < kv_tokens_.size()) {
            llama_memory_t mem = llama_get_memory(ctx_);
            if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(n_common), -1)) {
                llama_memory_clear(mem, true);
                n_common = 0;
            }
            kv_tokens_.resize(n_common);
        }
        result.cached_tokens = static_cast<int32_t>(n_common);

        if (!DecodePrompt(tokens, n_common, &result)) {
            result.prompt_eval_us = llama_time_us() - t_start;
            return result;
        }
        const int64_t t_prompt_done = llama_time_us();
        result.prompt_eval_us = t_prompt_done - t_start;

        // ---- Sampling loop -------------------------------------------------------------------
        Sampler sampler(vocab_, params);
        if (sampler.grammar_failed()) {
            result.stop_reason = StopReason::kError;
            result.error = "Failed to parse GBNF grammar";
            return result;
        }

        const int32_t room = n_ctx - static_cast<int32_t>(kv_tokens_.size()) - 1;
        const int32_t budget = std::max(0, std::min(params.max_tokens, room));
        result.stop_reason = params.max_tokens <= room ? StopReason::kMaxTokens : StopReason::kContextFull;

        std::string pending;  // bytes of a UTF-8 sequence that is not complete yet
        bool stopped = false;
        for (int32_t i = 0; i < budget; ++i) {
            if (g_stop_requested.load(std::memory_order_relaxed)) {
                result.stop_reason = StopReason::kCancelled;
                stopped = true;
                break;
            }
            const llama_token token = sampler.Sample(ctx_);
            if (token == LLAMA_TOKEN_NULL) {
                result.stop_reason = StopReason::kError;
                result.error = "Sampling failed (no valid token)";
                stopped = true;
                break;
            }
            if (llama_vocab_is_eog(vocab_, token)) {
                result.stop_reason = StopReason::kEndOfGeneration;
                stopped = true;
                break;
            }
            ++result.generated_tokens;

            AppendPiece(token, &pending);
            const size_t ready = Utf8CompletePrefixLength(pending);
            if (ready > 0) {
                if (!sink(pending.data(), ready)) {
                    result.stop_reason = StopReason::kCancelled;
                    pending.clear();
                    stopped = true;
                    break;
                }
                pending.erase(0, ready);
            }

            if (!DecodeToken(token, &result)) {
                stopped = true;
                break;
            }
        }
        if (!pending.empty()) {
            sink(pending.data(), pending.size());
        }
        (void) stopped;
        result.generation_us = llama_time_us() - t_prompt_done;
    } catch (const std::exception& e) {
        DropUncommittedCache();
        result.stop_reason = StopReason::kError;
        result.error = e.what();
    }
    return result;
}

bool LlmEngine::DecodePrompt(const std::vector<llama_token>& tokens,
                             size_t start,
                             GenerationResult* result) {
    const size_t n = tokens.size();
    for (size_t i = start; i < n; i += static_cast<size_t>(n_batch_)) {
        const size_t end = std::min(n, i + static_cast<size_t>(n_batch_));
        llama_batch_ext_clear(batch_);
        for (size_t j = i; j < end; ++j) {
            const int32_t idx = llama_batch_ext_add_token(batch_, 0, tokens[j]);
            if (idx < 0) {
                result->stop_reason = StopReason::kError;
                result->error = "Failed to build batch (" + std::to_string(idx) + ")";
                return false;
            }
            const auto pos = static_cast<llama_pos>(j);
            llama_batch_ext_set_pos(batch_, idx, &pos);
            if (j == n - 1) llama_batch_ext_set_output_logits(batch_, idx, true);
        }
        const int32_t rc = llama_process(ctx_, LLAMA_PROCESS_TYPE_DECODE, batch_);
        if (rc != 0) {
            DropUncommittedCache();
            if (rc == 2) {
                result->stop_reason = StopReason::kCancelled;
            } else if (rc == 1) {
                result->stop_reason = StopReason::kContextFull;
                result->error = "No KV cache slot available";
            } else {
                result->stop_reason = StopReason::kError;
                result->error = "llama_process failed with " + std::to_string(rc);
            }
            return false;
        }
        kv_tokens_.insert(kv_tokens_.end(), tokens.begin() + static_cast<std::ptrdiff_t>(i),
                          tokens.begin() + static_cast<std::ptrdiff_t>(end));
    }
    return true;
}

bool LlmEngine::DecodeToken(llama_token token, GenerationResult* result) {
    llama_batch_ext_clear(batch_);
    const int32_t idx = llama_batch_ext_add_token(batch_, 0, token);
    if (idx < 0) {
        result->stop_reason = StopReason::kError;
        result->error = "Failed to build batch";
        return false;
    }
    const auto pos = static_cast<llama_pos>(kv_tokens_.size());
    llama_batch_ext_set_pos(batch_, idx, &pos);
    llama_batch_ext_set_output_logits(batch_, idx, true);
    const int32_t rc = llama_process(ctx_, LLAMA_PROCESS_TYPE_DECODE, batch_);
    if (rc != 0) {
        DropUncommittedCache();
        result->stop_reason = rc == 2 ? StopReason::kCancelled
                            : rc == 1 ? StopReason::kContextFull
                                      : StopReason::kError;
        if (rc != 2) result->error = "llama_process failed with " + std::to_string(rc);
        return false;
    }
    kv_tokens_.push_back(token);
    return true;
}

// After an aborted/failed llama_process the cache may hold ubatches we never recorded in
// kv_tokens_. Trim it back so the bookkeeping matches the memory exactly.
void LlmEngine::DropUncommittedCache() {
    llama_memory_t mem = llama_get_memory(ctx_);
    if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(kv_tokens_.size()), -1)) {
        llama_memory_clear(mem, true);
        kv_tokens_.clear();
    }
}

// =============================================================================================
//  Utilities
// =============================================================================================
std::vector<llama_token> LlmEngine::Tokenize(const std::string& text, bool add_special) const {
    if (text.empty()) return {};
    const auto len = static_cast<int32_t>(text.size());
    int32_t n = llama_tokenize(vocab_, text.data(), len, nullptr, 0, add_special, /*parse_special=*/true);
    if (n == INT32_MIN) throw std::runtime_error("Tokenization overflow");
    if (n < 0) n = -n;
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    const int32_t got = llama_tokenize(vocab_, text.data(), len, tokens.data(), n, add_special, true);
    if (got < 0) throw std::runtime_error("Tokenization failed");
    tokens.resize(static_cast<size_t>(got));
    return tokens;
}

void LlmEngine::AppendPiece(llama_token token, std::string* out) const {
    // special=true so tags such as <think> and <tool_call> reach the Kotlin parsers verbatim.
    char buf[128];
    const int32_t n = llama_token_to_piece(vocab_, token, buf, sizeof(buf), 0, /*special=*/true);
    if (n >= 0) {
        out->append(buf, static_cast<size_t>(n));
        return;
    }
    std::string big(static_cast<size_t>(-n), '\0');
    const int32_t m = llama_token_to_piece(vocab_, token, big.data(), -n, 0, true);
    if (m > 0) out->append(big.data(), static_cast<size_t>(m));
}

int32_t LlmEngine::CountTokens(const std::string& text) const {
    try {
        return static_cast<int32_t>(Tokenize(text, /*add_special=*/false).size());
    } catch (const std::exception&) {
        return -1;
    }
}

void LlmEngine::SetThreads(int32_t n_threads) {
    const int32_t n = std::max(1, n_threads);
    llama_set_n_threads(ctx_, n, n);
}

void LlmEngine::ClearCache() {
    llama_memory_clear(llama_get_memory(ctx_), true);
    kv_tokens_.clear();
}

int32_t LlmEngine::ContextSize() const {
    return static_cast<int32_t>(llama_n_ctx(ctx_));
}

std::string LlmEngine::Describe() const {
    char desc[256] = {0};
    llama_model_desc(model_, desc, sizeof(desc));
    char arch[64] = {0};
    llama_model_meta_val_str(model_, "general.architecture", arch, sizeof(arch));
    char name[128] = {0};
    llama_model_meta_val_str(model_, "general.name", name, sizeof(name));

    std::ostringstream os;
    os << "{"
       << "\"description\":\"" << JsonEscape(desc) << "\","
       << "\"architecture\":\"" << JsonEscape(arch) << "\","
       << "\"name\":\"" << JsonEscape(name) << "\","
       << "\"sizeBytes\":" << llama_model_size(model_) << ","
       << "\"parameters\":" << llama_model_n_params(model_) << ","
       << "\"contextTrain\":" << llama_model_n_ctx_train(model_) << ","
       << "\"context\":" << llama_n_ctx(ctx_) << ","
       << "\"batch\":" << n_batch_ << ","
       << "\"threads\":" << llama_n_threads(ctx_) << ","
       << "\"kvCacheType\":\"" << (kv_quantized_ ? "q8_0" : "f16") << "\","
       << "\"cachedTokens\":" << kv_tokens_.size()
       << "}";
    return os.str();
}

}  // namespace ailauncher
