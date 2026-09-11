package app.chronota.feature.timer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.chronota.R
import app.chronota.data.entity.*
import app.chronota.domain.*
import app.chronota.feature.today.timerElapsedText
import app.chronota.ui.components.*
import app.chronota.ui.theme.Space
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun TimerSheet(timer: TimerSession?, categories: List<Category>, plans: List<Plan>, initialPlan: Long?, initialMode: TimerMode,
    busy: Boolean, dismiss: () -> Unit, start: (String, Long?, Long?, TimerMode, Int, Int, Int, Map<Long, String>) -> Unit,
    cancel: () -> Unit, finish: () -> Unit, definitions: List<PropertyDefinition> = emptyList(), planValues: List<PlanPropertyValue> = emptyList(), timerPreferences: app.chronota.data.repository.AppPreferences = app.chronota.data.repository.AppPreferences()) {
    val plan = plans.firstOrNull { it.id == initialPlan }
    var title by rememberSaveable { mutableStateOf(plan?.title.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(plan?.categoryId) }
    var fields by rememberSaveable { mutableStateOf<Map<Long, String>>(HashMap(planValues.filter { it.planId == initialPlan }.associate { it.definitionId to it.value })) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var error by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(timer?.token) { if (timer != null) while (true) { now = Instant.now(); delay(1000) } }
    EditorSheet(if (timer == null) R.string.start_timer else if (timer.mode == TimerMode.POMODORO) R.string.pomodoro else R.string.timer, dismiss) {
        if (timer == null) {
            CategoryChoice(categories, category, { category = it })
            OptionalTitle(title, { title = it }, "timer_title")
            ChoiceField(R.string.timer_mode, mode, listOf(TimerMode.TIMER to stringResource(R.string.timer), TimerMode.POMODORO to stringResource(R.string.pomodoro)), { mode = it })
            PropertyFields(definitions.filter { it.categoryId == category }, propertyInputs(definitions.filter { it.categoryId == category }, fields), { id, text -> fields = HashMap(fields).apply { put(id, text) } })
            if (mode == TimerMode.POMODORO) Text(stringResource(R.string.pomodoro_summary, timerPreferences.workMinutes, timerPreferences.breakMinutes, timerPreferences.timerCycles), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) Text(stringResource(R.string.error_time), color = MaterialTheme.colorScheme.error)
            ActionButton(stringResource(R.string.start_timer), {
                try { start(title, category, null, mode, timerPreferences.workMinutes, timerPreferences.breakMinutes, timerPreferences.timerCycles, propertyInputs(definitions.filter { it.categoryId == category }, fields)) }
                catch (_: NumberFormatException) { error = true }
            }, Modifier.testTag("timer_start"), icon = AppIcons.Play, enabled = !busy)
        } else {
            Text(itemName(timer.title, categories.firstOrNull { it.id == timer.categoryId }), style = MaterialTheme.typography.titleMedium)
            Text(timerElapsedText(timer.elapsed(now)), style = MaterialTheme.typography.headlineSmall)
            if (timer.mode == TimerMode.POMODORO) {
                Text(stringResource(if (timer.phase == TimerPhase.WORK) R.string.timer_work else R.string.timer_break))
                Text(timerElapsedText(java.time.Duration.between(now, timer.phaseEnd()).toMillis().coerceAtLeast(0)), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.cycle_value, timer.cycle, timer.cycles))
            }
            // Ending the timer opens the record it produces, attributes and all.
            ActionButton(stringResource(R.string.finish_timer), finish, Modifier.testTag("timer_finish"), icon = AppIcons.Check, enabled = !busy)
            // Cancelling throws the run away, so it is red and asks first.
            ActionButton(stringResource(R.string.cancel_timer), { confirmingCancel = true }, Modifier.testTag("timer_cancel"), icon = AppIcons.Close, enabled = !busy, destructive = true)
        }
    }
    if (confirmingCancel) ConfirmAction(R.string.cancel_timer, R.string.cancel_timer_hint, { confirmingCancel = false },
        { confirmingCancel = false; cancel() })
}
