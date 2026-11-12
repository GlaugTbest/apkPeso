package br.com.glaugtbest.gtheday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val MEASURES = "measures"
private val portuguese = Locale.forLanguageTag("pt-BR")
internal val shortDate = DateTimeFormatter.ofPattern("dd/MM", portuguese)
internal val fullDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", portuguese)
internal fun number(value: Double) = String.format(portuguese, "%.1f", value)
internal fun dateTitle(date: LocalDate) =
    date.format(fullDate).replaceFirstChar { it.titlecase(portuguese) }
internal val panelShape = RoundedCornerShape(20.dp)

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        content = content
    )
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = panelShape, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
internal fun PageTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DayScreen(
    date: LocalDate,
    record: DayRecord,
    history: Boolean,
    onBack: () -> Unit,
    showNotificationPrompt: Boolean,
    onEnableNotifications: () -> Unit,
    onToggle: (Habit) -> Unit,
    onCardio: (Int) -> Unit,
    onEdit: (String) -> Unit
) {
    Page {
        if (history) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text("Voltar para a semana", Modifier.padding(start = 8.dp))
            }
        }
        PageTitle(if (history) date.format(shortDate) else "Hoje", dateTitle(date))
        val completed = listOf(Habit.WATER, Habit.CREATINE, Habit.STRENGTH, Habit.CARDIO).count(record::isDone)
        Surface(
            shape = panelShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hábitos do dia", style = MaterialTheme.typography.titleMedium)
                Text(completed.toString() + " de 4 concluídos", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Panel {
            HabitRow(Habit.WATER.title, record.water, "Bebi água", onClick = { onToggle(Habit.WATER) })
            HorizontalDivider()
            HabitRow(Habit.CREATINE.title, record.creatine, "Tomei", onClick = { onToggle(Habit.CREATINE) })
            HorizontalDivider()
            HabitRow(Habit.STRENGTH.title, record.strength, "Treinei", onClick = { onToggle(Habit.STRENGTH) })
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cardio", style = MaterialTheme.typography.titleMedium)
                record.cardio?.let { Text(it.toString() + " min registrados") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (minutes in listOf(20, 30)) {
                    FilledTonalButton(onClick = { onCardio(minutes) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(minutes.toString() + " min") }
                }
                OutlinedButton(onClick = { onEdit(Habit.CARDIO.name) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Outro") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Refeições", style = MaterialTheme.typography.titleLarge)
            Habit.meals.forEach { meal ->
                val entry = record.meal(meal)
                Surface(
                    onClick = { onEdit(meal.name) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 57.dp).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(meal.title, style = MaterialTheme.typography.titleMedium)
                            if (entry.isNotEmpty()) {
                                Text(entry, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (entry.isBlank()) {
                            Text("Registrar", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Registrado",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (history) {
            OutlinedButton(onClick = { onEdit(MEASURES) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Editar peso e cintura")
            }
        }
        if (showNotificationPrompt) {
            Panel {
                Text("Ative os lembretes", style = MaterialTheme.typography.titleMedium)
                Text("Permita notificações para receber avisos com o app fechado.")
                TextButton(onClick = onEnableNotifications) { Text("Permitir notificações") }
            }
        }
    }
}

@Composable
private fun HabitRow(title: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (done) {
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("Feito", Modifier.padding(start = 6.dp))
            }
        } else {
            Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}
