package br.com.glaugtbest.gtheday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class Habit(val id: Int, val title: String, val time: LocalTime, val enabledByDefault: Boolean) {
    WATER(1, "Água", LocalTime.of(10, 0), true),
    CREATINE(2, "Creatina", LocalTime.of(9, 0), true),
    STRENGTH(3, "Musculação", LocalTime.of(18, 30), true),
    CARDIO(4, "Cardio", LocalTime.of(19, 0), false),
    BREAKFAST(5, "Café da manhã", LocalTime.of(8, 0), false),
    LUNCH(6, "Almoço", LocalTime.of(12, 30), true),
    SNACK(7, "Lanche", LocalTime.of(16, 0), false),
    DINNER(8, "Jantar", LocalTime.of(20, 0), true);

    val isMeal: Boolean get() = this in meals

    val notificationText: String
        get() = when (this) {
            WATER -> "Hora de beber água."
            CREATINE -> "Já tomou a creatina hoje?"
            STRENGTH -> "Hora de treinar."
            CARDIO -> "Hora de fazer cardio."
            else -> "Toque para registrar o que comeu."
        }

    companion object {
        val meals = listOf(BREAKFAST, LUNCH, SNACK, DINNER)
        fun fromId(id: Int): Habit? = entries.firstOrNull { it.id == id }
    }
}

data class WeekSummary(
    val strengthSessions: Int,
    val cardioSessions: Int,
    val cardioMinutes: Int,
    val weightAverage: Double?
)

fun weekDays(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { monday.plusDays(it.toLong()) }
}

fun summarize(records: List<DayRecord>): WeekSummary {
    val weights = records.mapNotNull { it.weight }
    return WeekSummary(
        strengthSessions = records.count { it.strength },
        cardioSessions = records.count { it.cardio != null },
        cardioMinutes = records.sumOf { it.cardio ?: 0 },
        weightAverage = weights.takeIf { it.isNotEmpty() }?.average()
    )
}

fun nextDaily(habit: Habit, now: ZonedDateTime, completed: Boolean): ZonedDateTime {
    val date = now.toLocalDate()
    val today = date.atTime(habit.time).atZone(now.zone)
    return if (!completed && today.isAfter(now)) today
        else date.plusDays(1).atTime(habit.time).atZone(now.zone)
}

fun parseDate(value: String?): LocalDate? = value?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
}

fun outstandingDaily(
    habit: Habit,
    now: ZonedDateTime,
    suppressed: Boolean,
    scheduledAt: Long
): ZonedDateTime {
    val today = now.toLocalDate().atTime(habit.time).atZone(now.zone)
    if (!suppressed && today.toInstant().toEpochMilli() == scheduledAt) return today
    return nextDaily(habit, now, completed = suppressed)
}
