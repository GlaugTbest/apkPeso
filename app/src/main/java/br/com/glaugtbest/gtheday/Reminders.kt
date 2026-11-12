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

    suspend fun reconcile(habit: Habit, afterFiring: Boolean = false) {
        val now = ZonedDateTime.now()
        val date = now.toLocalDate()
        val done = app.days.read(date.toString())?.isDone(habit) == true
        val active = enabled(habit) && canNotify()
        if (!active || done) {
            clearSnooze(habit)
            notifications.cancel(habit.id)
        }
        if (!active) {
            val existing = PendingIntent.getBroadcast(
                app, habit.id, broadcast(FIRE, habit, date), PendingIntent.FLAG_NO_CREATE or IMMUTABLE
            )
            if (existing != null) {
                alarms.cancel(existing)
                existing.cancel()
            }
            return
        }

        val snoozeDate = preferences.getString("snooze_date_" + habit.id, null)
        val snoozeAt = preferences.getLong("snooze_at_" + habit.id, 0)
        if (snoozeDate == date.toString() && snoozeAt > 0 && !done) {
            schedule(habit, date, snoozeAt.coerceAtLeast(System.currentTimeMillis() + 500))
            return
        }
        if (snoozeDate != null) clearSnooze(habit)
        val next = nextDaily(habit, now, completed = done || afterFiring)
        schedule(habit, next.toLocalDate(), next.toInstant().toEpochMilli())
    }

    suspend fun fire(habit: Habit, date: LocalDate, at: Long) {
        val today = LocalDate.now()
        if (date != today || !enabled(habit) || !canNotify() ||
            app.days.read(date.toString())?.isDone(habit) == true
        ) {
            reconcile(habit)
            return
        }
        if (preferences.getString("snooze_date_" + habit.id, null) == date.toString()) {
            if (at < preferences.getLong("snooze_at_" + habit.id, 0)) {
                reconcile(habit)
                return
            }
            clearSnooze(habit)
        }

        val open = Intent(app, MainActivity::class.java)
            .setAction(OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HABIT, habit.id)
            .putExtra(DATE, date.toString())
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(habit.title)
            .setContentText(habit.notificationText)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(app, habit.id, open, UPDATE))
        if (habit.isMeal) {
            val later = PendingIntent.getBroadcast(app, habit.id, broadcast(SNOOZE, habit, date), UPDATE)
            notification.addAction(R.drawable.ic_reminder, "Lembrar em 30 min", later)
        }
        notifications.notify(habit.id, notification.build())
        reconcile(habit, afterFiring = true)
    }

    suspend fun snooze(habit: Habit, date: LocalDate) {
        if (!habit.isMeal || date != LocalDate.now() || !enabled(habit) || !canNotify() ||
            app.days.read(date.toString())?.isDone(habit) == true
        ) return
        val next = ZonedDateTime.now().plusMinutes(30)
        notifications.cancel(habit.id)
        preferences.edit()
            .putString("snooze_date_" + habit.id, next.toLocalDate().toString())
            .putLong("snooze_at_" + habit.id, next.toInstant().toEpochMilli())
            .apply()
        schedule(habit, next.toLocalDate(), next.toInstant().toEpochMilli())
    }

    private fun clearSnooze(habit: Habit) {
        preferences.edit().remove("snooze_date_" + habit.id).remove("snooze_at_" + habit.id).apply()
    }

    private fun schedule(habit: Habit, date: LocalDate, time: Long) {
        val intent = broadcast(FIRE, habit, date).putExtra(AT, time)
        val pending = PendingIntent.getBroadcast(app, habit.id, intent, UPDATE)
        if (canScheduleExactly()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                return
            } catch (_: SecurityException) {
                // Access can be revoked between checking and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
    }

    private fun broadcast(action: String, habit: Habit, date: LocalDate) =
        Intent(app, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(HABIT, habit.id)
            .putExtra(DATE, date.toString())

    companion object {
        const val FIRE = "br.com.glaugtbest.gtheday.FIRE"
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
