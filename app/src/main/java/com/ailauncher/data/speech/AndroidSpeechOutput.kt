package com.ailauncher.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.ailauncher.domain.speech.SpeechOutput
import com.ailauncher.domain.speech.SpeechText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replies read aloud with the phone's text-to-speech engine (SPOKEN_REPLIES). Only a voice that
 * works offline is used: a network voice would send the reply's text off the phone, so without an
 * offline voice nothing is read. Audio ducks other sound while speaking.
 */
@Singleton
class AndroidSpeechOutput @Inject constructor(@ApplicationContext private val context: Context) : SpeechOutput {

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // All of the state below is touched on the main thread only.
    private var engine: TextToSpeech? = null
    private var ready = false
    private var offlineVoice = false
    private var pending: String? = null
    private var lastUtterance: String? = null
    private var utterances = 0
    private var focus: AudioFocusRequest? = null

    override fun speak(text: String) {
        main.post { speakNow(text) }
    }

    override fun stop() {
        main.post {
            pending = null
            engine?.stop()
            finished()
        }
    }

    private fun speakNow(text: String) {
        val chunks = SpeechText.chunks(text)
        if (chunks.isEmpty()) return
        val tts = engine ?: TextToSpeech(context) { status -> main.post { onInit(status) } }.also { engine = it }
        if (!ready) {
            pending = text
            return
        }
        if (!offlineVoice) {
            Log.i(TAG, "No offline text-to-speech voice; not reading aloud")
            return
        }
        requestFocus()
        val batch = ++utterances
        chunks.forEachIndexed { index, chunk ->
            val id = "aura-$batch-$index"
            tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            lastUtterance = id
        }
        _speaking.value = true
    }

    private fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Text-to-speech is not available ($status)")
            tts.shutdown()
            engine = null
            pending = null
            return
        }
        tts.setAudioAttributes(attributes)
        offlineVoice = runCatching { chooseOfflineVoice(tts) }.getOrDefault(false)
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    main.post { if (utteranceId == lastUtterance) finished() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { if (utteranceId == lastUtterance) finished() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { if (utteranceId == lastUtterance) finished() }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    main.post { if (utteranceId == lastUtterance) finished() }
                }
            },
        )
        ready = true
        pending?.let {
            pending = null
            speakNow(it)
        }
    }

    /** The phone's language, with a voice that needs no network; false if there is none. */
    private fun chooseOfflineVoice(tts: TextToSpeech): Boolean {
        val locale = Locale.getDefault()
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        val current: Voice? = tts.voice
        if (current != null && current.usableOffline()) return true
        val language = (current?.locale ?: locale).language
        val offline = tts.voices.orEmpty()
            .filter { it.usableOffline() && it.locale.language == language }
            .maxByOrNull { it.quality }
            ?: return false
        tts.voice = offline
        return true
    }

    private fun Voice.usableOffline(): Boolean =
        !isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()

    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focus = request
        audio?.requestAudioFocus(request)
    }

    private fun finished() {
        _speaking.value = false
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
    }

    private companion object {
        const val TAG = "SpeechOutput"
    }
}
