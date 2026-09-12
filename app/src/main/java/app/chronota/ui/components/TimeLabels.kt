package app.chronota.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import app.chronota.R
import app.chronota.data.entity.Plan
import app.chronota.data.entity.Recurrence
import app.chronota.domain.*
import app.chronota.ui.theme.Metrics
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun timeText(value: Instant, zone: ZoneId = ZoneId.systemDefault()): String = value.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
@Composable fun dateText(date: LocalDate): String {
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    // Chinese dates keep a space between numerals and characters, matching the rest of the app.
    return if (locale.language == "zh") date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", locale))
    else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
@Composable fun rememberNow(): Instant = produceState(Instant.now()) {
    while (true) { value = Instant.now(); delay(30_000) }
}.value
/** Durations show only the parts that matter: "1 小时", "30 分", "1 小时 30 分". */
@Composable fun durationText(millis: Long): String {
    val hours = millis / 3_600_000
    val minutes = millis / 60_000 % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_value, hours, minutes)
        hours > 0 -> stringResource(R.string.hours_value, hours)
        else -> stringResource(R.string.duration_minutes, minutes)
    }
}

/** Reminder offsets read as minutes, whole hours, or hours plus minutes. */
@Composable fun reminderText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.on_time)
    minutes % 60 == 0 -> stringResource(R.string.hours_value, minutes / 60)
    minutes > 60 -> stringResource(R.string.hours_minutes_value, minutes / 60, minutes % 60)
    else -> stringResource(R.string.minutes_value, minutes)
}

@Composable fun recurrenceText(recurrence: Recurrence, interval: Int = 1): String {
    val step = interval.coerceAtLeast(1)
    return when (recurrence) {
        Recurrence.NONE -> stringResource(R.string.recurrence_none)
        Recurrence.DAILY -> if (step == 1) stringResource(R.string.recurrence_daily) else pluralStringResource(R.plurals.recurrence_every_days, step, step)
        Recurrence.WEEKLY -> if (step == 1) stringResource(R.string.recurrence_weekly) else pluralStringResource(R.plurals.recurrence_every_weeks, step, step)
        Recurrence.MONTHLY -> if (step == 1) stringResource(R.string.recurrence_monthly) else pluralStringResource(R.plurals.recurrence_every_months, step, step)
        Recurrence.YEARLY -> if (step == 1) stringResource(R.string.recurrence_yearly) else pluralStringResource(R.plurals.recurrence_every_years, step, step)
    }
}

/** Unit suffix for the recurrence interval input, e.g. "3 days" / "3 天". */
@Composable fun intervalUnitText(recurrence: Recurrence, count: Int): String = when (recurrence) {
    Recurrence.NONE -> ""
    Recurrence.DAILY -> pluralStringResource(R.plurals.unit_days, count, count)
    Recurrence.WEEKLY -> pluralStringResource(R.plurals.unit_weeks, count, count)
    Recurrence.MONTHLY -> pluralStringResource(R.plurals.unit_months, count, count)
    Recurrence.YEARLY -> pluralStringResource(R.plurals.unit_years, count, count)
}

/** Unit suffix for minute inputs, e.g. "30 minutes" / "30 分钟". */
@Composable fun minutesUnitText(count: Int): String = pluralStringResource(R.plurals.unit_minutes, count, count)

@Composable fun recurrenceText(plan: Plan): String = if (plan.recurrenceUntil != null) {
    stringResource(R.string.recurrence_until, recurrenceText(plan.recurrence, plan.recurrenceInterval), dateText(plan.recurrenceUntil))
} else recurrenceText(plan.recurrence, plan.recurrenceInterval)

/**
 * "10:00–11:00", or a single "10:00" when the span has no length: an item with no duration is a
 * moment, so the app never prints a range for it. A range that crosses midnight prints both dates,
 * because "23:00–01:00" would not say which day the end lands on.
 */
@Composable fun spanTimeText(span: TimeSpan, zone: ZoneId = ZoneId.systemDefault()): String {
    val ends = rangeEndsText(span, zone)
    return if (ends.first == ends.second) ends.first else ends.first + "–" + ends.second
}

/**
 * The two ends of a range as separate strings. They are bare clock times while both ends share a
 * calendar date; once the range crosses one, each end carries its date, and the year joins in only
 * when the range crosses a year as well.
 */
