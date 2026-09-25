package com.kevan.hangry.domain.model

import java.time.LocalDate

/**
 * Optional guided programs (stress, focus, knees, back, first pull-up...). Each is a short path
 * of levels of simple exercises: do a session, say how it felt, and move up only when it feels
 * easy - never automatically. All are off until the user turns one on. Not medical advice.
 */
data class ProgramExercise(
    val id: String,
    val name: String,
    /** e.g. "2 × 15" or "5 minutes". */
    val dose: String,
    val howTo: String,
    /** A simpler way to do it if it's too much today. */
    val easier: String,
    /** A BreathingPattern id: the session can open that guided breathing with Dash. */
    val breathingPattern: String? = null
)

data class ProgramLevel(
    val number: Int,
    val title: String,
    val focus: String,
    val exercises: List<ProgramExercise>
)

/** Body programs talk about pain; mind programs about how you felt afterwards. */
enum class ProgramKind { BODY, MIND }

data class Program(
    val id: String,
    val title: String,
    val tagline: String,
    val intro: String,
    val kind: ProgramKind,
    val sessionsPerWeek: Int,
    val levels: List<ProgramLevel>,
    /** Extra warning signs shown with the safety note. */
    val extraSafety: String? = null,
    /** What the user needs, if anything. */
    val equipment: String? = null,
    val credit: String? = null,
    /** Sessions tick off Mobility on the longevity pillars. */
    val countsAsMobility: Boolean = false,
    /** From this level, sessions count as a Strength session on the longevity pillars. */
    val strengthFromLevel: Int? = null,
    val sessionsToAdvance: Int = 6
) {
    fun level(number: Int): ProgramLevel = levels[(number - 1).coerceIn(0, levels.lastIndex)]

    val safety: String get() = when (kind) {
        ProgramKind.BODY ->
            "Check with your doctor or physiotherapist before starting, especially if you have pain, a recent injury or surgery. " +
                "Only move as far as feels pain-free, and stop any exercise that hurts. This is general fitness guidance, not medical advice."
        ProgramKind.MIND ->
            "These are simple wellbeing habits, not treatment. If stress, anxiety, low mood or trouble concentrating is affecting your life, " +
                "please talk to a doctor or a mental health professional. Stop a breathing exercise if you feel dizzy or light-headed."
    } + (extraSafety?.let { " $it" } ?: "")
}

/** How a session felt - decides whether to move on, stay, or ease off. */
enum class ProgramFeel {
    EASY, OK, HARD;

    fun label(kind: ProgramKind): String = when (kind) {
        ProgramKind.BODY -> when (this) { EASY -> "Easy & pain-free"; OK -> "Challenging but OK"; HARD -> "Some pain" }
        ProgramKind.MIND -> when (this) { EASY -> "Felt easy and it helped"; OK -> "Took effort, still helped"; HARD -> "Didn't feel right" }
    }

    fun detail(kind: ProgramKind): String = when (kind) {
        ProgramKind.BODY -> when (this) { EASY -> "Everything felt controlled"; OK -> "Hard work, no pain"; HARD -> "Something hurt or felt wrong" }
        ProgramKind.MIND -> when (this) { EASY -> "Calmer or clearer afterwards"; OK -> "Hard to stick with, but worth it"; HARD -> "Dizzy, restless or worse afterwards" }
    }

    companion object {
        fun fromName(name: String?): ProgramFeel? = entries.firstOrNull { it.name == name }
    }
}

data class ProgramSession(val id: Long, val programId: String, val date: LocalDate, val level: Int, val feel: ProgramFeel)

data class ProgramSnapshot(
    val program: Program,
    val enabled: Boolean = false,
    val level: Int = 1,
    /** This program's sessions, newest first. */
    val sessions: List<ProgramSession> = emptyList(),
    val today: LocalDate = LocalDate.now()
) {
    val currentLevel: ProgramLevel get() = program.level(level)
    val sessionsAtLevel: List<ProgramSession> get() = sessions.filter { it.level == level }
    val sessionsThisWeek: Int get() = sessions.count { !it.date.isBefore(LongevityWeek.weekStart(today)) }
    val doneToday: Boolean get() = sessions.any { it.date == today }
    val advice: ProgramAdvice get() = ProgramAdvice.of(program, level, sessions)
}

sealed class ProgramAdvice {
    /** Keep going; [remaining] more sessions at this level before moving up. */
    data class KeepGoing(val remaining: Int) : ProgramAdvice()
    /** Enough sessions that felt easy - the user can choose to move up. */
    data object ReadyToMoveUp : ProgramAdvice()
    /** Top level, going well: keep it up as maintenance. */
    data object Maintain : ProgramAdvice()
    /** The last session didn't feel right: use the easier versions, stay at this level. */
    data object EaseOff : ProgramAdvice()
    /** Twice in a row: step back and check with a professional. */
    data object StepBack : ProgramAdvice()

    companion object {
        /** The last sessions that must have felt easy to move up. */
        const val EASY_SESSIONS_TO_ADVANCE = 2

        /** Never moves anyone up automatically - it only says when they could. */
        fun of(program: Program, level: Int, sessions: List<ProgramSession>): ProgramAdvice {
            val recent = sessions.take(2)
            if (recent.size == 2 && recent.all { it.feel == ProgramFeel.HARD }) return StepBack
            if (recent.firstOrNull()?.feel == ProgramFeel.HARD) return EaseOff
            val atLevel = sessions.filter { it.level == level }
            val lastEasy = atLevel.take(EASY_SESSIONS_TO_ADVANCE)
                .let { it.size == EASY_SESSIONS_TO_ADVANCE && it.all { s -> s.feel == ProgramFeel.EASY } }
            return when {
                atLevel.size >= program.sessionsToAdvance && lastEasy -> if (level >= program.levels.size) Maintain else ReadyToMoveUp
                else -> KeepGoing((program.sessionsToAdvance - atLevel.size).coerceAtLeast(if (lastEasy) 0 else 1))
            }
        }
    }
}
