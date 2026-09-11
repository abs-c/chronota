package app.chronota.feature.today

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.chronota.R
import app.chronota.data.entity.Plan
import app.chronota.domain.resolveLocal
import app.chronota.ui.components.*
import java.time.*

@Composable
fun ScheduleSheet(plan: Plan, category: app.chronota.data.entity.Category?, snap: Int, busy: Boolean, dismiss: () -> Unit, schedule: (Instant, Instant) -> Unit) {
    val zone = ZoneId.of(plan.zoneId)
    val initial = remember { ZonedDateTime.now(zone).withSecond(0).withNano(0).let { it.plusMinutes((snap - it.minute % snap).toLong()) } }
    val initialEnd = remember { initial.plusMinutes((plan.estimatedDuration ?: 30).coerceIn(1, 1440)) }
    var date by rememberSaveable(plan.id) { mutableStateOf(initial.toLocalDate()) }
    var endDate by rememberSaveable(plan.id) { mutableStateOf(initialEnd.toLocalDate()) }
    var start by rememberSaveable(plan.id) { mutableStateOf(initial.toLocalTime()) }
    var end by rememberSaveable(plan.id) { mutableStateOf(initialEnd.toLocalTime()) }
    var error by remember { mutableStateOf(false) }
    EditorSheet(R.string.schedule, dismiss) {
        Text(itemName(plan.title, category))
        DateTimeField(R.string.start_at, date, start, change = { d, t -> date = d; start = t })
        DateTimeField(R.string.end_at, endDate, end, change = { d, t -> endDate = d; end = t })
        if (error) Text(stringResource(R.string.error_time), color = MaterialTheme.colorScheme.error)
        EditorActions(busy, { try { schedule(resolveLocal(date, start, zone), resolveLocal(endDate, end, zone)) } catch (_: IllegalArgumentException) { error = true } })
    }
}
