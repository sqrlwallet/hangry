package com.kevan.hangry.data.breathing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.kevan.hangry.domain.model.BreathPhaseType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays short synthesized cues for guided breathing - no audio assets involved:
 * a rising chime means breathe in, a falling chime means breathe out, and a soft single
 * ping marks a hold. Distinct pitch direction lets the session be followed eyes-closed.
 *
 * Cues use media attributes so they follow the headphone route and mix with any music.
 */
class BreathingTonePlayer {

    enum class Cue { INHALE, EXHALE, HOLD, FINISH }

    private val tracks: Map<Cue, AudioTrack?> = mapOf(
        Cue.INHALE to buildTrack(glide(fromHz = 392.0, toHz = 587.3, seconds = 0.75)),
        Cue.EXHALE to buildTrack(glide(fromHz = 587.3, toHz = 392.0, seconds = 0.75)),
        Cue.HOLD to buildTrack(glide(fromHz = 784.0, toHz = 784.0, seconds = 0.4, amplitude = 0.28)),
        Cue.FINISH to buildTrack(chime(listOf(523.3, 659.3, 784.0)))
    )

    fun play(cue: Cue) {
        val track = tracks[cue] ?: return
        try {
            // Static tracks have to be stopped and rewound before they can replay.
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't play breathing cue $cue: ${e.message}")
        }
    }

    fun playFor(phase: BreathPhaseType) = play(
        when (phase) {
            BreathPhaseType.INHALE -> Cue.INHALE
            BreathPhaseType.EXHALE -> Cue.EXHALE
            BreathPhaseType.HOLD_FULL, BreathPhaseType.HOLD_EMPTY -> Cue.HOLD
        }
    )

    fun release() {
        tracks.values.forEach { track -> runCatching { track?.release() } }
    }

    private fun buildTrack(samples: ShortArray): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't create breathing cue track: ${e.message}")
        null
    }

    private fun glide(
        fromHz: Double,
        toHz: Double,
        seconds: Double,
        amplitude: Double = 0.45
    ): ShortArray {
        val count = (SAMPLE_RATE * seconds).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            // Ease the glide so the pitch change reads clearly without sounding like a siren.
            val eased = t * t * (3 - 2 * t)
            val freq = fromHz + (toHz - fromHz) * eased
            phase += 2 * PI * freq / SAMPLE_RATE
            val tone = sin(phase) + 0.22 * sin(2 * phase) + 0.08 * sin(3 * phase)
            out[i] = (tone / 1.3 * amplitude * envelope(i, count) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun chime(notesHz: List<Double>): ShortArray {
        val noteSamples = (SAMPLE_RATE * 0.22).toInt()
        val tailSamples = (SAMPLE_RATE * 0.9).toInt()
        val total = noteSamples * (notesHz.size - 1) + tailSamples
        val mix = DoubleArray(total)
        notesHz.forEachIndexed { index, hz ->
            val offset = index * noteSamples
            for (i in 0 until tailSamples) {
                val phase = 2 * PI * hz * i / SAMPLE_RATE
                mix[offset + i] += (sin(phase) + 0.2 * sin(2 * phase)) * envelope(i, tailSamples)
            }
        }
        return ShortArray(total) { i -> (mix[i] / 2.4 * 0.45 * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Short attack, exponential bell-like decay, and a hard fade to zero to avoid clicks. */
    private fun envelope(i: Int, count: Int): Double {
        val attack = (SAMPLE_RATE * 0.015).toInt()
        val attackGain = min(1.0, i.toDouble() / attack)
        val decay = exp(-3.2 * i / count)
        val fadeOut = min(1.0, (count - i).toDouble() / (SAMPLE_RATE * 0.03))
        return attackGain * decay * fadeOut
    }

    private companion object {
        const val TAG = "HangryBreathing"
        const val SAMPLE_RATE = 44_100
    }
}
