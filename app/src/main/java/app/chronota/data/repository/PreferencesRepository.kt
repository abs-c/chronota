package app.chronota.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.chronota.domain.ThemeMode
import app.chronota.domain.HistoricalPlanPolicy
import app.chronota.domain.OrbAction
import java.time.Instant
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import app.chronota.data.backup.WebDavFailure
import app.chronota.data.backup.WebDavTarget
import app.chronota.data.entity.TimerMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "",
    val dayStartMinutes: Int = 0,
    val preferCategoryName: Boolean = false,
    val readFailed: Boolean = false,
    val historyPolicy: HistoricalPlanPolicy = HistoricalPlanPolicy.IMMUTABLE,
    val orbAction: OrbAction = OrbAction.RECORD,
    val snapMinutes: Int = 15,
    val weekView: Boolean = true,
    val weekStart: Int = 7,
    val timerMode: TimerMode = TimerMode.TIMER,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val timerCycles: Int = 4,
    /** Whether the review calendar also draws plans. The plan calendar never draws records. */
    val reviewCalendarPlans: Boolean = true,
    /** Whether the month pages spell out the details of their day cards. */
    val monthDetails: Boolean = true,
    /** Where WebDAV backups go and who they authenticate as. The password stays on this device. */
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavPassword: String = "",
    val webDavFolder: String = "chronota",
    val autoBackupEnabled: Boolean = false,
    /** Wall-clock time the automatic backup runs, as minutes past midnight. */
    val autoBackupMinutes: Int = 3 * 60,
    /** When the last attempt ran and how it went; null means it never has. */
    val lastBackupAt: Instant? = null,
    val lastBackupFailure: WebDavFailure? = null,
) {
    val webDavTarget: WebDavTarget get() = WebDavTarget(webDavUrl, webDavUser, webDavPassword)
    val webDavConfigured: Boolean get() = webDavTarget.configured
}

class PreferencesRepository(private val store: DataStore<Preferences>) {
    private val dayStartKey = intPreferencesKey("day_start_minutes")
    private val categoryNameKey = booleanPreferencesKey("prefer_category_name")
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val historyKey = stringPreferencesKey("historical_plan_policy")
    private val orbKey = stringPreferencesKey("orb_action")
    private val snapKey = intPreferencesKey("snap_minutes")
    private val timerModeKey = stringPreferencesKey("timer_mode")
    private val workKey = intPreferencesKey("timer_work")
    private val breakKey = intPreferencesKey("timer_break")
    private val cyclesKey = intPreferencesKey("timer_cycles")
    private val defaultsKey = booleanPreferencesKey("default_categories_v1")
    private val weekKey = booleanPreferencesKey("week_view")
    private val weekStartKey = intPreferencesKey("week_start")
    private val reviewCalendarPlansKey = booleanPreferencesKey("review_calendar_plans")
    private val monthDetailsKey = booleanPreferencesKey("month_details")
    private val webDavUrlKey = stringPreferencesKey("webdav_url")
    private val webDavUserKey = stringPreferencesKey("webdav_user")
    private val webDavPasswordKey = stringPreferencesKey("webdav_password")
    private val webDavFolderKey = stringPreferencesKey("webdav_folder")
    private val autoBackupKey = booleanPreferencesKey("auto_backup_enabled")
    private val autoBackupMinutesKey = intPreferencesKey("auto_backup_minutes")
    private val lastBackupAtKey = longPreferencesKey("last_backup_at")
    private val lastBackupFailureKey = stringPreferencesKey("last_backup_failure")

