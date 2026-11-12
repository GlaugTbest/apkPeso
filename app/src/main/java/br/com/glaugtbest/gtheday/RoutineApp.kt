package br.com.glaugtbest.gtheday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val title: String) { TODAY("Hoje"), WEEK("Semana"), SETTINGS("Configurações") }

@Composable
fun RoutineApp(
    app: GtheDayApp,
    incoming: ReminderDestination?,
    onIncomingHandled: () -> Unit,
    systemStateVersion: Int,
    onEnableNotifications: () -> Unit,
    onEnableExactAlarms: () -> Unit
) {
    val today by produceState(LocalDate.now(), systemStateVersion) {
        while (true) {
            val current = LocalDate.now()
            if (current != value) {
                value = current
                app.refreshReminders()
            }
            delay(1_000)
        }
    }
    var tabName by rememberSaveable { mutableStateOf(Tab.TODAY.name) }
    val tab = Tab.valueOf(tabName)
    var weekDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pastDay by rememberSaveable { mutableStateOf<String?>(null) }
    var editDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editKind by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationsEnabled = remember(systemStateVersion) { app.reminders.canNotify() }
    val preciseAlarms = remember(systemStateVersion) { app.reminders.canScheduleExactly() }

    fun openEditor(date: LocalDate, kind: String) {
        editDate = date.toString()
        editKind = kind
    }

    fun closeEditor() {
        editDate = null
        editKind = null
    }

    fun save(
        date: LocalDate,
        habit: Habit?,
        message: String,
        onSaved: () -> Unit = {},
        change: (DayRecord) -> DayRecord
    ) {
        scope.launch {
            val previous = app.changeDay(date, habit, change)
            onSaved()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Desfazer", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                app.changeDay(date, habit) { latest ->
                    if (habit == null) latest.copy(weight = previous.weight, waist = previous.waist)
                    else latest.restoreHabit(habit, previous)
                }
            }
        }
    }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            if (incoming.date != today) {
                tabName = Tab.WEEK.name
                weekDate = incoming.date.toString()
                pastDay = incoming.date.toString()
            } else {
                tabName = Tab.TODAY.name
                pastDay = null
            }
            if (incoming.habit.isMeal || incoming.habit == Habit.CARDIO) {
                openEditor(incoming.date, incoming.habit.name)
            } else closeEditor()
            onIncomingHandled()
        }
    }

    val dateBeingEdited = parseDate(editDate)
    val editor = editKind
    val dateFromHistory = parseDate(pastDay)
    BackHandler(dateBeingEdited != null || dateFromHistory != null) {
        if (dateBeingEdited != null) closeEditor() else pastDay = null
    }

    if (dateBeingEdited != null && editor != null) {
        key(dateBeingEdited, editor) {
            val record by remember(dateBeingEdited) { app.days.observe(dateBeingEdited.toString()) }
                .collectAsStateWithLifecycle(initialValue = null)
            val loaded = remember(dateBeingEdited) { mutableStateOf<DayRecord?>(null) }
            LaunchedEffect(dateBeingEdited) {
                loaded.value = app.days.read(dateBeingEdited.toString()) ?: DayRecord(dateBeingEdited.toString())
            }
            val ready = record ?: loaded.value
            if (ready != null) {
                EditEntry(
                    date = dateBeingEdited,
                    kind = editor,
                    record = ready,
                    onBack = ::closeEditor,
                    onSave = { habit, message, change ->
                        save(dateBeingEdited, habit, message, ::closeEditor, change)
                    }
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    val icon = when (item) {
                        Tab.TODAY -> Icons.Filled.Home
                        Tab.WEEK -> Icons.Filled.DateRange
                        Tab.SETTINGS -> Icons.Filled.Settings
                    }
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = {
                            tabName = item.name
                            pastDay = null
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(item.title, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY, Tab.WEEK -> {
                    val shown = if (tab == Tab.WEEK) dateFromHistory else today
                    if (shown != null) key(shown, tab) {
                        val record by remember(shown) { app.days.observe(shown.toString()) }
                            .collectAsStateWithLifecycle(initialValue = null)
                        DayScreen(
                            date = shown,
                            record = record ?: DayRecord(shown.toString()),
                            history = tab == Tab.WEEK,
                            onBack = { pastDay = null },
                            showNotificationPrompt = tab == Tab.TODAY && !notificationsEnabled,
                            onEnableNotifications = onEnableNotifications,
                            onToggle = { habit ->
                                val message = when (habit) {
                                    Habit.WATER -> "Água atualizada"
                                    Habit.CREATINE -> "Creatina atualizada"
                                    else -> "Musculação atualizada"
                                }
                                save(shown, habit, message) { it.toggle(habit) }
                            },
                            onCardio = { minutes ->
                                save(shown, Habit.CARDIO, "Cardio salvo") { it.copy(cardio = minutes) }
                            },
                            onEdit = { openEditor(shown, it) }
                        )
                    } else {
                        val anchor = parseDate(weekDate) ?: today
                        val days = remember(anchor) { weekDays(anchor) }
                        val records by remember(days) {
                            app.days.observeWeek(days.first().toString(), days.last().toString())
                        }.collectAsStateWithLifecycle(initialValue = emptyList())
                        WeekScreen(
                            today = today,
                            days = days,
                            records = records,
                            onPrevious = { weekDate = anchor.minusWeeks(1).toString() },
                            onNext = { weekDate = anchor.plusWeeks(1).toString() },
                            onDay = { pastDay = it.toString() },
                            onMeasures = {
                                val date = if (today in days) today else days.last()
                                openEditor(date, MEASURES)
                            }
                        )
                    }
                }

                Tab.SETTINGS -> SettingsScreen(
                    notificationsEnabled = notificationsEnabled,
                    preciseAlarms = preciseAlarms,
                    onEnableNotifications = onEnableNotifications,
                    onEnableExactAlarms = onEnableExactAlarms,
                    enabled = app.reminders::enabled,
                    onChange = { habit, enabled -> scope.launch { app.setReminder(habit, enabled) } }
                )
            }
        }
    }
}
