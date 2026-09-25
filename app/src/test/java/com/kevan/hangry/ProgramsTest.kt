package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.calculation.LongevityCalculator
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.LongevityPillar
import com.kevan.hangry.domain.model.ProgramAdvice
import com.kevan.hangry.domain.model.ProgramCatalog
import com.kevan.hangry.domain.model.ProgramFeel
import com.kevan.hangry.domain.model.ProgramKind
import com.kevan.hangry.domain.model.ProgramSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgramsTest {

    private val start = LocalDate.of(2026, 9, 1)
    private val knees = ProgramCatalog.KNEES

    /** Newest first, like the repository returns them. */
    private fun sessions(level: Int, vararg feels: ProgramFeel) =
        feels.mapIndexed { i, f -> ProgramSession(i.toLong(), knees.id, start.plusDays(i * 2L), level, f) }.reversed()

    @Test
    fun `all eight programs are there with unique ids`() {
        val ids = ProgramCatalog.ALL.map { it.id }
        assertEquals(listOf("stress", "focus", "mobility", "shoulder", "back", "pull_up", "push_up", "knees"), ids)
    }

    @Test
    fun `every exercise has a how-to and an easier version, and levels are numbered in order`() {
        ProgramCatalog.ALL.forEach { p ->
            assertTrue(p.id, p.levels.size >= 3)
            assertEquals(p.id, (1..p.levels.size).toList(), p.levels.map { it.number })
            p.levels.forEach { l ->
                assertTrue("${p.id} ${l.number}", l.exercises.size in 3..6)
                l.exercises.forEach { e -> assertTrue("${p.id} ${e.id}", e.howTo.isNotBlank() && e.easier.isNotBlank() && e.dose.isNotBlank()) }
                assertEquals("${p.id} ${l.number} duplicate exercise ids", l.exercises.size, l.exercises.map { it.id }.toSet().size)
            }
        }
    }

    @Test
    fun `breathing steps point at real breathing patterns`() {
        val patterns = BreathingPattern.entries.map { it.id }.toSet()
        ProgramCatalog.ALL.flatMap { p -> p.levels.flatMap { it.exercises } }.mapNotNull { it.breathingPattern }
            .forEach { assertTrue(it, it in patterns) }
    }

    @Test
    fun `every program shows safety guidance, and back pain lists warning signs`() {
        ProgramCatalog.ALL.forEach { assertTrue(it.id, it.safety.contains("not") && it.safety.length > 80) }
        assertTrue(ProgramCatalog.BACK.safety.contains("bladder"))
        assertEquals(ProgramKind.MIND, ProgramCatalog.STRESS.kind)
    }

    @Test
    fun `new starters keep going at level 1`() {
        assertEquals(ProgramAdvice.KeepGoing(6), ProgramAdvice.of(knees, 1, emptyList()))
    }

    @Test
    fun `six sessions ending with two easy ones can move up`() {
        val s = sessions(1, ProgramFeel.OK, ProgramFeel.OK, ProgramFeel.OK, ProgramFeel.OK, ProgramFeel.EASY, ProgramFeel.EASY)
        assertEquals(ProgramAdvice.ReadyToMoveUp, ProgramAdvice.of(knees, 1, s))
    }

    @Test
    fun `sessions that still feel hard don't move up`() {
        val s = sessions(1, *Array(6) { ProgramFeel.OK })
        assertTrue(ProgramAdvice.of(knees, 1, s) is ProgramAdvice.KeepGoing)
    }

    @Test
    fun `a bad session means ease off, two means step back`() {
        assertEquals(ProgramAdvice.EaseOff, ProgramAdvice.of(knees, 2, sessions(2, ProgramFeel.EASY, ProgramFeel.HARD)))
        assertEquals(ProgramAdvice.StepBack, ProgramAdvice.of(knees, 2, sessions(2, ProgramFeel.HARD, ProgramFeel.HARD)))
    }

    @Test
    fun `the top level turns into maintenance`() {
        assertEquals(ProgramAdvice.Maintain, ProgramAdvice.of(knees, 4, sessions(4, *Array(6) { ProgramFeel.EASY })))
    }

    @Test
    fun `mind programs need a week of sessions before moving up`() {
        assertEquals(ProgramAdvice.KeepGoing(7), ProgramAdvice.of(ProgramCatalog.STRESS, 1, emptyList()))
    }

    @Test
    fun `program sessions count toward strength and mobility on the longevity pillars`() {
        val monday = LocalDate.of(2026, 9, 21)
        val week = LongevityCalculator.week(
            monday, emptyList(), emptyList(), emptyList(), HeartRateZones.DEFAULT, emptyMap(), ZoneOffset.UTC,
            extraStrengthDays = setOf(monday, monday.plusDays(2), monday.minusDays(3)),
            extraMobilityDays = setOf(monday.plusDays(1))
        )
        assertEquals(2, week.of(LongevityPillar.STRENGTH).value)
        assertEquals(1, week.of(LongevityPillar.MOBILITY).value)
    }
}
