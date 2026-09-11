package app.chronota.feature.todo

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.chronota.R
import app.chronota.data.entity.*
import app.chronota.domain.*
import app.chronota.ui.components.*
import java.time.*
import java.time.temporal.ChronoUnit

private val REMINDER_PRESETS = listOf(0, 5, 15, 30, 60, 120)

@Composable
fun PlanEditor(value: Plan, categories: List<Category>, reminders: List<Reminder>, policy: HistoricalPlanPolicy,
    busy: Boolean, dismiss: () -> Unit, save: (Plan, List<Reminder>, Map<Long, String>) -> Unit, delete: () -> Unit,
    onRecord: (() -> Unit)? = null, onTimer: (() -> Unit)? = null,
    definitions: List<PropertyDefinition> = emptyList(), values: List<PlanPropertyValue> = emptyList(),
    onDuplicate: ((Plan, List<Reminder>, Map<Long, String>) -> Unit)? = null,
    occurrenceDate: LocalDate? = null, onSaveOccurrence: ((Plan, Plan, List<Reminder>, Map<Long, String>) -> Unit)? = null) {
    var title by rememberSaveable(value.id) { mutableStateOf(value.title) }
    var category by rememberSaveable(value.id) { mutableStateOf(value.categoryId) }
    val initial = remember { LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0) }
    var allDay by rememberSaveable(value.id) { mutableStateOf(value.allDay) }
    var date by rememberSaveable(value.id) { mutableStateOf(value.scheduledDate ?: initial.toLocalDate()) }
    var endDate by rememberSaveable(value.id) { mutableStateOf(value.scheduledDate?.plusDays(value.endDayOffset.toLong()) ?: initial.plusMinutes(30).toLocalDate()) }
    var start by rememberSaveable(value.id) { mutableStateOf(value.startTime ?: initial.toLocalTime()) }
    var end by rememberSaveable(value.id) { mutableStateOf(value.endTime ?: initial.plusMinutes(30).toLocalTime()) }
    var note by rememberSaveable(value.id) { mutableStateOf(value.note) }
    var offsets by rememberSaveable(value.id) { mutableStateOf(reminders.filter { it.anchor == ReminderAnchor.START }.map { it.minutesBefore }) }
    var recurrence by rememberSaveable(value.id) { mutableStateOf(value.recurrence) }
    var interval by rememberSaveable(value.id) { mutableIntStateOf(value.recurrenceInterval.coerceAtLeast(1)) }
    var until by rememberSaveable(value.id) { mutableStateOf(value.recurrenceUntil) }
    var skipText by rememberSaveable(value.id) { mutableStateOf(value.skipDates) }
    var reminderDialog by remember { mutableStateOf(false) }
    var customReminder by remember { mutableStateOf(false) }
    var recurrenceDialog by remember { mutableStateOf(false) }
    var intervalDialog by remember { mutableStateOf(false) }
    var untilDialog by remember { mutableStateOf(false) }
    var skipDialog by remember { mutableStateOf(false) }
    var fields by rememberSaveable(value.id) { mutableStateOf<Map<Long, String>>(HashMap(values.filter { it.planId == value.id }.associate { it.definitionId to it.value })) }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var scope by rememberSaveable(value.id) { mutableIntStateOf(0) }
    val editable = value.id == 0L || value.canEdit(rememberNow(), policy)
    val visible = definitions.filter { it.categoryId == category }
    val reminderSummary = if (offsets.isEmpty()) stringResource(R.string.no_reminder)
        else offsets.sorted().map { reminderText(it) }.joinToString("、")
    val recurrenceSummary = if (recurrence == Recurrence.NONE) stringResource(R.string.recurrence_none)
        else recurrenceText(recurrence, interval) + until?.let { " · " + dateText(it) }.orEmpty()
    val buildPlan: () -> Plan? = {
        try {
            val days = ChronoUnit.DAYS.between(date, endDate).toInt()
            require(days >= 0)
            val repeating = recurrence != Recurrence.NONE
            val updated = value.copy(title = title, categoryId = category, scheduledDate = date, allDay = allDay,
                startTime = start.takeUnless { allDay }, endTime = end.takeUnless { allDay }, endDayOffset = days,
                estimatedDuration = null, deadline = null, note = note,
                recurrence = recurrence, recurrenceInterval = if (repeating) interval else 1,
                recurrenceUntil = until.takeIf { repeating }, skipDates = skipText.takeIf { repeating }.orEmpty())
            updated.timeSpan()
            require(!repeating || (updated.recurrenceUntil == null || updated.recurrenceUntil >= date))
            error = null
            updated
        } catch (_: IllegalArgumentException) {
            error = if (recurrence != Recurrence.NONE && until != null && until!! < date) R.string.error_recurrence else R.string.error_time
            null
        }
    }
    val buildReminders = { offsets.sorted().map { Reminder(planId = value.id, anchor = ReminderAnchor.START, minutesBefore = it) } }
    EditorSheet(if (value.id == 0L) R.string.new_plan else R.string.edit_plan, dismiss, footer = {
        EditorActions(busy, onSave = if (!editable) null else ({ buildPlan()?.let { built ->
            val properties = propertyInputs(visible, fields, value.id == 0L || category != value.categoryId)
            if (scope == 1 && occurrenceDate != null && onSaveOccurrence != null) {
                val series = value.copy(skipDates = skippedDatesText(parseSkipDates(value.skipDates) + occurrenceDate))
                val single = built.copy(id = 0, recurrence = Recurrence.NONE, recurrenceInterval = 1, recurrenceUntil = null, skipDates = "", scheduledDate = occurrenceDate)
                onSaveOccurrence(series, single, buildReminders(), properties)
            } else save(built, buildReminders(), properties)
        } }),
            onDelete = if (value.id != 0L && editable) ({ confirmDelete = true }) else null,
            onDuplicate = if (!editable || onDuplicate == null) null else ({ buildPlan()?.let { onDuplicate(it, buildReminders(), propertyInputs(visible, fields, true)) } }))
    }) {
        if (!editable) Text(stringResource(R.string.history_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (occurrenceDate != null && value.isRecurring()) ChoiceField(R.string.edit_scope, scope, listOf(0 to stringResource(R.string.edit_series), 1 to stringResource(R.string.edit_occurrence)), { scope = it }, enabled = editable)
        CategoryChoice(categories, category, { category = it }, editable)
        OptionalTitle(title, { title = it }, "plan_title", value.id == 0L, editable)
        PropertyFields(visible, propertyInputs(visible, fields, value.id == 0L || category != value.categoryId), { id, text -> fields = HashMap(fields).apply { put(id, text) } }, optional = true, enabled = editable)
        OptionGroup {
            SwitchRow(R.string.all_day, allDay, { allDay = it }, editable)
            AppDivider()
            DateTimeField(R.string.start_at, date, start, allDay, editable) { d, t ->
                val duration = Duration.between(LocalDateTime.of(date, start), LocalDateTime.of(endDate, end)).coerceAtLeast(Duration.ofMinutes(30))
                date = d; start = t
                if (until != null && until!! < d) until = d
                if (LocalDateTime.of(endDate, end) <= LocalDateTime.of(d, t)) {
                    val next = LocalDateTime.of(d, t).plus(duration); endDate = next.toLocalDate(); end = next.toLocalTime()
                }
            }
            AppDivider()
            DateTimeField(R.string.end_at, endDate, end, allDay, editable) { d, t -> endDate = d; end = t }
        }
        if (!(scope == 1 && occurrenceDate != null && value.isRecurring())) OptionRow(stringResource(R.string.recurrence), { recurrenceDialog = true },
            value = recurrenceSummary, enabled = editable)
        OptionRow(stringResource(R.string.reminders), { reminderDialog = true },
            description = reminderSummary, enabled = editable)

        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (onRecord != null && value.id != 0L) AddRow(R.string.add_record_from_plan, onRecord)
        if (onTimer != null && value.id != 0L) ActionButton(stringResource(R.string.start_timer), onTimer, secondary = true, icon = AppIcons.Timer)

    }
    if (reminderDialog) SelectionDialog(stringResource(R.string.reminders), { reminderDialog = false }) {
        SelectionRow(stringResource(R.string.no_reminder), offsets.isEmpty(), { offsets = emptyList() }, multi = true)
        (REMINDER_PRESETS + offsets).distinct().sorted().forEach { minutes ->
            SelectionRow(reminderText(minutes), minutes in offsets, {
                offsets = if (minutes in offsets) offsets - minutes else offsets + minutes
            }, multi = true)
        }
        SelectionRow(stringResource(R.string.custom_reminder), false, { reminderDialog = false; customReminder = true }, multi = true)
    }
    if (customReminder) NumberDialog(stringResource(R.string.custom_reminder_title), stringResource(R.string.reminder_minutes_label),
        offsets.lastOrNull() ?: 10, 0..525600, { customReminder = false }, unit = { minutesUnitText(it) }) {
        offsets = (offsets + it).distinct(); customReminder = false
    }
    if (recurrenceDialog) SelectionDialog(stringResource(R.string.recurrence), { recurrenceDialog = false }) {
        Recurrence.entries.forEach { option ->
            SelectionRow(recurrenceText(option, if (option == recurrence) interval else 1), option == recurrence, {
                recurrence = option
                if (option == Recurrence.NONE) { interval = 1; until = null }
            })
        }
        if (recurrence != Recurrence.NONE) {
            OptionRow(stringResource(R.string.recurrence_interval), { intervalDialog = true }, value = interval.toString() + " " + intervalUnitText(recurrence, interval))
            OptionRow(stringResource(R.string.recurrence_ends), { untilDialog = true },
                value = until?.let { dateText(it) } ?: stringResource(R.string.recurrence_no_end),
                description = null)
            val skips = parseSkipDates(skipText)
            OptionRow(stringResource(R.string.skip_dates), { skipDialog = true },
                value = if (skips.isEmpty()) stringResource(R.string.goal_none) else skips.size.toString() + " " + pluralStringResource(R.plurals.unit_days, skips.size, skips.size))
        }
    }
    if (intervalDialog) NumberDialog(stringResource(R.string.recurrence_interval), stringResource(R.string.recurrence_interval), interval, 1..99, { intervalDialog = false }, unit = { intervalUnitText(recurrence, it) }) {
        interval = it; intervalDialog = false
    }
    if (untilDialog) CalendarDialog(stringResource(R.string.recurrence_ends), until ?: date, { untilDialog = false }, clear = { until = null; untilDialog = false }) { until = it; untilDialog = false }
    if (skipDialog) MultiDateDialog(stringResource(R.string.skip_dates), parseSkipDates(skipText), { skipDialog = false }) { skipText = skippedDatesText(it); skipDialog = false }
    if (confirmDelete) ConfirmAction(R.string.delete, R.string.confirm_delete, { confirmDelete = false }, { confirmDelete = false; delete() })
}
