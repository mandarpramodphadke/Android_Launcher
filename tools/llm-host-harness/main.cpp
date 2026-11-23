// Desktop driver for ailauncher::LlmEngine. Every prompt file is generated in order on the SAME
// engine instance, so running two prompts that share a prefix exercises KV prefix caching.
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "llm_engine.h"

using namespace ailauncher;

static std::string ReadFile(const char* path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        std::fprintf(stderr, "cannot read %s\n", path);
        std::exit(2);
    }
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

static void Quiet(ggml_log_level level, const char* text, void*) {
    if (level >= GGML_LOG_LEVEL_WARN) std::fputs(text, stderr);
}

int main(int argc, char** argv) {
    LoadParams load;
    SamplingParams sampling;
    std::vector<const char*> prompts;
    int cancel_after_ms = -1;
    bool cancel_only_first = false;
    bool verbose = false;
    for (int i = 1; i < argc; ++i) {
        auto next = [&]() -> const char* { return i + 1 < argc ? argv[++i] : ""; };
        if (!std::strcmp(argv[i], "--model")) load.model_path = next();
        else if (!std::strcmp(argv[i], "--ctx")) load.n_ctx = std::atoi(next());
        else if (!std::strcmp(argv[i], "--threads")) load.n_threads = std::atoi(next());
        else if (!std::strcmp(argv[i], "--no-kv-quant")) load.quantize_kv_cache = false;
        else if (!std::strcmp(argv[i], "--max")) sampling.max_tokens = std::atoi(next());
        else if (!std::strcmp(argv[i], "--temp")) sampling.temperature = std::atof(next());
        else if (!std::strcmp(argv[i], "--top-p")) sampling.top_p = std::atof(next());
        else if (!std::strcmp(argv[i], "--top-k")) sampling.top_k = std::atoi(next());
        else if (!std::strcmp(argv[i], "--presence")) sampling.presence_penalty = std::atof(next());
        else if (!std::strcmp(argv[i], "--seed")) sampling.seed = static_cast<uint32_t>(std::atol(next()));
        else if (!std::strcmp(argv[i], "--grammar")) sampling.grammar = ReadFile(next());
        else if (!std::strcmp(argv[i], "--cancel-after-ms")) cancel_after_ms = std::atoi(next());
        else if (!std::strcmp(argv[i], "--cancel-only-first")) cancel_only_first = true;
        else if (!std::strcmp(argv[i], "--verbose")) verbose = true;
        else prompts.push_back(argv[i]);
    }
    if (load.model_path.empty() || prompts.empty()) {
        std::fprintf(stderr, "usage: %s --model <gguf> [options] prompt.txt [prompt2.txt ...]\n", argv[0]);
        return 1;
    }
    if (!verbose) llama_log_set(Quiet, nullptr);

    LlmEngine::InitBackends("");
    std::fprintf(stderr, "%s\n", LlmEngine::SystemInfo().c_str());

    std::string error;
    float last = -1.0f;
    auto engine = LlmEngine::Load(load, [&](float p) {
        if (p - last >= 0.25f || p >= 1.0f) { std::fprintf(stderr, "load %.0f%%\n", p * 100); last = p; }
        return true;
    }, &error);
    if (!engine) {
        std::fprintf(stderr, "load failed: %s\n", error.c_str());
        return 1;
    }
    std::fprintf(stderr, "%s\n", engine->Describe().c_str());

    for (size_t prompt_index = 0; prompt_index < prompts.size(); ++prompt_index) {
        const char* path = prompts[prompt_index];
        const std::string prompt = ReadFile(path);
        std::fprintf(stderr, "\n===== %s (%d tokens) =====\n", path, engine->CountTokens(prompt));
        std::thread canceller;
        if (cancel_after_ms >= 0 && (!cancel_only_first || prompt_index == 0)) {
            canceller = std::thread([cancel_after_ms] {
                std::this_thread::sleep_for(std::chrono::milliseconds(cancel_after_ms));
                LlmEngine::RequestStop();
            });
        }
        std::string out;
        const GenerationResult r = engine->Generate(prompt, sampling, [&](const char* data, size_t n) {
            out.append(data, n);
            std::fwrite(data, 1, n, stdout);
            std::fflush(stdout);
            return true;
        });
        if (canceller.joinable()) canceller.join();
        const double pp = r.prompt_eval_us / 1e6, tg = r.generation_us / 1e6;
        std::fprintf(stderr,
                     "\n----- stop=%d prompt=%d cached=%d generated=%d | prompt %.2fs (%.1f tok/s) | gen %.2fs (%.1f tok/s) %s\n",
                     static_cast<int>(r.stop_reason), r.prompt_tokens, r.cached_tokens, r.generated_tokens,
                     pp, pp > 0 ? (r.prompt_tokens - r.cached_tokens) / pp : 0.0,
                     tg, tg > 0 ? r.generated_tokens / tg : 0.0, r.error.c_str());
    }
    return 0;
}
