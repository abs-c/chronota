package app.chronota.feature.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chronota.data.entity.*
import app.chronota.domain.*
import app.chronota.feature.*
import app.chronota.ui.components.*
import app.chronota.ui.theme.Space
import java.time.*
import app.chronota.R
import app.chronota.ui.components.AppDivider
import app.chronota.ui.components.AppIcons
import app.chronota.ui.components.EmptyState
import app.chronota.ui.components.PageColumn
import app.chronota.ui.components.PageHeader

@Composable
fun TodoScreen(onSettings: () -> Unit, onCategories: () -> Unit, model: WorkspaceViewModel = workspaceModel(),
    onRecord: ((Plan) -> Unit)? = null, onTimer: ((Plan) -> Unit)? = null, onReminderEnabled: () -> Unit = {}, onOpenRecord: (Long) -> Unit = {}, onCreatePlan: (TimeSpan) -> Unit = {}, onCreateRecord: (TimeSpan) -> Unit = {},
    onGoal: (Long) -> Unit = {}, onAddGoal: () -> Unit = {}, onDeleteGoal: (Long) -> Unit = {}, onGoalsTab: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var draftDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var planSeed by remember { mutableStateOf<Plan?>(null) }
    var planOccurrence by remember { mutableStateOf<LocalDate?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val prefs by (androidx.compose.ui.platform.LocalContext.current.applicationContext as app.chronota.ChronotaApplication).preferencesRepository.preferences.collectAsStateWithLifecycle(app.chronota.data.repository.AppPreferences())
    LaunchedEffect(model) { model.messages.collect { notice = it } }
    val now = rememberNow()
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    app.chronota.feature.browse.BrowseScreen(false, state, { id -> planSeed = null; editing = id }, onOpenRecord,
        { draftDate = it; planSeed = null; editing = 0 }, onCreatePlan, onCreateRecord, onGoal = onGoal, onAddGoal = onAddGoal, onDeleteGoal = onDeleteGoal, onModeChange = { onGoalsTab(it == 2) }, onPlanOccurrence = { id, occurrence -> planSeed = null; planOccurrence = occurrence; editing = id })
    if (!state.loading && !state.failed) editing?.let { id ->
        val value = planSeed?.takeIf { id == 0L } ?: state.plans.firstOrNull { it.id == id } ?: Plan(title = "", scheduledDate = draftDate, allDay = draftDate != null)
        PlanEditor(value, state.categories, state.reminders.filter { it.planId == id }, prefs.historyPolicy, busy,
            { editing = null; planSeed = null }, { plan, reminders, properties -> model.savePlan(plan, prefs.historyPolicy, reminders, properties) { editing = null; planSeed = null; if (reminders.isNotEmpty()) onReminderEnabled() } },
            { model.deletePlan(id, prefs.historyPolicy) { editing = null; planSeed = null } },
            onRecord = onRecord?.let { action -> ({ editing = null; planSeed = null; action(value) }) }, onTimer = onTimer?.let { action -> ({ editing = null; planSeed = null; action(value) }) }, definitions = state.definitions, values = state.planValues,
            onDuplicate = { plan, reminders, properties -> model.savePlan(plan, prefs.historyPolicy, reminders, properties) {
                planSeed = plan.copy(id = 0, createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now()); editing = 0
            } },
            occurrenceDate = planOccurrence,
            onSaveOccurrence = { series, single, reminders, properties -> model.perform({ editing = null; planOccurrence = null }) {
                model.repository.savePlan(series, prefs.historyPolicy, reminders, properties)
                model.repository.savePlan(single, prefs.historyPolicy, emptyList(), properties)
            } })
    }
    notice?.let { message -> NoticeDialog(message) { notice = null } }
}
