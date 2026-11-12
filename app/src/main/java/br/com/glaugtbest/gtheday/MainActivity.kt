package br.com.glaugtbest.gtheday

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as GtheDayApp
    private var incoming by mutableStateOf<ReminderDestination?>(null)
    private var systemStateVersion by mutableIntStateOf(0)
    private val notificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSystemState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) acceptIntent(intent)
        setContent {
            GtheDayTheme {
                RoutineApp(
                    app = app,
                    incoming = incoming,
                    onIncomingHandled = { incoming = null },
                    systemStateVersion = systemStateVersion,
                    onEnableNotifications = ::enableNotifications,
                    onEnableExactAlarms = ::enableExactAlarms
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()
    }

    private fun refreshSystemState() {
        systemStateVersion++
        lifecycleScope.launch { app.refreshReminders() }
    }

    private fun acceptIntent(intent: Intent) {
        if (intent.action != Reminders.OPEN) return
        val habit = Habit.fromId(intent.getIntExtra(Reminders.HABIT, 0)) ?: return
        val date = parseDate(intent.getStringExtra(Reminders.DATE))
            ?.takeUnless { it.isAfter(LocalDate.now()) } ?: return
        incoming = ReminderDestination(habit, date)
    }

    private fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val settings = getPreferences(MODE_PRIVATE)
            if (!settings.getBoolean("requestedNotifications", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                settings.edit().putBoolean("requestedNotifications", true).apply()
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun enableExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName)))
        }
    }
}

data class ReminderDestination(val habit: Habit, val date: LocalDate)