    val preferences = store.data.map { preferences ->
        AppPreferences(
            language = preferences[languageKey] ?: "",
            dayStartMinutes = preferences[dayStartKey] ?: 0,
            preferCategoryName = preferences[categoryNameKey] ?: false,
            themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] }
                ?: ThemeMode.SYSTEM,
            historyPolicy = HistoricalPlanPolicy.entries.firstOrNull { it.name == preferences[historyKey] } ?: HistoricalPlanPolicy.IMMUTABLE,
            orbAction = OrbAction.entries.firstOrNull { it.name == preferences[orbKey] } ?: OrbAction.RECORD,
            snapMinutes = preferences[snapKey]?.takeIf { it in listOf(15, 30) } ?: 15,
            weekView = preferences[weekKey] ?: true,
            weekStart = (preferences[weekStartKey] ?: 7).takeIf { it in 1..7 } ?: 7,
            timerMode = TimerMode.entries.firstOrNull { it.name == preferences[timerModeKey] } ?: if (preferences[orbKey] == "POMODORO") TimerMode.POMODORO else TimerMode.TIMER,
            workMinutes = preferences[workKey] ?: 25,
            breakMinutes = preferences[breakKey] ?: 5,
            timerCycles = preferences[cyclesKey] ?: 4,
            reviewCalendarPlans = preferences[reviewCalendarPlansKey] ?: true,
            monthDetails = preferences[monthDetailsKey] ?: true,
            webDavUrl = preferences[webDavUrlKey] ?: "",
            webDavUser = preferences[webDavUserKey] ?: "",
            webDavPassword = preferences[webDavPasswordKey] ?: "",
            webDavFolder = preferences[webDavFolderKey] ?: "chronota",
            autoBackupEnabled = preferences[autoBackupKey] ?: false,
            autoBackupMinutes = (preferences[autoBackupMinutesKey] ?: (3 * 60)).coerceIn(0, 1439),
            lastBackupAt = preferences[lastBackupAtKey]?.let(Instant::ofEpochMilli),
            lastBackupFailure = preferences[lastBackupFailureKey]?.let { name -> WebDavFailure.entries.firstOrNull { it.name == name } ?: WebDavFailure.SERVER },
        )
    }.catch { error ->
        if (error is IOException) emit(AppPreferences(readFailed = true)) else throw error
    }

    suspend fun installDefaults(repository: TimeRepository) {
        if (store.data.first()[defaultsKey] != true) {
            repository.installDefaultCategories()
            store.edit { it[defaultsKey] = true }
        }
    }
    suspend fun setTimer(mode: TimerMode, work: Int, rest: Int, cycles: Int) {
        if (work !in 1..180 || rest !in 1..60 || cycles !in 1..12) throw RuleViolation("invalid_time")
        store.edit { it[timerModeKey] = mode.name; it[workKey] = work; it[breakKey] = rest; it[cyclesKey] = cycles }
    }
    suspend fun setDayStart(minutes: Int) { require(minutes in 0..1439); store.edit { it[dayStartKey] = minutes } }
    suspend fun setWeekStart(day: Int) { require(day in 1..7); store.edit { it[weekStartKey] = day } }
    suspend fun setPreferCategory(value: Boolean) { store.edit { it[categoryNameKey] = value } }
    suspend fun setLanguage(tag: String) { require(tag in listOf("", "zh-CN", "en")); store.edit { it[languageKey] = tag } }
    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }
    suspend fun setHistory(policy: HistoricalPlanPolicy) { store.edit { it[historyKey] = policy.name } }
    suspend fun setOrb(action: OrbAction) { store.edit { it[orbKey] = action.name } }
    suspend fun setSnap(minutes: Int) { require(minutes == 15 || minutes == 30); store.edit { it[snapKey] = minutes } }
    suspend fun setWeekView(value: Boolean) { store.edit { it[weekKey] = value } }
    suspend fun setReviewCalendarPlans(value: Boolean) { store.edit { it[reviewCalendarPlansKey] = value } }
    suspend fun setMonthDetails(value: Boolean) { store.edit { it[monthDetailsKey] = value } }

    /** Saves the WebDAV account and, when a run is enabled, forgets the previous attempt's outcome. */
    suspend fun setWebDav(url: String, user: String, password: String, folder: String) {
        store.edit {
            it[webDavUrlKey] = url.trim()
            it[webDavUserKey] = user.trim()
            it[webDavPasswordKey] = password
            it[webDavFolderKey] = folder.trim().trim('/').ifBlank { "chronota" }
        }
    }

    suspend fun setAutoBackup(enabled: Boolean, minutes: Int) {
        require(minutes in 0..1439)
        store.edit { it[autoBackupKey] = enabled; it[autoBackupMinutesKey] = minutes }
    }

    /** Records how the last attempt went, success or failure alike. */
    suspend fun recordBackup(at: Instant, failure: WebDavFailure?) {
        store.edit { it[lastBackupAtKey] = at.toEpochMilli(); it[lastBackupFailureKey] = failure?.name ?: "" }
    }
}
