package com.ailauncher.domain.speech

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Reads replies aloud on the phone (SPOKEN_REPLIES). Implemented with Android's TextToSpeech. */
interface SpeechOutput {
    /** True while something is being read aloud. */
    val speaking: StateFlow<Boolean>

    /** Reads [text] (plain text, see [SpeechText]) aloud, replacing whatever was being read. */
    fun speak(text: String)
    fun stop()
}

/**
 * The last thing the user said into the microphone, so the chat knows which message was spoken
 * and can read its reply aloud ("After I use my voice"). Valid for a short while only.
 */
@Singleton
class VoicePrompts @Inject constructor() {
    private var last: Pair<String, Long>? = null

    @Synchronized
    fun mark(text: String, now: Long = System.currentTimeMillis()) {
        last = text.trim() to now
    }

    /** True once if [text] is what was just spoken. */
    @Synchronized
    fun consume(text: String, now: Long = System.currentTimeMillis()): Boolean {
        val (spoken, at) = last ?: return false
        if (spoken != text.trim() || now - at > VALID_MILLIS) return false
        last = null
        return true
    }

    private companion object {
        const val VALID_MILLIS = 60_000L
    }
}
