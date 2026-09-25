package com.kevan.hangry.data.repository

import android.content.Context
import com.kevan.hangry.data.local.dao.ProgramSessionDao
import com.kevan.hangry.data.local.entity.ProgramSessionEntity
import com.kevan.hangry.domain.model.ProgramCatalog
import com.kevan.hangry.domain.model.ProgramFeel
import com.kevan.hangry.domain.model.ProgramSession
import com.kevan.hangry.domain.model.ProgramSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * The guided programs: which are on and at what level (SharedPreferences, only changed through
 * here) plus finished sessions (Room). Every program is off until the user turns it on.
 */
class ProgramsRepository(context: Context, private val dao: ProgramSessionDao) {

    private val prefs = context.getSharedPreferences("hangry_programs", Context.MODE_PRIVATE)
    private data class Settings(val enabled: Boolean, val level: Int)
    private val settings = MutableStateFlow(
        ProgramCatalog.ALL.associate { p -> p.id to Settings(prefs.getBoolean("${p.id}_enabled", false), prefs.getInt("${p.id}_level", 1)) }
    )

    fun observeAll(): Flow<List<ProgramSnapshot>> = combine(settings, dao.observeAll()) { s, rows -> snapshots(s, rows) }

    suspend fun current(): List<ProgramSnapshot> = snapshots(settings.value, dao.getAll())

    fun setEnabled(programId: String, enabled: Boolean) = update(programId) { it.copy(enabled = enabled) }

    fun setLevel(programId: String, level: Int) {
        val max = ProgramCatalog.byId(programId)?.levels?.size ?: return
        update(programId) { it.copy(level = level.coerceIn(1, max)) }
    }

    /** Saves today's session at the program's current level. */
    suspend fun finishSession(programId: String, feel: ProgramFeel, date: LocalDate = LocalDate.now()) {
        val level = settings.value[programId]?.level ?: return
        dao.insert(ProgramSessionEntity(programId = programId, date = date, level = level, feel = feel.name))
    }

    suspend fun deleteSession(id: Long) = dao.delete(id)

    private fun update(programId: String, transform: (Settings) -> Settings) {
        val current = settings.value[programId] ?: return
        val next = transform(current)
        prefs.edit().putBoolean("${programId}_enabled", next.enabled).putInt("${programId}_level", next.level).apply()
        settings.value = settings.value + (programId to next)
    }

    private fun snapshots(s: Map<String, Settings>, rows: List<ProgramSessionEntity>): List<ProgramSnapshot> {
        val today = LocalDate.now()
        val byProgram = rows.groupBy { it.programId }
        return ProgramCatalog.ALL.map { program ->
            val setting = s[program.id] ?: Settings(false, 1)
            ProgramSnapshot(
                program = program,
                enabled = setting.enabled,
                level = setting.level,
                sessions = byProgram[program.id].orEmpty().mapNotNull { e ->
                    ProgramFeel.fromName(e.feel)?.let { ProgramSession(e.id, e.programId, e.date, e.level, it) }
                },
                today = today
            )
        }
    }
}
