package app.chronota.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.chronota.R
import app.chronota.data.entity.Category
import app.chronota.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun FormField(@StringRes label: Int, value: String, change: (String) -> Unit, tag: String = "", singleLine: Boolean = true, enabled: Boolean = true) {
    PlainInput(stringResource(label), value, change, Modifier.testTag(tag), singleLine, enabled)
}

@Composable fun EditorSheet(@StringRes title: Int, onDismiss: () -> Unit, footer: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    PanelDialog(stringResource(title), onDismiss) {
        content()
        if (footer != null) {
            Spacer(Modifier.height(Space.sm))
            footer()
        }
    }
}

@Composable fun OptionalTitle(value: String, change: (String) -> Unit, tag: String, initiallyVisible: Boolean = true, enabled: Boolean = true) {
    var visible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initiallyVisible || value.isNotBlank()) }
    if (visible) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { FormField(R.string.title, value, change, tag, enabled = enabled) }
        if (enabled) IconButton(onClick = { visible = false; change("") }) { Icon(AppIcons.Close, stringResource(R.string.remove_title), Modifier.size(Metrics.icon)) }
    } else if (enabled) TextButton(onClick = { visible = true }, modifier = Modifier.height(Metrics.controlHeight)) {
        Icon(AppIcons.Add, null, Modifier.size(Metrics.icon)); Text(stringResource(R.string.add_title))
    }
}

@Composable fun EditorActions(busy: Boolean, onSave: (() -> Unit)?, onDelete: (() -> Unit)? = null, onDuplicate: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (onDuplicate != null) ActionButton(stringResource(R.string.duplicate), onDuplicate, Modifier.testTag("duplicate"), enabled = !busy, secondary = true)
        if (onSave != null) ActionButton(stringResource(if (busy) R.string.saving else R.string.save), onSave, Modifier.testTag("save"), enabled = !busy)
        if (onDelete != null) ActionButton(stringResource(R.string.delete), onDelete, enabled = !busy, destructive = true)
    }
}
@Composable fun ConfirmAction(@StringRes title: Int, @StringRes body: Int, onDismiss: () -> Unit, confirm: () -> Unit) {
    PanelDialog(stringResource(title), onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = confirm) { Text(stringResource(R.string.confirm)) }
        }
    }) { Text(stringResource(body)) }
}
@Composable fun NoticeDialog(@StringRes message: Int, dismiss: () -> Unit) {
    PanelDialog(null, dismiss, footer = { TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ok)) } }) { Text(stringResource(message), Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), style = MaterialTheme.typography.bodyLarge) }
}
@Composable fun <T> ChoiceField(@StringRes label: Int, value: T, options: List<Pair<T, String>>, select: (T) -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    var expanded by remember { mutableStateOf(false) }
    OptionRow(stringResource(label), icon, { expanded = true }, value = options.firstOrNull { it.first == value }?.second, enabled = enabled)
    if (expanded) SelectionDialog(stringResource(label), { expanded = false }) {
        options.forEach { (key, name) -> SelectionRow(name, key == value, { select(key); expanded = false }) }
    }
}
@Composable fun CategoryChoice(categories: List<Category>, id: Long?, select: (Long?) -> Unit, enabled: Boolean = true, emptyLabel: Int? = null) {
    var expanded by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == id }
    val description = stringResource(R.string.categories)
    Row(Modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = enabled) { expanded = true }
        .semantics { contentDescription = description }.padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        CategoryMark(category)
        Text(category?.name ?: stringResource(emptyLabel ?: R.string.uncategorized), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
    }
    if (expanded) CategoryPicker(categories, id, { select(it); expanded = false }, { expanded = false })
}
@Composable fun DateField(@StringRes label: Int, date: LocalDate?, select: (LocalDate?) -> Unit, optional: Boolean = true, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    OptionRow(stringResource(label), { open = true }, value = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(androidx.compose.ui.platform.LocalResources.current.configuration.locales[0])), enabled = enabled)
    if (open) CalendarDialog(stringResource(label), date ?: LocalDate.now(), { open = false }, clear = if (optional) ({ select(null); open = false }) else null) { select(it); open = false }
}
@Composable fun TimeField(@StringRes label: Int, time: LocalTime, select: (LocalTime) -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    var open by remember { mutableStateOf(false) }
    OptionRow(stringResource(label), icon, { open = true }, value = time.format(DateTimeFormatter.ofPattern("HH:mm")), enabled = enabled)
    if (open) ClockDialog(stringResource(label), time, { open = false }) { select(it); open = false }
}
val categoryIcons get() = CategoryIconLibrary.icons
@Composable fun CategoryMark(category: Category?) {
    Icon(categoryIcons.firstOrNull { it.first == category?.icon }?.second ?: AppIcons.Categories, null,
        tint = category?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Metrics.icon))
}
