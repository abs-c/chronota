package app.chronota.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chronota.feature.workspaceModel
import app.chronota.feature.review.RecordEditor
import app.chronota.data.entity.Record
import app.chronota.domain.recordDraft
import app.chronota.domain.*
import app.chronota.ui.components.*
import androidx.compose.material3.Button
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import app.chronota.data.entity.Plan
import app.chronota.data.entity.Goal
import app.chronota.data.entity.TimerMode
import app.chronota.feature.timer.TimerSheet
import app.chronota.feature.today.FloatingOrb
import app.chronota.feature.today.ScheduleSheet
import app.chronota.feature.todo.PlanEditor
import app.chronota.data.repository.AppPreferences
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import app.chronota.feature.today.label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.chronota.R
import app.chronota.domain.ThemeMode
import app.chronota.feature.category.CategoryScreen
import app.chronota.feature.review.ReviewScreen
import app.chronota.feature.settings.SettingsScreen
import app.chronota.feature.today.TodayScreen
import app.chronota.feature.todo.TodoScreen
import app.chronota.ui.components.AppDivider
import app.chronota.ui.components.AppIcons
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space


@Composable
fun ChronotaApp(themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit, snackbar: SnackbarHostState) {
    val model = workspaceModel()
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var recordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var planEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var planId by rememberSaveable { mutableStateOf<Long?>(null) }
    var goalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var planSeed by remember { mutableStateOf<Plan?>(null) }
    var goalSeed by remember { mutableStateOf<Goal?>(null) }
    var recordSeed by remember { mutableStateOf<Record?>(null) }
    var timerDraft by remember { mutableStateOf<app.chronota.data.repository.TimerDraft?>(null) }
    var todoGoals by remember { mutableStateOf(false) }
    var planOccurrence by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var planStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var heldPlan by rememberSaveable { mutableStateOf<Long?>(null) }
    var remainingPlan by rememberSaveable { mutableStateOf<Long?>(null) }



    var showTimer by rememberSaveable { mutableStateOf(false) }
    var timerPlan by rememberSaveable { mutableStateOf<Long?>(null) }
    var timerMode by rememberSaveable { mutableStateOf(TimerMode.TIMER) }
    var showInbox by rememberSaveable { mutableStateOf(false) }
    var scheduling by rememberSaveable { mutableStateOf<Long?>(null) }
    val application = LocalContext.current.applicationContext as app.chronota.ChronotaApplication
    val prefs by application.preferencesRepository.preferences.collectAsStateWithLifecycle(AppPreferences())
    var notice by remember { mutableStateOf<Int?>(null) }
    // A backup is written where the user points the system picker and read back the same way: no
    // storage permission, and the file is theirs to keep anywhere.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    fun backupError(error: Throwable): Int = app.chronota.feature.settings.backupErrorResource(error)
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val outcome = runCatching {
                val text = application.backups.exportText()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: throw java.io.IOException("no stream")
            }
            notice = outcome.fold({ R.string.backup_saved }, { backupError(it) })
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notice = R.string.notification_denied else model.perform { application.notifications.reschedule() }
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(model) { model.messages.collect { notice = it } }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Destination.TODAY.route

    val backdrop = rememberGraphicsLayer()
    val backdropColor = MaterialTheme.colorScheme.background
    var wheelOpen by remember { mutableStateOf(false) }
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    // Every page draws the running timer as the record it is about to become, so tapping that one has
    // to open the timer instead of an editor for a record that does not exist yet.
    fun openRecordEntry(id: Long, time: Instant? = null) {
        if (id == LIVE_RECORD_ID) { showTimer = true; return }
        recordEnd = null
        recordStart = time?.toEpochMilli()
        draftPlanId = null
        recordId = id
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.padding(insets).fillMaxSize()) {
            if (state.failed) Text(stringResource(R.string.error_load), Modifier.padding(Space.md), color = MaterialTheme.colorScheme.error)
            NavHost(nav, startDestination = Destination.TODAY.route, enterTransition = { fadeIn(tween(90)) }, exitTransition = { fadeOut(tween(70)) }, modifier = Modifier.fillMaxSize().onGloballyPositioned { backdropOrigin = it.positionInRoot() }.drawWithContent { backdrop.record { drawRect(backdropColor); this@drawWithContent.drawContent() }; drawLayer(backdrop) }) {
                composable("today") { TodayScreen(onSettings = { nav.navigate("settings") }, state = state, snapMinutes = 15,
                    onPlan = { id, time -> planSeed = null; planOccurrence = null; planEnd = null; planStart = time?.toEpochMilli(); planId = id },
                    onPlanDate = { id, occurrence -> planSeed = null; planOccurrence = occurrence; planStart = null; planEnd = null; planId = id },
                    onRecord = { id, time -> openRecordEntry(id, time) }, policy = prefs.historyPolicy,
                    onHold = { if (it.temporalState(Instant.now()) == PlanTemporalState.PAST) { draftPlanId = it.id; recordStart = null; recordEnd = null; recordId = 0 } else heldPlan = it.id },
                    onMove = { plan, start, end -> planStart = start.toEpochMilli(); planEnd = end.toEpochMilli(); planId = plan.id },
                    weekView = prefs.weekView,
                    onCreatePlan = { planSeed = null; planOccurrence = null; planStart = it.start.toEpochMilli(); planEnd = it.end.toEpochMilli(); planId = 0 },
                    onCreateRecord = { recordSeed = null; recordStart = it.start.toEpochMilli(); recordEnd = it.end.toEpochMilli(); draftPlanId = null; recordId = 0 },
                    orb = {}) }
                composable("todo") {
                    TodoScreen(
                        onSettings = { nav.navigate("settings") },
                        onCategories = { nav.navigate("categories") },
                        onRecord = { recordStart = null; recordEnd = null; draftPlanId = it.id; recordId = 0 },
                        onTimer = { timerPlan = it.id; timerMode = prefs.timerMode; showTimer = true },
                        onReminderEnabled = { requestNotifications() },
                        onCreatePlan = { planSeed = null; planOccurrence = null; planStart = it.start.toEpochMilli(); planEnd = it.end.toEpochMilli(); planId = 0 },
                        onCreateRecord = { recordSeed = null; recordStart = it.start.toEpochMilli(); recordEnd = it.end.toEpochMilli(); draftPlanId = null; recordId = 0 },
                        onOpenRecord = { openRecordEntry(it) },
                        onGoal = { goalSeed = null; goalId = it }, onAddGoal = { goalSeed = null; goalId = 0 }, onDeleteGoal = { model.deleteGoal(it) {} }, onGoalsTab = { todoGoals = it },
                    )
                }
                composable("records") { ReviewScreen(onSettings = { nav.navigate("settings") }, state = state, onRecord = { openRecordEntry(it) },
                    onPlan = { planStart = null; planEnd = null; planId = it },
                    onCreatePlan = { planSeed = null; planOccurrence = null; planStart = it.start.toEpochMilli(); planEnd = it.end.toEpochMilli(); planId = 0 },
                    onCreateRecord = { recordSeed = null; recordStart = it.start.toEpochMilli(); recordEnd = it.end.toEpochMilli(); draftPlanId = null; recordId = 0 },
                    onDeleteGoal = { model.deleteGoal(it) {} },
                    onAddRecord = { date ->
                        val now = Instant.now()
                        val requested = date?.atTime(12, 0)?.atZone(ZoneId.systemDefault())?.toInstant()
                        recordStart = requested?.takeIf { it < now }?.toEpochMilli()
                        recordEnd = null; draftPlanId = null; recordId = 0
                    }) }
                composable("categories") { CategoryScreen(onBack = { nav.popBackStack() }) }
                composable("settings") {
                    // Saving happens either way: the Save button keeps the details and closes the panel,
                    // while "Back up now" keeps them and uploads straight away, so nobody has to save first.
                    val keepWebDav: suspend (String, String, String, String, Boolean, Int) -> Unit = { url, user, password, folder, auto, minutes ->
                        application.preferencesRepository.setWebDav(url, user, password, folder)
                        application.preferencesRepository.setAutoBackup(auto, minutes)
                        if (auto) app.chronota.data.backup.BackupScheduler.schedule(context, minutes)
                        else app.chronota.data.backup.BackupScheduler.cancel(context)
                    }
                    SettingsScreen(themeMode, onTheme, onBack = { nav.popBackStack() }, onCategories = { nav.navigate("categories") }, historyPolicy = prefs.historyPolicy,
                        onHistory = { model.perform { application.preferencesRepository.setHistory(it) } }, weekView = prefs.weekView, onWeekView = { model.perform { application.preferencesRepository.setWeekView(it) } },

                        timerPreferences = prefs, onDayStart = { model.perform { application.preferencesRepository.setDayStart(it) } }, onPreferCategory = { model.perform { application.preferencesRepository.setPreferCategory(it) } }, onLanguage = { model.perform { application.preferencesRepository.setLanguage(it) } }, onWeekStart = { model.perform { application.preferencesRepository.setWeekStart(it) } }, onTimerSettings = { mode, work, rest, cycles, done -> model.perform(done) { application.preferencesRepository.setTimer(mode, work, rest, cycles) } },
                        onRecordsCalendarPlans = { model.perform { application.preferencesRepository.setRecordsCalendarPlans(it) } },
                        onMonthDetails = { model.perform { application.preferencesRepository.setMonthDetails(it) } },
                        onBackupExport = { exportFile.launch(application.backups.fileName()) },
                        onBackupImport = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        onBackupSave = { url, user, password, folder, auto, minutes, done -> scope.launch { keepWebDav(url, user, password, folder, auto, minutes); done() } },
                        onBackupTest = { url, user, password, folder, result -> scope.launch { result(runCatching { application.backups.verify(url, user, password, folder) }.getOrNull()?.let { backupError(app.chronota.data.backup.WebDavException(it)) }) } },
                        onBackupNow = { url, user, password, folder, auto, minutes, result -> scope.launch {
                            keepWebDav(url, user, password, folder, auto, minutes)
                            result(application.backups.uploadNow()?.let { backupError(app.chronota.data.backup.WebDavException(it)) })
                        } },
                        onBackupRestore = { url, user, password, folder, result -> scope.launch { result(runCatching { application.backups.restoreNow(app.chronota.data.backup.WebDavTarget(url, user, password), folder) }.fold({ null }, { backupError(it) })) } },
                        orbAction = prefs.orbAction, onOrbAction = { model.perform { application.preferencesRepository.setOrb(it) } },
                        notificationSettings = { application.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        exactSettings = if (Build.VERSION.SDK_INT >= 31) ({ application.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, ("package:" + application.packageName).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) else null)
                }
            }
            if (wheelOpen) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .26f)).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
            if (Destination.entries.any { it.route == route }) Box(Modifier.align(Alignment.BottomCenter)) {
                GlassDock(route, { destination -> nav.navigate(destination) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                } }, state.timer, prefs.orbAction, { action -> when (action) {
                    OrbAction.PLAN -> { planSeed = null; planOccurrence = null; planEnd = null; planStart = null; planId = 0 }
                    OrbAction.RECORD -> { recordSeed = null; recordEnd = null; recordStart = null; recordEnd = null; draftPlanId = null; recordId = 0 }
                    OrbAction.GOAL -> { goalSeed = null; goalId = 0 }
                    OrbAction.INBOX -> showInbox = true
                    OrbAction.TIMER -> { timerPlan = null; timerMode = prefs.timerMode; showTimer = true }
                } }, { showTimer = true }, backdrop, backdropOrigin, { wheelOpen = it }, if (route == "todo" && todoGoals) OrbAction.GOAL else null)
            }
        }
    }
    if (!state.loading && !state.failed) recordId?.let { id ->
        val now = remember(id, draftPlanId, recordStart) { Instant.now() }
        val requestedStart = recordStart?.let(Instant::ofEpochMilli)?.takeIf { it < now } ?: now.minusSeconds(1800)
        // -1 marks the editor the running timer hands its record to.
        val finishedTimer = timerDraft.takeIf { id == -1L }
        val value = state.records.firstOrNull { it.id == id }
            ?: recordSeed?.takeIf { id == 0L }
            ?: finishedTimer?.record
            ?: state.plans.firstOrNull { it.id == draftPlanId }?.recordDraft(now)
            ?: Record(title = "", startTime = requestedStart, endTime = minOf(recordEnd?.let(Instant::ofEpochMilli) ?: requestedStart.plusSeconds(1800), now))
        val editorValues = when {
            finishedTimer != null -> finishedTimer.properties.map { (definition, text) -> app.chronota.data.entity.PropertyValue(0, definition, text) }
            draftPlanId == null -> state.values
            else -> state.planValues.filter { it.planId == draftPlanId }.map { app.chronota.data.entity.PropertyValue(0, it.definitionId, it.value) }
        }
        RecordEditor(value, state.categories, state.plans, busy, { recordId = null; recordSeed = null; timerDraft = null },
            { record, properties ->
                if (finishedTimer == null) model.saveRecord(record, properties) { recordId = null; recordSeed = null }
                else model.perform({ recordId = null; recordSeed = null; timerDraft = null }) { application.timers.finishAs(record, properties) }
            }, { model.deleteRecord(id) { recordId = null; recordSeed = null } }, state.definitions, editorValues,
            onDuplicate = { record, properties -> model.saveRecord(record, properties) { recordSeed = record.copy(id = 0, createdAt = Instant.now()); recordId = 0 } })
    }
    if (!state.loading && !state.failed) planId?.let { id ->
        val zone = ZoneId.systemDefault()
        val start = planStart?.let(Instant::ofEpochMilli)?.atZone(zone)
        val end = planEnd?.let(Instant::ofEpochMilli)?.atZone(zone) ?: start?.plusMinutes(30)
        val original = planSeed?.takeIf { id == 0L } ?: state.plans.firstOrNull { it.id == id } ?: Plan(title = "")
        val value = if (start == null || end == null) original else original.copy(allDay = false, scheduledDate = start.toLocalDate(),
            startTime = start.toLocalTime(), endTime = end.toLocalTime(), endDayOffset = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).toInt())
        PlanEditor(value, state.categories, state.reminders.filter { it.planId == id }, prefs.historyPolicy, busy,
            { planId = null; planSeed = null }, { plan, reminders, properties -> model.savePlan(plan, prefs.historyPolicy, reminders, properties) { planId = null; planSeed = null; if (reminders.isNotEmpty()) requestNotifications() } },
            { model.deletePlan(id, prefs.historyPolicy) { planId = null; planSeed = null } },
            onRecord = { planId = null; planSeed = null; recordStart = null; recordEnd = null; draftPlanId = id; recordId = 0 },
            onTimer = { planId = null; planSeed = null; timerPlan = id; timerMode = prefs.timerMode; showTimer = true }, definitions = state.definitions, values = state.planValues,
            onDuplicate = { plan, reminders, properties -> model.savePlan(plan, prefs.historyPolicy, reminders, properties) {
                planSeed = plan.copy(id = 0, createdAt = Instant.now(), updatedAt = Instant.now()); planStart = null; planEnd = null; planId = 0
            } }, occurrenceDate = planOccurrence,
            onSaveOccurrence = { series, single, reminders, properties -> model.perform({ planId = null; planOccurrence = null }) {
                model.repository.savePlan(series, prefs.historyPolicy, reminders, properties)
                model.repository.savePlan(single, prefs.historyPolicy, emptyList(), properties)
            } })
    }
    notice?.let { message -> NoticeDialog(message) { notice = null } }
    // Restoring replaces everything, so it asks first — and only then reads the file.
    pendingRestore?.let { uri ->
        ConfirmAction(R.string.backup_confirm_title, R.string.backup_confirm_body, { pendingRestore = null }) {
            pendingRestore = null
            scope.launch {
                val outcome = runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw java.io.IOException("no stream")
                    application.backups.restoreText(text)
                }
                notice = outcome.fold({ R.string.backup_restored }, { backupError(it) })
            }
        }
    }
    if (!state.loading && !state.failed) goalId?.let { id ->
        val value = goalSeed?.takeIf { id == 0L } ?: state.goals.firstOrNull { it.id == id } ?: Goal()
        app.chronota.feature.todo.GoalEditor(value, state, busy, { goalId = null; goalSeed = null },
            { goal -> model.saveGoal(goal) { goalId = null; goalSeed = null } }, if (id != 0L) ({ model.deleteGoal(id) { goalId = null; goalSeed = null } }) else null,
            onDuplicate = { goal -> model.saveGoal(goal) { goalSeed = goal.copy(id = 0, createdAt = Instant.now()); goalId = 0 } })
    }
    heldPlan?.let { id ->
        EditorSheet(R.string.state_active, { heldPlan = null }) {
            OptionRow(stringResource(R.string.start_timer), { heldPlan = null; timerPlan = id; timerMode = prefs.timerMode; showTimer = true })
            OptionRow(stringResource(R.string.remaining_time), { heldPlan = null; remainingPlan = id })
            OptionRow(stringResource(R.string.details), { heldPlan = null; planStart = null; planEnd = null; planId = id })
        }
    }
    remainingPlan?.let { id -> state.plans.firstOrNull { it.id == id }?.let { plan ->
        var end by rememberSaveable(id) { mutableStateOf(plan.endTime ?: java.time.LocalTime.now()) }
        var date by rememberSaveable(id) { mutableStateOf(plan.scheduledDate!!.plusDays(plan.endDayOffset.toLong())) }
        EditorSheet(R.string.remaining_time, { remainingPlan = null }) {
            DateTimeField(R.string.end_at, date, end) { d, t -> date = d; end = t }
            EditorActions(busy, { model.perform({ remainingPlan = null }) { model.repository.adjustRemaining(id, resolveLocal(date, end, ZoneId.of(plan.zoneId)), prefs.historyPolicy) } })
        }
    } }
    if (showTimer) TimerSheet(state.timer, state.categories, state.plans, timerPlan, timerMode, busy,
        { showTimer = false }, { title, category, plan, mode, work, rest, cycles, properties -> model.perform({ showTimer = false; requestNotifications() }) { application.timers.start(title, category, null, mode, work, rest, cycles, properties) } },
        cancel = { model.perform { application.timers.cancel() } },
        finish = {
            // The finished timer hands its record over to the standard record editor.
            model.perform {
                val draft = application.timers.draft()
                if (draft == null) application.timers.finish() else {
                    showTimer = false; timerDraft = draft; recordSeed = null; draftPlanId = null; recordStart = null; recordEnd = null; recordId = -1L
                }
            }
        }, definitions = state.definitions, planValues = state.planValues, timerPreferences = prefs)
    if (showInbox) EditorSheet(R.string.open_inbox, { showInbox = false }) {
        val now = Instant.now()
        val pending = state.plans.filter { plan -> plan.canEdit(now, prefs.historyPolicy) &&
            (plan.scheduledDate == null || (plan.scheduledDate == logicalDate(now, dayStartMinutes = prefs.dayStartMinutes) && plan.startTime == null) ||
                plan.deadline?.let { it >= now && it <= now.plusSeconds(7 * 86400L) } == true) }
        if (pending.isEmpty()) Text(stringResource(R.string.no_inbox))
        pending.forEach { plan -> OptionRow(itemName(plan.title, state.categories.firstOrNull { it.id == plan.categoryId }), { showInbox = false; scheduling = plan.id }, description = planTimeText(plan)) }
    }
    scheduling?.let { id -> state.plans.firstOrNull { it.id == id }?.let { plan ->
        ScheduleSheet(plan, state.categories.firstOrNull { it.id == plan.categoryId }, 15, busy, { scheduling = null }, { start, end ->
            model.perform({ scheduling = null }) { model.repository.schedulePlan(id, start, end, prefs.historyPolicy) }
        })
    } }
}
