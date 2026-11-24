package com.ailauncher.data.llm

import com.ailauncher.domain.ai.PromptMessage
import com.ailauncher.domain.ai.Qwen3PromptBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.concurrent.thread

/**
 * Integration test of the real JNI bridge (llama_jni.cpp + llm_engine.cpp) through the exact
 * Kotlin declarations in [LlamaNative], on the desktop JVM with a real GGUF. JNI name/signature
 * mismatches compile fine and only fail at runtime, so this guards the native boundary.
 *
 * Skipped unless both properties are given:
 * ```
 * cmake -S tools/llm-host-harness -B build/host -DLLAMA_CPP_DIR=<llama.cpp> && cmake --build build/host
 * ./gradlew :app:testDebugUnitTest --tests '*LlamaNativeHostTest*' \
 *     -Pailauncher.test.nativeDir=$PWD/build/host -Pailauncher.test.model=/path/qwen3-1.7b-q4_k_m.gguf
 * ```
 */
class LlamaNativeHostTest {

    private val modelPath: String? = System.getProperty("ailauncher.test.model")

    @Before
    fun requireModel() {
        assumeTrue(
            "Set -Pailauncher.test.model and -Pailauncher.test.nativeDir to run the JNI integration test",
            modelPath != null && File(modelPath).isFile,
        )
    }

    private fun prompt(user: String, thinking: Boolean = false) = Qwen3PromptBuilder.build(
        messages = listOf(
            PromptMessage(PromptMessage.Role.SYSTEM, "You are a concise assistant."),
            PromptMessage(PromptMessage.Role.USER, if (thinking) user else "$user ${Qwen3PromptBuilder.NO_THINK}"),
        ),
        enableThinking = thinking,
    ).toByteArray(Charsets.UTF_8)

    private fun generate(
        handle: Long,
        prompt: ByteArray,
        maxTokens: Int = 32,
        grammar: String? = null,
        onToken: (String) -> Boolean = { true },
    ): LongArray = LlamaNative.generateStream(
        handle = handle,
        prompt = prompt,
        temperature = 0.7f,
        topP = 0.8f,
        topK = 20,
        minP = 0f,
        presencePenalty = 0f,
        repeatPenalty = 1f,
        maxTokens = maxTokens,
        seed = 42,
        grammar = grammar?.toByteArray(Charsets.UTF_8),
        callback = { bytes -> onToken(String(bytes, Charsets.UTF_8)) },
    )

    @Test
    fun jniBridgeEndToEnd() {
        LlamaNative.initialize("")
        assertThat(LlamaNative.systemInfo()).contains("Devices")

        val progress = mutableListOf<Float>()
        val handle = LlamaNative.loadModel(modelPath!!, 1024, 4, 512, true, true, 0) { p ->
            progress += p
            true
        }
        try {
            assertThat(handle).isNotEqualTo(0L)
            assertThat(progress.last()).isWithin(0.01f).of(1f)
            assertThat(LlamaNative.describe(handle)).contains("\"architecture\":\"qwen3\"")
            assertThat(LlamaNative.countTokens(handle, "Hello, world!".toByteArray())).isGreaterThan(1)

            // 1. Streaming through NativeTokenCallback; multi-byte UTF-8 must never be split.
            val greeting = StringBuilder()
            val stats = generate(handle, prompt("Reply with exactly this text: こんにちは 👋")) { greeting.append(it); true }
            assertThat(greeting.toString()).doesNotContain("�")
            assertThat(greeting.toString()).contains("こんにちは")
            assertThat(stats).hasLength(6)
            assertThat(stats[3]).isGreaterThan(0L)

            // 2. Prefix KV cache: the same prompt is served from the cache the second time.
            val again = generate(handle, prompt("Reply with exactly this text: こんにちは 👋"))
            assertThat(again[2]).isGreaterThan(10L)

            // 3. Grammar-constrained sampling (GBNF).
            val answer = StringBuilder()
            generate(handle, prompt("Is the sky blue on a clear day? Answer yes or no."), grammar = "root ::= \"yes\" | \"no\"") {
                answer.append(it)
                true
            }
            assertThat(answer.toString()).isAnyOf("yes", "no")

            // 4. Returning false from the callback stops generation (StopReason.CANCELLED = 2).
            var calls = 0
            val cancelled = generate(handle, prompt("Count from one to fifty."), maxTokens = 200) { ++calls < 3 }
            assertThat(cancelled[0]).isEqualTo(2L)
            assertThat(calls).isEqualTo(3)

            // 5. stopGeneration() from another thread aborts promptly.
            val stopper = thread {
                Thread.sleep(400)
                LlamaNative.stopGeneration()
            }
            val stopped = generate(handle, prompt("Explain photosynthesis in detail.", thinking = true), maxTokens = 600)
            stopper.join()
            assertThat(stopped[0]).isEqualTo(2L)

            // 6. A Kotlin exception thrown in the callback propagates through C++ untouched.
            assertThrows(IllegalArgumentException::class.java) {
                generate(handle, prompt("Say hello.")) { throw IllegalArgumentException("boom") }
            }

            // The engine is still healthy afterwards.
            assertThat(generate(handle, prompt("Say OK."))[3]).isGreaterThan(0L)
        } finally {
            LlamaNative.unloadModel(handle)
        }
    }
}
