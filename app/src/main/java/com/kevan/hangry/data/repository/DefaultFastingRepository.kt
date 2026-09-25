package com.kevan.hangry.data.repository

import android.app.NotificationManager
import android.content.Context
import com.kevan.hangry.data.fasting.FastingPrefs
import com.kevan.hangry.data.fasting.FastingReceiver
import com.kevan.hangry.data.fasting.FastingReminderScheduler
import com.kevan.hangry.data.local.dao.FastDao
import com.kevan.hangry.data.local.entity.FastEntity
import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.Fast
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingPlan
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.repository.FastingRepository
import com.kevan.hangry.ui.widget.HangryWidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DefaultFastingRepository(
    private val context: Context,
    private val dao: FastDao,
    private val prefs: FastingPrefs,
    private val scheduler: FastingReminderScheduler
) : FastingRepository {

    // Settings live in SharedPreferences and only change through here, so a StateFlow mirrors them.
    private val settings = MutableStateFlow(prefs.read())
    private val mutex = Mutex()

    override fun observe(): Flow<FastingSnapshot> =
        combine(settings, dao.observeAll()) { s, fasts -> snapshot(s, fasts) }

    override suspend fun current(): FastingSnapshot = snapshot(settings.value, dao.getAll())

    override suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        if (!enabled) endActive(Instant.now())
        update { it.copy(enabled = enabled) }
    }

    override suspend fun setPlan(plan: FastingPlan, customHours: Int?) = mutex.withLock {
        update { s ->
            s.copy(
                plan = plan,
                customHours = (customHours ?: s.customHours).coerceIn(FastingPlan.MIN_CUSTOM_HOURS, FastingPlan.MAX_CUSTOM_HOURS)
            )
        }
        dao.getActive()?.let { dao.update(it.copy(targetMinutes = settings.value.targetHours * 60, planId = plan.id)) }
        afterChange()
    }

    override suspend fun setGoalReminder(enabled: Boolean) = mutex.withLock {
        update { it.copy(goalReminder = enabled) }
    }

    override suspend fun startFast(at: Instant) = mutex.withLock {
        if (!settings.value.enabled || dao.getActive() != null) return@withLock
        val s = settings.value
        dao.insert(FastEntity(startAt = minOf(at, Instant.now()), targetMinutes = s.targetHours * 60, planId = s.plan.id))
        afterChange()
    }

    override suspend fun endFast(at: Instant): Boolean = mutex.withLock {
        endActive(at).also { afterChange() }
    }

    override suspend fun editStart(at: Instant) = mutex.withLock {
        val active = dao.getActive() ?: return@withLock
        dao.update(active.copy(startAt = minOf(at, Instant.now())))
        afterChange()
    }

    override suspend fun deleteFast(id: Long) = mutex.withLock {
        dao.delete(id)
        afterChange()
    }

    override suspend fun rescheduleReminder() {
        val s = settings.value
        val active = dao.getActive()?.toModel()
        scheduler.schedule(active?.goalAt()?.takeIf { s.enabled && s.goalReminder })
    }

    /** Saves the running fast, unless it was a mis-tap of a few minutes. */
    private suspend fun endActive(at: Instant): Boolean {
        val active = dao.getActive() ?: return false
        val end = at.coerceIn(active.startAt, Instant.now())
        if (Duration.between(active.startAt, end).toMinutes() < FastingMath.MIN_SAVED_MINUTES) {
            dao.delete(active.id)
        } else {
            dao.update(active.copy(endAt = end))
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(FastingReceiver.NOTIFICATION_ID)
        return true
    }

    private suspend fun update(transform: (FastingPrefs.Settings) -> FastingPrefs.Settings) {
        val next = transform(settings.value)
        prefs.write(next)
        settings.value = next
        afterChange()
    }

    private suspend fun afterChange() {
        rescheduleReminder()
        HangryWidgetUpdater.updateAllWidgets(context)
    }

    private fun snapshot(s: FastingPrefs.Settings, entities: List<FastEntity>): FastingSnapshot {
        val fasts = entities.map { it.toModel() }
        val zone = ZoneId.systemDefault()
        val streak = StreakCalculator.streak(StreakType.FASTING, FastingMath.goalDays(fasts, zone), LocalDate.now(zone))
        return FastingSnapshot(
            enabled = s.enabled,
            plan = s.plan,
            targetHours = s.targetHours,
            goalReminder = s.goalReminder,
            active = fasts.firstOrNull { it.isActive },
            history = fasts.filter { !it.isActive },
            streak = streak.current,
            bestStreak = streak.best
        )
    }

    private fun FastEntity.toModel() = Fast(id, startAt, endAt, targetMinutes, planId)
}
