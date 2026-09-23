package com.kevan.hangry.data.breathing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.kevan.hangry.domain.model.BreathPhaseType
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays short synthesized cues for guided breathing - no audio assets involved:
 * one plain beep at the start of every phase (in, hold, out), and a double beep when the
 * session finishes.
 *
 * Cues use media attributes so they follow the headphone route and mix with any music.
 */
class BreathingTonePlayer {

    enum class Cue { INHALE, EXHALE, HOLD, FINISH }

    private val beep: AudioTrack? = buildTrack(beeps(count = 1))
    private val finishBeep: AudioTrack? = buildTrack(beeps(count = 2))

    fun play(cue: Cue) {
        val track = (if (cue == Cue.FINISH) finishBeep else beep) ?: return
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
        runCatching { beep?.release() }
        runCatching { finishBeep?.release() }
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

    /** Plain sine beep(s) with a few ms of fade in/out so the edges don't click. */
    private fun beeps(count: Int): ShortArray {
        val beepSamples = (SAMPLE_RATE * BEEP_SECONDS).toInt()
        val gapSamples = (SAMPLE_RATE * GAP_SECONDS).toInt()
        val fadeSamples = (SAMPLE_RATE * 0.005).toInt()
        val out = ShortArray(count * beepSamples + (count - 1) * gapSamples)
        repeat(count) { b ->
            val offset = b * (beepSamples + gapSamples)
            for (i in 0 until beepSamples) {
                val fade = min(1.0, min(i, beepSamples - i).toDouble() / fadeSamples)
                val sample = sin(2 * PI * BEEP_HZ * i / SAMPLE_RATE) * AMPLITUDE * fade
                out[offset + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private companion object {
        const val TAG = "HangryBreathing"
        const val SAMPLE_RATE = 44_100
        const val BEEP_HZ = 880.0
        const val BEEP_SECONDS = 0.15
        const val GAP_SECONDS = 0.12
        const val AMPLITUDE = 0.4
    }
}
