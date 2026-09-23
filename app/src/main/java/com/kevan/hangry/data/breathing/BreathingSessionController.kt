package com.kevan.hangry.data.breathing

import android.content.Context
import android.os.SystemClock
import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import com.kevan.hangry.domain.model.BreathPosition
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.positionAt
import com.kevan.hangry.domain.repository.BreathingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

sealed interface BreathingSessionState {
    data object Idle : BreathingSessionState

    data class Active(
        val pattern: BreathingPattern,
        val targetSeconds: Int,
        val elapsedMs: Long,
        val position: BreathPosition,
        val isPaused: Boolean,
        val soundEnabled: Boolean
    ) : BreathingSessionState {
        val remainingSeconds: Int get() = (targetSeconds - (elapsedMs / 1000).toInt()).coerceAtLeast(0)
        val progress: Float get() = (elapsedMs / (targetSeconds * 1000f)).coerceIn(0f, 1f)
    }

    data class Finished(
        val pattern: BreathingPattern,
        val durationSeconds: Int,
        val cycles: Int,
        val completed: Boolean,
        /** False while the session is still being written; null when too short to be logged. */
        val saved: Boolean?,
        val healthConnectSynced: Boolean
    ) : BreathingSessionState
}

/**
 * App-scoped owner of the one breathing session that can run at a time. Lives outside any
 * screen so the session - its timing, audio cues and final save - survives navigation and the
 * screen turning off; [BreathingSessionService] keeps the process in the foreground meanwhile.
 *
 * Timing is derived from accumulated elapsed-realtime rather than counted ticks, so a late
 * tick can never make the cues drift from the schedule.
 */
class BreathingSessionController(
    context: Context,
    private val repository: BreathingRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BreathingSessionState>(BreathingSessionState.Idle)
    val state: StateFlow<BreathingSessionState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var tonePlayer: BreathingTonePlayer? = null

    private var pattern = BreathingPattern.BPM_6
    private var targetSeconds = 0
    private var startedAt: Instant = Instant.now()
    private var accumulatedMs = 0L
    private var runningSinceRealtime: Long? = null
    private var soundEnabled = true

    fun start(pattern: BreathingPattern, minutes: Int, soundEnabled: Boolean) {
        if (_state.value is BreathingSessionState.Active) return
        this.pattern = pattern
        this.targetSeconds = pattern.sessionSecondsFor(minutes)
        this.soundEnabled = soundEnabled
        startedAt = Instant.now()
        accumulatedMs = 0L
        runningSinceRealtime = SystemClock.elapsedRealtime()
        tonePlayer?.release()
        tonePlayer = BreathingTonePlayer()

        publishActive()
        BreathingSessionService.start(appContext)
        loopJob = scope.launch { runLoop() }
    }

    fun pause() {
        val since = runningSinceRealtime ?: return
        accumulatedMs += SystemClock.elapsedRealtime() - since
        runningSinceRealtime = null
        publishActive()
    }

    fun resume() {
        if (_state.value !is BreathingSessionState.Active || runningSinceRealtime != null) return
        runningSinceRealtime = SystemClock.elapsedRealtime()
        publishActive()
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        if (_state.value is BreathingSessionState.Active) publishActive()
    }

    /** Ends the session early; whatever was completed is still logged. */
    fun stop() {
        if (_state.value !is BreathingSessionState.Active) return
        loopJob?.cancel()
        scope.launch { finish(completed = false) }
    }

    /** Clears the finished summary once the user has seen it. */
    fun dismissSummary() {
        if (_state.value is BreathingSessionState.Finished) _state.value = BreathingSessionState.Idle
    }

    private suspend fun runLoop() {
        var lastCueKey = -1L
        while (true) {
            val elapsed = elapsedMs()
            if (elapsed >= targetSeconds * 1000L) {
                finish(completed = true)
                return
            }
            val position = pattern.positionAt(elapsed)
            val cueKey = position.cycleIndex * 100L + position.phaseIndex
            if (cueKey != lastCueKey && runningSinceRealtime != null) {
                if (soundEnabled) tonePlayer?.playFor(position.phase.type)
                lastCueKey = cueKey
            }
            publishActive(elapsed, position)
            delay(TICK_MS)
        }
    }

    private suspend fun finish(completed: Boolean) {
        val elapsed = elapsedMs().coerceAtMost(targetSeconds * 1000L)
        runningSinceRealtime = null
        val durationSeconds = (elapsed / 1000).toInt()
        val cycles = pattern.cyclesIn(durationSeconds)
        val endedAt = Instant.now()
        val loggable = durationSeconds >= MIN_LOGGED_SECONDS

        if (completed && soundEnabled) tonePlayer?.play(BreathingTonePlayer.Cue.FINISH)

        _state.value = BreathingSessionState.Finished(
            pattern = pattern,
            durationSeconds = durationSeconds,
            cycles = cycles,
            completed = completed,
            saved = if (loggable) false else null,
            healthConnectSynced = false
        )

        val player = tonePlayer
        tonePlayer = null
        scope.launch {
            delay(FINISH_CUE_MS)
            player?.release()
        }

        if (!loggable) return
        val stored = withContext(NonCancellable + Dispatchers.IO) {
            repository.saveSession(
                BreathingSessionEntity(
                    date = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    startTime = startedAt,
                    endTime = endedAt,
                    patternId = pattern.id,
                    durationSeconds = durationSeconds,
                    cyclesCompleted = cycles,
                    completed = completed
                )
            )
        }
        val current = _state.value
        if (current is BreathingSessionState.Finished) {
            _state.value = current.copy(saved = true, healthConnectSynced = stored.healthConnectSynced)
        }
    }

    private fun elapsedMs(): Long {
        val since = runningSinceRealtime ?: return accumulatedMs
        return accumulatedMs + (SystemClock.elapsedRealtime() - since)
    }

    private fun publishActive(
        elapsed: Long = elapsedMs(),
        position: BreathPosition = pattern.positionAt(elapsed)
    ) {
        _state.value = BreathingSessionState.Active(
            pattern = pattern,
            targetSeconds = targetSeconds,
            elapsedMs = elapsed,
            position = position,
            isPaused = runningSinceRealtime == null,
            soundEnabled = soundEnabled
        )
    }

    private companion object {
        const val TICK_MS = 50L
        const val FINISH_CUE_MS = 2_000L
        /** Sessions shorter than this are treated as a false start and not logged. */
        const val MIN_LOGGED_SECONDS = 30
    }
}
