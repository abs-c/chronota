package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import app.chronota.domain.logicalDate
import app.chronota.domain.weekStartOf
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * The seven dates of the week around [date]: weekday names that never move, dates that slide under a
 * swipe, and the selected day filled like a control.
 *
 * Today's header and the calendar's day view both draw this one, so the two read as the same view.
 * [onPrevious] and [onNext] are what a swipe turns — a week on Today, a day inside the calendar —
 * while the layout, the spacing and the slide animation stay identical.
 */
@Composable fun WeekStrip(date: LocalDate, weekStart: Int, select: (LocalDate) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    val today = logicalDate(rememberNow(), zone, LocalDisplayPreferences.current.dayStartMinutes)
    val monday = weekStartOf(date, weekStart)
    val swipe = rememberSwipeController()
    Column(modifier.fillMaxWidth().padding(horizontal = Space.md).swipeGestures(swipe, onPrevious, onNext)) {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(monday.plusDays(index.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().swipeTranslation(swipe)) {
            repeat(7) { index ->
                val day = monday.plusDays(index.toLong())
                Box(Modifier.weight(1f).testTag("week_day_$index").clickable { select(day) }.padding(vertical = Space.xxs), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(Metrics.calendarDay).clip(CircleShape)
                        .background(if (day == date) MaterialTheme.colorScheme.primary else if (day == today) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(day.dayOfMonth.toString(), color = if (day == date) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
