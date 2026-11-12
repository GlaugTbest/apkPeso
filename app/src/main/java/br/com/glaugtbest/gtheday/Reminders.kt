package br.com.glaugtbest.gtheday

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

class Reminders(private val app: GtheDayApp) {
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val preferences = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lembretes diários", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun enabled(habit: Habit): Boolean =
        preferences.getBoolean("enabled_" + habit.id, habit.enabledByDefault)

    fun setEnabled(habit: Habit, enabled: Boolean) {
        preferences.edit().putBoolean("enabled_" + habit.id, enabled).apply()
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                app, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canScheduleExactly(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    suspend fun reconcile(habit: Habit, restore: Boolean = false) {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val id = habit.id
        val active = enabled(habit) && canNotify()
        if (!active) {
            clearSnooze(habit)
            preferences.edit().remove("daily_at_" + id).apply()
            notifications.cancel(id)
            cancelAlarm(FIRE, habit)
            cancelAlarm(FIRE_SNOOZE, habit)
            return
        }

        val done = completed(habit, today)
        val visibleDate = parseDate(preferences.getString("visible_date_" + id, null))
        if (visibleDate != null && (visibleDate.isBefore(today.minusDays(1)) ||
                (if (visibleDate == today) done else completed(habit, visibleDate)))
        ) {
            notifications.cancel(id)
            preferences.edit().remove("visible_date_" + id).apply()
        }

        val delivered = preferences.getString("delivered_date_" + id, null) == today.toString()
        val previousDaily = preferences.getLong("daily_at_" + id, 0)
        val next = outstandingDaily(habit, now, done || delivered, previousDaily)
        val dailyAt = next.toInstant().toEpochMilli()
        preferences.edit().putLong("daily_at_" + id, dailyAt).apply()
        schedule(FIRE, habit, next.toLocalDate(), dailyAt, restore || dailyAt != previousDaily)

        val source = parseDate(preferences.getString("snooze_date_" + id, null))
        val snoozeAt = preferences.getLong("snooze_at_" + id, 0)
        val targetDay = java.time.Instant.ofEpochMilli(snoozeAt).atZone(now.zone).toLocalDate()
        if (source != null && !source.isAfter(today) && !targetDay.isBefore(today) &&
            !(if (source == today) done else completed(habit, source))
        ) {
            schedule(FIRE_SNOOZE, habit, source, snoozeAt, restore)
        } else {
            clearSnooze(habit)
            cancelAlarm(FIRE_SNOOZE, habit)
        }
    }

    suspend fun fire(habit: Habit, date: LocalDate, at: Long, snoozed: Boolean) {
        val today = LocalDate.now()
        val id = habit.id
        val matches = if (snoozed) {
            preferences.getString("snooze_date_" + id, null) == date.toString() &&
                preferences.getLong("snooze_at_" + id, 0) == at &&
                !java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().isBefore(today)
        } else {
            date == today && preferences.getLong("daily_at_" + id, 0) == at &&
                preferences.getString("delivered_date_" + id, null) != date.toString()
        }
        if (!matches || !enabled(habit) || !canNotify() || completed(habit, date)) {
            reconcile(habit)
            return
        }

        val open = Intent(app, MainActivity::class.java)
            .setAction(OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HABIT, id)
            .putExtra(DATE, date.toString())
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(habit.title)
            .setContentText(habit.notificationText)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(app, id, open, UPDATE))
        if (habit.isMeal) {
            val later = PendingIntent.getBroadcast(app, id, broadcast(SNOOZE, habit, date), UPDATE)
            notification.addAction(R.drawable.ic_reminder, "Lembrar em 30 min", later)
        }
        notifications.notify(id, notification.build())

        if (snoozed) clearSnooze(habit)
        val changed = preferences.edit().putString("visible_date_" + id, date.toString())
        if (!snoozed) changed.putString("delivered_date_" + id, date.toString())
        changed.apply()
        reconcile(habit)
    }

    suspend fun snooze(habit: Habit, date: LocalDate) {
        val today = LocalDate.now()
        val id = habit.id
        if (!habit.isMeal || date.isAfter(today) || date.isBefore(today.minusDays(1)) ||
            preferences.getString("visible_date_" + id, null) != date.toString() ||
            !enabled(habit) || !canNotify() || completed(habit, date)
        ) return
        val at = System.currentTimeMillis() + 30 * 60 * 1_000L
        notifications.cancel(id)
        preferences.edit()
            .remove("visible_date_" + id)
            .putString("snooze_date_" + id, date.toString())
            .putLong("snooze_at_" + id, at)
            .apply()
        schedule(FIRE_SNOOZE, habit, date, at, restore = true)
    }

    private suspend fun completed(habit: Habit, date: LocalDate): Boolean =
        app.days.read(date.toString())?.isDone(habit) == true

    private fun clearSnooze(habit: Habit) {
        preferences.edit().remove("snooze_date_" + habit.id).remove("snooze_at_" + habit.id).apply()
    }

    private fun existingAlarm(action: String, habit: Habit): PendingIntent? =
        PendingIntent.getBroadcast(
            app, habit.id, broadcast(action, habit, LocalDate.now()), PendingIntent.FLAG_NO_CREATE or IMMUTABLE
        )

    private fun cancelAlarm(action: String, habit: Habit) {
        val existing = existingAlarm(action, habit) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    private fun schedule(action: String, habit: Habit, date: LocalDate, at: Long, restore: Boolean) {
        val now = System.currentTimeMillis()
        val exact = canScheduleExactly()
        val exactKey = "exact_" + action + "_" + habit.id
        // An inexact alarm can still be waiting in Android after its requested time.
        if (!restore && at <= now && exact == preferences.getBoolean(exactKey, false) &&
            existingAlarm(action, habit) != null
        ) return

        val intent = broadcast(action, habit, date).putExtra(AT, at)
        val pending = PendingIntent.getBroadcast(app, habit.id, intent, UPDATE)
        val trigger = at.coerceAtLeast(now + 500)
        if (exact) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                preferences.edit().putBoolean(exactKey, true).apply()
                return
            } catch (_: SecurityException) {
                // Access can be revoked between checking and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        preferences.edit().putBoolean(exactKey, false).apply()
    }

    private fun broadcast(action: String, habit: Habit, date: LocalDate) =
        Intent(app, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(HABIT, habit.id)
            .putExtra(DATE, date.toString())

    companion object {
        const val FIRE = "br.com.glaugtbest.gtheday.FIRE"
        const val FIRE_SNOOZE = "br.com.glaugtbest.gtheday.FIRE_SNOOZE"
        const val SNOOZE = "br.com.glaugtbest.gtheday.SNOOZE"
        const val OPEN = "br.com.glaugtbest.gtheday.OPEN"
        const val HABIT = "habit"
        const val DATE = "date"
        const val AT = "at"
        const val CHANNEL = "daily"
        private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
        private const val UPDATE = PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as GtheDayApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.handleReminder(
                    intent.action,
                    Habit.fromId(intent.getIntExtra(Reminders.HABIT, 0)),
                    parseDate(intent.getStringExtra(Reminders.DATE)),
                    intent.getLongExtra(Reminders.AT, 0)
                )
            } catch (failure: Exception) {
                Log.e("GtheDay", "Falha ao atualizar lembretes", failure)
            } finally {
                pending.finish()
            }
        }
    }
}
