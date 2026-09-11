package app.chronotation.feature.settings

import androidx.compose.foundation.layout.*
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import app.chronotation.R
import app.chronotation.domain.*
import app.chronotation.feature.today.label
import app.chronotation.ui.components.*
import app.chronotation.ui.theme.*

@Composable
fun SettingsScreen(themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit, onBack: () -> Unit,
    onCategories: () -> Unit = {}, historyPolicy: HistoricalPlanPolicy = HistoricalPlanPolicy.IMMUTABLE, onHistory: (HistoricalPlanPolicy) -> Unit = {},
    orbAction: OrbAction = OrbAction.RECORD, onOrbAction: (OrbAction) -> Unit = {},
    notificationSettings: () -> Unit = {}, exactSettings: (() -> Unit)? = null, weekView: Boolean = true, onWeekView: (Boolean) -> Unit = {},
    timerPreferences: app.chronotation.data.repository.AppPreferences = app.chronotation.data.repository.AppPreferences(),
    onLanguage: (String) -> Unit = {}, onWeekStart: (Int) -> Unit = {},
    onDayStart: (Int) -> Unit = {}, onPreferCategory: (Boolean) -> Unit = {},
    onReviewCalendarPlans: (Boolean) -> Unit = {}, onMonthDetails: (Boolean) -> Unit = {},
    onBackupExport: () -> Unit = {}, onBackupImport: () -> Unit = {},
    onBackupSave: (String, String, String, String, Boolean, Int, () -> Unit) -> Unit = { _, _, _, _, _, _, done -> done() },
    onBackupTest: (String, String, String, String, (Int?) -> Unit) -> Unit = { _, _, _, _, result -> result(R.string.backup_error_config) },
    onBackupNow: ((Int?) -> Unit) -> Unit = { result -> result(R.string.backup_error_config) },
    onBackupRestore: ((Int?) -> Unit) -> Unit = { result -> result(R.string.backup_error_config) },
    onTimerSettings: (app.chronotation.data.entity.TimerMode, Int, Int, Int, () -> Unit) -> Unit = { _, _, _, _, _ -> }) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    PageColumn {
        Column(Modifier.testTag("settings_list").verticalScroll(rememberScrollState()).padding(Space.md).padding(bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            // Ordered from what the app stores outwards to how it looks: content, then the time model
            // it is filed under, then what each view shows, then appearance, touch and notifications.
            SettingsSection(R.string.settings_data) {
            OptionRow(stringResource(R.string.categories), AppIcons.Categories, onCategories)
            SwitchRow(R.string.allow_history, historyPolicy == HistoricalPlanPolicy.EDITABLE, { onHistory(if (it) HistoricalPlanPolicy.EDITABLE else HistoricalPlanPolicy.IMMUTABLE) }, icon = AppIcons.Notebook)
            }
            SettingsSection(R.string.settings_time) {
            TimeField(R.string.day_start, java.time.LocalTime.ofSecondOfDay(timerPreferences.dayStartMinutes * 60L), { onDayStart(it.hour * 60 + it.minute) }, icon = AppIcons.Sunrise)
            ChoiceField(R.string.week_start, timerPreferences.weekStart, listOf(7 to stringResource(R.string.week_start_sunday), 1 to stringResource(R.string.week_start_monday)), onWeekStart, icon = AppIcons.WeekStart)
            OptionRow(stringResource(R.string.timer_settings), AppIcons.Timer, { panel = "timer" })
            }
            SettingsSection(R.string.settings_display) {
            SwitchRow(R.string.week_view, weekView, onWeekView, icon = AppIcons.Today)
            ChoiceField(R.string.item_heading, timerPreferences.preferCategoryName,
                listOf(false to stringResource(R.string.heading_title), true to stringResource(R.string.heading_category)), onPreferCategory, icon = AppIcons.FileText)
            SwitchRow(R.string.review_calendar_plans, timerPreferences.reviewCalendarPlans, onReviewCalendarPlans, icon = AppIcons.Review)
            SwitchRow(R.string.month_details, timerPreferences.monthDetails, onMonthDetails, icon = AppIcons.Settings)
            }
            SettingsSection(R.string.settings_appearance) {
            OptionRow(stringResource(R.string.appearance), AppIcons.Palette, { panel = "theme" }, description = stringResource(themeMode.label()))
            OptionRow(stringResource(R.string.language), AppIcons.Earth, { panel = "language" }, description = stringResource(languageLabel(timerPreferences.language)))
            }
            SettingsSection(R.string.settings_interaction) {
            OptionRow(stringResource(R.string.orb_action), AppIcons.Orbit, { panel = "orb" }, description = stringResource(orbAction.label()))
            }
            SettingsSection(R.string.settings_backup) {
            OptionRow(stringResource(R.string.backup_export), AppIcons.Copy, onBackupExport, description = stringResource(R.string.backup_export_hint))
            OptionRow(stringResource(R.string.backup_import), AppIcons.Copy, onBackupImport, description = stringResource(R.string.backup_import_hint))
            OptionRow(stringResource(R.string.backup_webdav), AppIcons.Cloud, { panel = "backup" }, description = lastBackupSummary(timerPreferences))
            }
            SettingsSection(R.string.settings_notifications) {
            OptionRow(stringResource(R.string.notification_settings), AppIcons.Bell, notificationSettings)
            if (exactSettings != null) OptionRow(stringResource(R.string.exact_alarms), AppIcons.Alarm, exactSettings)
            }
        }
    }
    when (panel) {
        "timer" -> TimerSettings(timerPreferences, { panel = null }, onTimerSettings)
        "backup" -> WebDavSettings(timerPreferences, false, { panel = null }, onBackupSave, onBackupTest, onBackupNow, onBackupRestore)
        "theme" -> SelectionDialog(stringResource(R.string.appearance), { panel = null }) {
            ThemeMode.entries.forEach { mode -> SelectionRow(stringResource(mode.label()), themeMode == mode, { onTheme(mode) },
                Modifier.testTag("theme_${mode.name}").semantics { selected = themeMode == mode }) }
        }
        "orb" -> SelectionDialog(stringResource(R.string.orb_action), { panel = null }) {
            orbActions.forEach { action -> SelectionRow(stringResource(action.label()), action == orbAction, { onOrbAction(action); panel = null }) }
        }
        "language" -> SelectionDialog(stringResource(R.string.language), { panel = null }) {
            listOf("", "zh-CN", "en").forEach { tag ->
                SelectionRow(stringResource(languageLabel(tag)), timerPreferences.language == tag, { onLanguage(tag); panel = null })
            }
        }
    }
}

private fun ThemeMode.label(): Int = when (this) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }

private fun languageLabel(tag: String): Int = when (tag) { "zh-CN" -> R.string.language_chinese; "en" -> R.string.language_english; else -> R.string.theme_system }

/** One captioned block of settings: a small heading over a flat group of rows. */
@Composable private fun SettingsSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(Space.xxs))
    Text(stringResource(title), Modifier.padding(start = Space.md, top = Space.xs, bottom = Space.xxs),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OptionGroup(content = content)
}
