package br.com.glaugtbest.gtheday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun EditEntry(
    date: LocalDate,
    kind: String,
    record: DayRecord,
    onBack: () -> Unit,
    onSave: (Habit?, String, (DayRecord) -> DayRecord) -> Unit
) {
    val habit = if (kind == MEASURES) null else Habit.valueOf(kind)
    val isMeal = habit?.isMeal == true
    val initial = when {
        habit == null -> record.weight?.toString()?.replace('.', ',').orEmpty()
        habit == Habit.CARDIO -> record.cardio?.toString().orEmpty()
        else -> record.meal(habit)
    }
    var primary by rememberSaveable { mutableStateOf(initial) }
    var secondary by rememberSaveable {
        mutableStateOf(record.waist?.toString()?.replace('.', ',').orEmpty())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun finish() {
        if (saving) return
        val text = primary.trim()
        val message: String
        val change: (DayRecord) -> DayRecord
        when {
            habit == null -> {
                val weight = decimal(text)
                val waist = decimal(secondary)
                if ((text.isNotEmpty() && weight == null) || (secondary.isNotBlank() && waist == null)) {
                    error = "Use um número maior que zero em cada campo preenchido."
                    return
                }
                message = "Medidas salvas"
                change = { it.copy(weight = weight, waist = waist) }
            }
            habit == Habit.CARDIO -> {
                val minutes = text.toIntOrNull()
                if (text.isNotEmpty() && (minutes == null || minutes !in 1..1440)) {
                    error = "Informe de 1 a 1440 minutos."
                    return
                }
                message = if (minutes == null) "Cardio removido" else "Cardio salvo"
                change = { it.copy(cardio = minutes) }
            }
            else -> {
                message = if (text.isEmpty()) "Refeição removida" else "Refeição salva"
                change = { it.withMeal(habit, text) }
            }
        }
        saving = true
        keyboard?.hide()
        onSave(habit, message, change)
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (isMeal) {
            delay(150)
            keyboard?.show()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Text(habit?.title ?: "Peso e cintura", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(date.format(fullDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val label = when {
                    habit == null -> "Peso (kg)"
                    habit == Habit.CARDIO -> "Duração (min)"
                    else -> "O que você comeu?"
                }
                val keyboardType = when {
                    habit == null -> KeyboardType.Decimal
                    habit == Habit.CARDIO -> KeyboardType.Number
                    else -> KeyboardType.Text
                }
                OutlinedTextField(
                    value = primary,
                    onValueChange = {
                        primary = it.replace('\n', ' ').take(if (isMeal) 240 else 12)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(label) },
                    singleLine = !isMeal,
                    minLines = if (isMeal) 2 else 1,
                    maxLines = if (isMeal) 4 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finish() })
                )
                if (habit == null) {
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it.take(12); error = null },
                        label = { Text("Cintura (cm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish() })
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (habit != null && initial.isNotBlank()) {
                    TextButton(onClick = { primary = ""; finish() }) { Text("Remover registro") }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(onClick = ::finish, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                        .heightIn(min = 52.dp)) {
                    Text("Salvar")
                }
            }
        }
    }
}

private fun decimal(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
