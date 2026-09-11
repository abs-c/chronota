package app.chronotation.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.chronotation.R
import app.chronotation.data.entity.TimerMode
import app.chronotation.data.repository.AppPreferences
import app.chronotation.ui.components.*
import app.chronotation.ui.theme.*

@Composable fun TimerSettings(value: AppPreferences, dismiss: () -> Unit, save: (TimerMode, Int, Int, Int, () -> Unit) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(value.timerMode) }
    var work by rememberSaveable { mutableStateOf(value.workMinutes.toString()) }
    var rest by rememberSaveable { mutableStateOf(value.breakMinutes.toString()) }
    var cycles by rememberSaveable { mutableStateOf(value.timerCycles.toString()) }
    var error by remember { mutableStateOf(false) }
    EditorSheet(R.string.timer_settings, dismiss) {
        OptionGroup {
        ChoiceField(R.string.default_timer_mode, mode, listOf(TimerMode.TIMER to stringResource(R.string.timer), TimerMode.POMODORO to stringResource(R.string.pomodoro)), { mode = it })
        // The pomodoro's own numbers are always editable: they belong to the pomodoro, not to
        // whichever mode happens to be the default one.
        Text(stringResource(R.string.pomodoro), Modifier.padding(horizontal = Space.md, vertical = Space.xxs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(Triple(R.string.work_minutes, work, { text: String -> work = text }), Triple(R.string.break_minutes, rest, { text: String -> rest = text }), Triple(R.string.cycles, cycles, { text: String -> cycles = text })).forEach { (label, text, change) ->
            Row(Modifier.fillMaxWidth().height(Metrics.controlHeight).padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                PlainInput(stringResource(label), text, change, Modifier.weight(.65f), keyboardType = KeyboardType.Number)
            }
        }
        }
        if (error) Text(stringResource(R.string.error_timer_parameters), color = MaterialTheme.colorScheme.error)
        EditorActions(false, {
            val w = work.toIntOrNull(); val r = rest.toIntOrNull(); val c = cycles.toIntOrNull()
            if (w == null || r == null || c == null || w !in 1..180 || r !in 1..60 || c !in 1..12) error = true
            else save(mode, w, r, c, dismiss)
        })
    }
}
