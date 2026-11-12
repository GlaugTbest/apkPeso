package br.com.glaugtbest.gtheday

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GtheDayApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, DayDatabase::class.java, "gtheday.db").build()
    }
    val days get() = database.days()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminders by lazy { Reminders(this) }
    private val operations = Mutex()

    suspend fun changeDay(
        date: LocalDate,
        habit: Habit? = null,
        change: (DayRecord) -> DayRecord
    ): DayRecord = operations.withLock {
        val previous = database.withTransaction {
            val current = days.read(date.toString()) ?: DayRecord(date.toString())
            days.write(change(current))
            current
        }
        if (habit != null && date == LocalDate.now()) reminders.reconcile(habit)
        previous
    }

    suspend fun refreshReminders() = operations.withLock {
        Habit.entries.forEach { reminders.reconcile(it) }
    }

    suspend fun setReminder(habit: Habit, enabled: Boolean) = operations.withLock {
        reminders.setEnabled(habit, enabled)
        reminders.reconcile(habit)
    }

    suspend fun handleReminder(action: String?, habit: Habit?, date: LocalDate?, at: Long) =
        operations.withLock {
            when (action) {
                Reminders.FIRE -> if (habit != null && date != null) reminders.fire(habit, date, at)
                Reminders.SNOOZE -> if (habit != null && date != null) reminders.snooze(habit, date)
                else -> Habit.entries.forEach { reminders.reconcile(it) }
            }
        }
}
