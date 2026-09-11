package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import app.chronota.R
import app.chronota.ui.theme.*

@Composable fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer), content = content)
}
@Composable fun OptionRow(title: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String? = null, enabled: Boolean = true, selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface, arrow: Boolean = true, value: String? = null, valueColor: Color? = null, valueContent: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = enabled, onClick = onClick).height(Metrics.controlHeight)
        .padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (icon != null) Icon(icon, null, Modifier.size(Metrics.icon), tint = tint.copy(alpha = if (enabled) 1f else .45f))
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = tint.copy(alpha = if (enabled) 1f else .45f))
        val detail = value ?: description
        valueContent?.invoke()
        if (!detail.isNullOrBlank()) Row(Modifier.widthIn(max = Metrics.optionValueWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            if (valueColor != null) Box(Modifier.size(Metrics.colorDot).clip(androidx.compose.foundation.shape.CircleShape).background(valueColor))
            Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (arrow) Icon(AppIcons.Next, null, Modifier.size(Metrics.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Icon-less option row: editors use this so only the settings page carries icons. */
@Composable fun OptionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String? = null, enabled: Boolean = true, selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface, arrow: Boolean = true, value: String? = null, valueColor: Color? = null, valueContent: (@Composable () -> Unit)? = null) =
    OptionRow(title, null, onClick, modifier, description, enabled, selected, tint, arrow, value, valueColor, valueContent)
fun fieldIcon(label: Int): ImageVector = when (label) {
    R.string.week_view, R.string.date, R.string.start_date, R.string.end_date, R.string.review_range_start, R.string.review_range_end -> AppIcons.Today
    R.string.day_start, R.string.default_timer_mode, R.string.start_time, R.string.end_time, R.string.work_minutes, R.string.break_minutes, R.string.timer -> AppIcons.Clock
    R.string.categories, R.string.parent_category, R.string.color_hex -> AppIcons.Categories
    R.string.note -> AppIcons.Book
    R.string.reminder_start -> AppIcons.Bell
    else -> AppIcons.Edit
}