@Composable fun rangeEndsText(span: TimeSpan, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val start = span.start.atZone(zone)
    val end = span.end.atZone(zone)
    if (span.millis == 0L || start.toLocalDate() == end.toLocalDate()) return timeText(span.start, zone) to timeText(span.end, zone)
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    val withYear = start.year != end.year
    val pattern = when {
        locale.language == "zh" && withYear -> "yyyy 年 M 月 d 日 HH:mm"
        locale.language == "zh" -> "M 月 d 日 HH:mm"
        withYear -> "MMM d, yyyy HH:mm"
        else -> "MMM d HH:mm"
    }
    val format = DateTimeFormatter.ofPattern(pattern, locale)
    return start.format(format) to end.format(format)
}

/**
 * A clock time on an axis. Once the logical day starts late, the hours after midnight belong to the
 * next calendar date; they say so with a small raised "+1" at their top right. The gutter holding
 * these labels is sized for the marked form, so the marker never reaches the blocks beside it.
 */
@Composable fun axisClockText(value: Instant, logicalDate: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): AnnotatedString =
    withNextDayMarker(timeText(value, zone), logicalDate != null && value.atZone(zone).toLocalDate() != logicalDate)

/** The same rule for a gutter label, which is built from the hour rather than from an instant. */
@Composable fun axisHourText(hour: LocalTime, dayStartMinutes: Int): AnnotatedString {
    val dayStart = LocalTime.ofSecondOfDay(dayStartMinutes.coerceIn(0, 1439) * 60L)
    return withNextDayMarker(hour.format(DateTimeFormatter.ofPattern("HH:mm")), hour < dayStart)
}

/**
 * The week's hour gutter. Every label down that column is a whole hour, so the minutes are dropped:
 * "04" instead of "04:00" hands the seven day columns visibly more room. Only the marker is the same
 * as the day view's; the line under each label is drawn the same way in both.
 */
@Composable fun axisHourNumberText(hour: LocalTime, dayStartMinutes: Int): AnnotatedString {
    val dayStart = LocalTime.ofSecondOfDay(dayStartMinutes.coerceIn(0, 1439) * 60L)
    return withNextDayMarker(hour.format(DateTimeFormatter.ofPattern("HH")), hour < dayStart)
}

/** The clock time, with the next-date marker set smaller and raised so it reads as a footnote. */
@Composable fun withNextDayMarker(text: String, nextDate: Boolean): AnnotatedString {
    val marker = stringResource(R.string.day_offset_suffix, 1)
    return buildAnnotatedString {
        append(text)
        if (nextDate) {
            append(" ")
            withStyle(SpanStyle(fontSize = Metrics.compactLabelSize, baselineShift = BaselineShift.Superscript)) { append(marker) }
        }
    }
}

/**
 * The two ends as an agenda row prints them. A range that crosses a calendar day spells out both
 * dates, which already says where the end lands; otherwise the ends are clock times, and one that
 * fell on the next calendar date of [logicalDate] carries the raised "+1" instead.
 */
@Composable fun axisRangeEndsText(span: TimeSpan, logicalDate: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): Pair<AnnotatedString, AnnotatedString> {
    val start = span.start.atZone(zone)
    val end = span.end.atZone(zone)
    val crosses = span.millis > 0 && start.toLocalDate() != end.toLocalDate()
    val ends = rangeEndsText(span, zone)
    @Composable fun mark(text: String, day: LocalDate) = withNextDayMarker(text, !crosses && logicalDate != null && day != logicalDate)
    return mark(ends.first, start.toLocalDate()) to mark(ends.second, end.toLocalDate())
}


@Composable fun planTimeText(plan: Plan): String {
    val date = plan.scheduledDate ?: return stringResource(R.string.unscheduled)
    val repeat = if (plan.isRecurring()) " · " + recurrenceText(plan) else ""
    if (plan.allDay) return dateText(date) + (if (plan.endDayOffset > 0) "–" + dateText(date.plusDays(plan.endDayOffset.toLong())) else "") + " · " + stringResource(R.string.all_day) + repeat
    val span = plan.occurrenceSpan(date)
    if (span == null) return dateText(date) + repeat
    val ends = rangeEndsText(span, ZoneId.of(plan.zoneId))
    val range = if (ends.first == ends.second) ends.first else ends.first + "–" + ends.second
    return stringResource(R.string.field_value, dateText(date), range) + repeat
}
fun PlanTemporalState.label(): Int = when (this) {
    PlanTemporalState.UNSCHEDULED -> R.string.state_unscheduled
    PlanTemporalState.FUTURE -> R.string.state_future
    PlanTemporalState.ACTIVE -> R.string.state_active
    PlanTemporalState.PAST -> R.string.state_past
}
