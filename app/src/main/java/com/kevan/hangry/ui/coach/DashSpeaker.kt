package com.kevan.hangry.ui.coach

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Reads Dash's replies out loud with the device's text-to-speech engine. A key identifies the
 * message being spoken so its button can show an active state. One instance lives per screen and
 * is shut down when the screen leaves composition.
 */
@Stable
class DashSpeaker(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val utteranceKeys = HashMap<String, String>()

    /** The message key currently being read, or null when nothing is playing. */
    var speakingKey by mutableStateOf<String?>(null)
        private set

    fun start() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = finish(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finish(utteranceId)

                    override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                })
            }
        }
    }

    /** Speaks [text] for [key], or stops if that same message is already being read. */
    fun toggle(key: String, text: String) {
        if (speakingKey == key) {
            stop()
            return
        }
        stop()
        val engine = tts ?: return
        if (text.isBlank()) return
        val utteranceId = "dash-$key"
        utteranceKeys[utteranceId] = key
        speakingKey = key
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        speakingKey = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun finish(utteranceId: String?) {
        val key = utteranceId?.let { utteranceKeys.remove(it) } ?: return
        main.post { if (speakingKey == key) speakingKey = null }
    }
}

@Composable
fun rememberDashSpeaker(): DashSpeaker {
    val context = LocalContext.current
    val speaker = remember { DashSpeaker(context.applicationContext) }
    DisposableEffect(speaker) {
        speaker.start()
        onDispose { speaker.shutdown() }
    }
    return speaker
}
