package app.chronotation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.chronotation.R
import app.chronotation.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun monthCells(month: YearMonth, weekStart: Int = 1): List<LocalDate?> {
    val leading = (month.atDay(1).dayOfWeek.value - weekStart + 7) % 7
    val size = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return List(size) { index -> (index - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
}

/** Weekday header that starts on the configured first day of the week. */
@Composable private fun WeekdayHeader(locale: Locale, weekStart: Int) {
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { offset -> Box(Modifier.weight(1f).height(Space.xl), contentAlignment = Alignment.Center) {
            Text(DayOfWeek.of(((weekStart - 1 + offset) % 7) + 1).getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable fun MultiDateDialog(title: String, selected: Set<LocalDate>, dismiss: () -> Unit, confirm: (Set<LocalDate>) -> Unit) {
    var chosen by remember { mutableStateOf(selected) }
    var displayed by rememberSaveable { mutableLongStateOf((selected.minOrNull() ?: LocalDate.now()).withDayOfMonth(1).toEpochDay()) }
    val month = YearMonth.from(LocalDate.ofEpochDay(displayed))
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { confirm(chosen) }) { Text(stringResource(R.string.confirm)) }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(Space.xs)) {
            Row(Modifier.fillMaxWidth().height(Metrics.controlHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy 年 M 月" else "MMMM yyyy", locale)),
                    Modifier.weight(1f).padding(horizontal = Space.sm), style = MaterialTheme.typography.titleMedium)
                IconButton({ displayed = month.minusMonths(1).atDay(1).toEpochDay() }) { Icon(AppIcons.Previous, stringResource(R.string.previous_month), Modifier.size(Metrics.icon)) }
                IconButton({ displayed = month.plusMonths(1).atDay(1).toEpochDay() }) { Icon(AppIcons.Next, stringResource(R.string.next_month), Modifier.size(Metrics.icon)) }
            }
            WeekdayHeader(locale, LocalDisplayPreferences.current.weekStart)
            monthCells(month, LocalDisplayPreferences.current.weekStart).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val active = day != null && day in chosen
                        Box(Modifier.weight(1f).height(Metrics.calendarCell).then(if (day == null) Modifier else Modifier.testTag("skip_$day").clickable { chosen = if (active) chosen - day else chosen + day }), contentAlignment = Alignment.Center) {
                            if (day != null) Box(Modifier.size(Metrics.calendarDay).clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .semantics { this.selected = active; contentDescription = day.toString() }, contentAlignment = Alignment.Center) {
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun CalendarDialog(title: String, date: LocalDate, dismiss: () -> Unit, clear: (() -> Unit)? = null, time: LocalTime? = null, selectDateTime: ((LocalDate, LocalTime) -> Unit)? = null, select: (LocalDate) -> Unit) {
    var selected by rememberSaveable { mutableLongStateOf(date.toEpochDay()) }
    var displayed by rememberSaveable { mutableLongStateOf(date.withDayOfMonth(1).toEpochDay()) }
    var clock by rememberSaveable { mutableStateOf((time?.hour ?: 0) * 60 + (time?.minute ?: 0)) }
    var chooseMonth by remember { mutableStateOf(false) }
    val month = YearMonth.from(LocalDate.ofEpochDay(displayed))
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (clear != null) TextButton(onClick = clear) { Text(stringResource(R.string.clear)) }
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { if (time != null) selectDateTime?.invoke(LocalDate.ofEpochDay(selected), LocalTime.of(clock / 60, clock % 60)) else select(LocalDate.ofEpochDay(selected)) }) { Text(stringResource(R.string.confirm)) }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(Space.xs)) {
            Row(Modifier.fillMaxWidth().height(Metrics.controlHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy 年 M 月" else "MMMM yyyy", locale)),
                    Modifier.weight(1f).height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).clickable { chooseMonth = true }.wrapContentHeight(Alignment.CenterVertically).padding(horizontal = Space.sm), style = MaterialTheme.typography.titleMedium)
                IconButton({ displayed = month.minusMonths(1).atDay(1).toEpochDay() }) { Icon(AppIcons.Previous, stringResource(R.string.previous_month), Modifier.size(Metrics.icon)) }
                IconButton({ displayed = month.plusMonths(1).atDay(1).toEpochDay() }) { Icon(AppIcons.Next, stringResource(R.string.next_month), Modifier.size(Metrics.icon)) }
            }
            WeekdayHeader(locale, LocalDisplayPreferences.current.weekStart)
            monthCells(month, LocalDisplayPreferences.current.weekStart).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val active = day?.toEpochDay() == selected
                        Box(Modifier.weight(1f).height(Metrics.calendarCell)
                            .then(if (day == null) Modifier else Modifier.testTag("calendar_$day").clickable { selected = day.toEpochDay() }), contentAlignment = Alignment.Center) {
                            if (day != null) Box(Modifier.size(Metrics.calendarDay).clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .semantics { this.selected = active; contentDescription = day.toString() }, contentAlignment = Alignment.Center) {
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else if (day == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    
        if (time != null) Column(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(stringResource(R.string.time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeWheel(clock / 60, clock % 60, { hour, minute -> clock = hour * 60 + minute })
        }
    }
    if (chooseMonth) {
        var year by remember { mutableStateOf(month.year.toString()) }
        SelectionDialog(stringResource(R.string.choose_month), { chooseMonth = false }) {
            PlainInput(stringResource(R.string.calendar_year), year, { year = it }, keyboardType = KeyboardType.Number)
            (1..12).chunked(3).forEach { row -> Row(Modifier.fillMaxWidth()) {
                row.forEach { number -> TextButton(onClick = {
                    year.toIntOrNull()?.takeIf { it in 1..9999 }?.let { displayed = LocalDate.of(it, number, 1).toEpochDay(); chooseMonth = false }
                }, Modifier.weight(1f).height(Metrics.controlHeight), enabled = year.toIntOrNull() in 1..9999) {
                    Text(Month.of(number).getDisplayName(TextStyle.SHORT, locale))
                } }
            } }
        }
    }
}
