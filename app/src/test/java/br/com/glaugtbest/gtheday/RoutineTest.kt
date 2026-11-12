package br.com.glaugtbest.gtheday

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTest {
    private val date = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.of("America/Fortaleza")

    @Test
    fun weekBeginsMondayAndContainsSevenDates() {
        val dates = weekDays(date)
        assertEquals(LocalDate.of(2026, 11, 9), dates.first())
        assertEquals(LocalDate.of(2026, 11, 15), dates.last())
        assertEquals(7, dates.size)
    }

    @Test
    fun weeklySummaryUsesOnlyRecordedWeightsAndCardioMinutes() {
        val summary = summarize(listOf(
            DayRecord("2026-11-09", strength = true, cardio = 20, weight = 80.0),
            DayRecord("2026-11-10", cardio = 35),
            DayRecord("2026-11-11", weight = 79.0)
        ))
        assertEquals(79.5, summary.weightAverage!!, 0.0)
        assertEquals(55, summary.cardioMinutes)
        assertEquals(2, summary.cardioSessions)
        assertEquals(1, summary.strengthSessions)
        assertNull(summarize(listOf(DayRecord(date.toString()))).weightAverage)
    }

    @Test
    fun completionsAreIndependentAndEmptyMealsDoNotCount() {
        val row = DayRecord(date.toString(), water = true, lunch = "Arroz e feijão", dinner = " ")
        assertTrue(row.isDone(Habit.WATER))
        assertTrue(row.isDone(Habit.LUNCH))
        assertFalse(row.isDone(Habit.DINNER))
        assertFalse(row.isDone(Habit.STRENGTH))
        assertFalse(DayRecord(date.plusDays(1).toString()).isDone(Habit.WATER))
    }

    @Test
    fun aCompletedOrElapsedOccurrenceSchedulesTomorrow() {
        val beforeLunch = ZonedDateTime.of(2026, 11, 12, 12, 29, 0, 0, zone)
        val lunch = beforeLunch.plusMinutes(1)
        assertEquals(lunch, nextDaily(Habit.LUNCH, beforeLunch, completed = false))
        assertEquals(lunch.plusDays(1), nextDaily(Habit.LUNCH, beforeLunch, completed = true))
        assertEquals(lunch.plusDays(1), nextDaily(Habit.LUNCH, lunch.plusMinutes(1), completed = false))
    }
}
