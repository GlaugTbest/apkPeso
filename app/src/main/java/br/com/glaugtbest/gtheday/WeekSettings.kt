package br.com.glaugtbest.gtheday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeekScreen(
    today: LocalDate,
    days: List<LocalDate>,
    records: List<DayRecord>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onMeasures: () -> Unit
) {
    val summary = summarize(records)
    val byDate = records.associateBy { it.date }
    Page {
        PageTitle("Semana")
        Surface(shape = panelShape, color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Semana anterior")
                }
                Text(days.first().format(shortDate) + " a " + days.last().format(shortDate),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNext, enabled = days.last().isBefore(today)) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Próxima semana")
                }
            }
        }
        Panel {
            Text("Resumo", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val workoutCount = summary.strengthSessions
                val workouts = workoutCount.toString() + if (workoutCount == 1) " treino" else " treinos"
                Metric("Musculação", workouts, Modifier.weight(1f))
                Metric("Cardio", summary.cardioSessions.toString() + " · " +
                    summary.cardioMinutes + " min", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Peso médio", summary.weightAverage?.let { number(it) + " kg" } ?: "—",
                    Modifier.weight(1f))
                val waist = records.lastOrNull { it.waist != null }?.waist
                Metric("Última cintura", waist?.let { number(it) + " cm" } ?: "—", Modifier.weight(1f))
            }
        }
        Button(onClick = onMeasures, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Text("Registrar peso e cintura")
        }
        Text("Histórico por dia", style = MaterialTheme.typography.titleLarge)
        days.asReversed().filter { !it.isAfter(today) }.forEach { date ->
            val row = byDate[date.toString()]
            val details = buildList {
                if (row?.strength == true) add("Treino")
                row?.cardio?.let { add("Cardio " + it + " min") }
                row?.weight?.let { add(number(it) + " kg") }
                val meals = Habit.meals.count { row?.isDone(it) == true }
                if (meals > 0) add(if (meals == 1) "1 refeição" else meals.toString() + " refeições")
                if (row?.water == true) add("Água")
                if (row?.creatine == true) add("Creatina")
                row?.waist?.let { add(number(it) + " cm") }
            }
            Surface(onClick = { onDay(date) }, shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dateTitle(date), style = MaterialTheme.typography.titleMedium)
                        Text(details.joinToString(" · ").ifEmpty { "Sem registros" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Editar dia")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    preciseAlarms: Boolean,
    onEnableNotifications: () -> Unit,
    onEnableExactAlarms: () -> Unit,
    enabled: (Habit) -> Boolean,
    onChange: (Habit, Boolean) -> Unit
) {
    Page {
        PageTitle("Configurações")
        Panel {
            Text(if (notificationsEnabled) "Notificações permitidas" else "Notificações bloqueadas",
                style = MaterialTheme.typography.titleMedium)
            if (!notificationsEnabled) {
                Text("Libere as notificações do GtheDay no Android para receber os avisos.")
                TextButton(onClick = onEnableNotifications) { Text("Permitir notificações") }
            } else if (!preciseAlarms) {
                Text("O Android pode atrasar um lembrete. Para receber no horário, permita alarmes exatos.")
                TextButton(onClick = onEnableExactAlarms) { Text("Permitir alarmes exatos") }
            }
        }
        Text("Lembretes diários", style = MaterialTheme.typography.titleLarge)
        Panel {
            Habit.entries.sortedBy { it.time }.forEachIndexed { index, habit ->
                var checked by remember(habit) { mutableStateOf(enabled(habit)) }
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(habit.title, style = MaterialTheme.typography.titleMedium)
                        Text(habit.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = checked,
                        modifier = Modifier.semantics { contentDescription = "Lembrete de " + habit.title },
                        onCheckedChange = { checked = it; onChange(habit, it) }
                    )
                }
            }
        }
    }
}
