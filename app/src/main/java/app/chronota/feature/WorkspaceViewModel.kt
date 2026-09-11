package app.chronota.feature

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chronota.ChronotaApplication
import app.chronota.R
import app.chronota.data.entity.*
import app.chronota.data.repository.RuleViolation
import app.chronota.domain.HistoricalPlanPolicy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorkspaceState(val categories: List<Category> = emptyList(), val plans: List<Plan> = emptyList(),
    val records: List<Record> = emptyList(), val reminders: List<Reminder> = emptyList(),
    val timer: TimerSession? = null, val loading: Boolean = true, val failed: Boolean = false,
    val definitions: List<PropertyDefinition> = emptyList(), val values: List<PropertyValue> = emptyList(), val planValues: List<PlanPropertyValue> = emptyList(),
    val goals: List<Goal> = emptyList())

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as ChronotaApplication).repository
    private val dao = repository.dao
    val state = combine(dao.categories(), dao.plans(), dao.records(), dao.reminders(), dao.timer()) { c, p, r, reminders, timer ->
        WorkspaceState(c, p, r, reminders, timer, loading = false)
    }.combine(combine(dao.definitions(), dao.values()) { d, v -> d to v }) { state, properties ->
        state.copy(definitions = properties.first, values = properties.second)
    }.combine(dao.planValues()) { state, values -> state.copy(planValues = values)
    }.combine(dao.goals()) { state, goals -> state.copy(goals = goals)
    }.catch { error -> android.util.Log.e("chronota", "Workspace observation failed", error); emit(WorkspaceState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkspaceState())
    private val notices = Channel<Int>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    val busy = MutableStateFlow(false)

    init {
        // Goals roll over on a timer as well as on launch, so an instance that runs out while the
        // screen is open is retired without waiting for the next start.
        viewModelScope.launch {
            while (true) {
                runCatching { repository.rolloverGoals(Instant.now(), ZoneId.systemDefault()) }
                    .onFailure { android.util.Log.e("chronota", "Goal rollover failed", it) }
                delay(60_000)
            }
        }
    }
    fun perform(done: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action(); done() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { notices.send(error.messageResource()) }
            finally { busy.value = false }
        }
    }
    fun saveCategory(value: Category, done: () -> Unit) = perform(done) { repository.saveCategory(value) }
    fun deleteCategory(id: Long, done: () -> Unit) = perform(done) { repository.deleteCategory(id) }
    fun savePlan(value: Plan, policy: HistoricalPlanPolicy, reminders: List<Reminder>, properties: Map<Long, String>? = null, done: () -> Unit) = perform(done) { repository.savePlan(value, policy, reminders, properties) }
    fun deletePlan(id: Long, policy: HistoricalPlanPolicy, done: () -> Unit) = perform(done) { repository.deletePlan(id, policy) }
    fun saveRecord(value: Record, properties: Map<Long, String>? = null, done: () -> Unit) = perform(done) { repository.saveRecord(value, properties) }
    fun deleteRecord(id: Long, done: () -> Unit) = perform(done) { repository.deleteRecord(id) }
    fun saveGoal(value: Goal, done: () -> Unit) = perform(done) {
        repository.saveGoal(value)
        // A goal saved with a frame that already ran out rolls forward straight away.
        repository.rolloverGoals(Instant.now(), ZoneId.systemDefault())
    }
    fun deleteGoal(id: Long, done: () -> Unit) = perform(done) { repository.deleteGoal(id) }
}

fun Exception.messageResource(): Int = when ((this as? RuleViolation)?.reason) {
    "required" -> R.string.error_required
    "category_depth" -> R.string.error_category_depth
    "category_used" -> R.string.error_category_used
    "secondary_required" -> R.string.error_secondary
    "history_locked" -> R.string.history_locked
    "invalid_time" -> R.string.error_time
    "record_future" -> R.string.error_record_time
    "invalid_reminder" -> R.string.error_reminder
    "timer_running" -> R.string.error_timer_running
    "missing" -> R.string.error_missing
    "property_value" -> R.string.error_property_value
    "property_duplicate" -> R.string.error_property_duplicate
    "property_required" -> R.string.error_property_required
    "property_used" -> R.string.error_property_used
    "property_category" -> R.string.error_property_category
    else -> R.string.error_save
}

@Composable fun workspaceModel(): WorkspaceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = androidx.compose.runtime.remember(application) { androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application) }
    return viewModel(factory = factory)
}
