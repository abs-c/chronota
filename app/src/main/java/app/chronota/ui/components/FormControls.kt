package app.chronota.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.chronota.R
import app.chronota.ui.theme.*
import java.time.*
import java.time.format.*


@Composable fun PanelDialog(title: String?, dismiss: () -> Unit, footer: (@Composable ColumnScope.() -> Unit)? = null, header: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {

        Surface(modifier = Modifier.padding(horizontal = Metrics.panelMargin).widthIn(max = Metrics.panelWidth).fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = Metrics.dialogElevation) {
            Column(Modifier.fillMaxWidth().heightIn(max = Metrics.panelHeight).padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                if (title != null) Row(Modifier.fillMaxWidth().height(Metrics.tabHeight), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = dismiss, modifier = Modifier.size(Metrics.tabHeight)) { Icon(AppIcons.Close, stringResource(R.string.close), Modifier.size(Metrics.icon)) }
                }
                if (header != null) header()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xs), content = content)
                if (footer != null) Column(Modifier.padding(top = Space.sm)) { footer() }
            }
        }
    }
}
@Composable fun PlainInput(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true, enabled: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, textAlign: TextAlign = TextAlign.Start, password: Boolean = false) {
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(value, change,
        modifier.fillMaxWidth().heightIn(min = if (singleLine) Metrics.controlHeight else Metrics.multilineHeight).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer).semantics { contentDescription = label },
        enabled = enabled, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = if (singleLine) Metrics.controlHeight else Metrics.multilineHeight).padding(horizontal = Space.md, vertical = if (singleLine) Space.xxs else Space.md), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) Text(label, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge.copy(textAlign = textAlign, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)), maxLines = 1)
                Box(Modifier.fillMaxWidth(), contentAlignment = if (singleLine) if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart else Alignment.TopStart) { inner() }
            }
        })
}
@Composable fun ActionButton(title: String, click: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, destructive: Boolean = false, secondary: Boolean = false) {
    val container = when { destructive -> MaterialTheme.colorScheme.errorContainer; secondary -> MaterialTheme.colorScheme.surfaceContainerHighest; else -> MaterialTheme.colorScheme.primary }
    val foreground = when { destructive -> MaterialTheme.colorScheme.onErrorContainer; secondary -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.onPrimary }
    Row(modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small)
        .background(container.copy(alpha = if (enabled) 1f else .5f)).clickable(enabled = enabled, onClick = click),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(Metrics.icon), tint = foreground); Spacer(Modifier.width(Space.xs)) }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = foreground)
    }
}
@Composable fun SelectionDialog(title: String, dismiss: () -> Unit, clear: (() -> Unit)? = null, header: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (clear != null) TextButton(onClick = clear) { Text(stringResource(R.string.clear)) }
            TextButton(onClick = dismiss) { Text(stringResource(R.string.confirm)) }
        }
    }, header = header, content = content)
}
@Composable fun SelectionRow(title: String, selected: Boolean, click: () -> Unit, modifier: Modifier = Modifier, multi: Boolean = false, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small)
        .clickable(onClick = click).padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
        if (multi) Checkbox(selected, null, modifier = Modifier.size(Metrics.check), colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color))
        else RadioButton(selected, null, modifier = Modifier.size(Metrics.check), colors = RadioButtonDefaults.colors(selectedColor = color, unselectedColor = color))
        Text(title, Modifier.padding(start = Space.xs), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable fun SwitchRow(@StringRes label: Int, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change).padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(Metrics.icon)); Spacer(Modifier.width(Space.xs)) }
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, null, enabled = enabled, modifier = Modifier.size(width = Metrics.switchWidth, height = Metrics.controlHeight).graphicsLayer { scaleX = Metrics.switchScale; scaleY = Metrics.switchScale })
    }
}

@Composable fun AddRow(@StringRes label: Int, click: () -> Unit, modifier: Modifier = Modifier) {
    ActionButton(stringResource(label), click, modifier, icon = AppIcons.Add, secondary = true)
}
@Composable fun AddTextRow(@StringRes label: Int, click: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).clickable(onClick = click),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Add, null, Modifier.size(Metrics.icon), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.xs))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
@Composable fun DateTimeField(@StringRes label: Int, date: LocalDate, time: LocalTime, allDay: Boolean = false, enabled: Boolean = true, change: (LocalDate, LocalTime) -> Unit) {
    var calendar by remember { mutableStateOf(false) }
    OptionRow(stringResource(label), { calendar = true },
        value = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(LocalResources.current.configuration.locales[0])) + if (allDay) "" else " " + time.format(DateTimeFormatter.ofPattern("HH:mm")), enabled = enabled)
    if (calendar) CalendarDialog(stringResource(label), date, { calendar = false }, time = time.takeUnless { allDay },
        selectDateTime = { d, t -> change(d, t); calendar = false }) { change(it, time); calendar = false }
}
@Composable fun ClockDialog(title: String, time: LocalTime, dismiss: () -> Unit, select: (LocalTime) -> Unit) {
    var minutes by rememberSaveable(title) { mutableStateOf(time.hour * 60 + time.minute) }
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { select(LocalTime.of(minutes / 60, minutes % 60)) }) { Text(stringResource(R.string.confirm)) }
        }
    }) {
        TimeWheel(minutes / 60, minutes % 60, { hour, minute -> minutes = hour * 60 + minute }, Modifier.padding(vertical = Space.sm))
    }
}
@Composable fun NumberDialog(title: String, label: String, value: Int, range: IntRange, dismiss: () -> Unit, unit: (@Composable (Int) -> String)? = null, confirm: (Int) -> Unit) {
    var text by rememberSaveable(title) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(enabled = parsed in range, onClick = { parsed?.let(confirm) }) { Text(stringResource(R.string.confirm)) }
        }
    }) {
        Box(Modifier.fillMaxWidth()) {
            PlainInput(label, text, { text = it.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.Number)
            if (unit != null) Text(unit(parsed ?: value), Modifier.align(Alignment.CenterEnd).padding(end = Space.md), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Number plus unit picker, shared by goal targets, goal time frames and plan intervals. */
@Composable fun <T> AmountDialog(title: String, amount: Int, unit: T, units: List<T>, dismiss: () -> Unit, label: @Composable (T) -> String, confirm: (Int, T) -> Unit) {
    var text by rememberSaveable(title) { mutableStateOf(amount.toString()) }
    var selected by rememberSaveable(title) { mutableStateOf(unit) }
    var menu by remember { mutableStateOf(false) }
    val parsed = text.toIntOrNull()
    PanelDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(enabled = parsed != null && parsed > 0, onClick = { confirm(parsed!!, selected) }) { Text(stringResource(R.string.confirm)) }
        }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlainInput("", text, { text = it.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.Number, textAlign = TextAlign.Center) }
            Row(Modifier.weight(1f).height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { menu = true }.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(label(selected), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(AppIcons.Down, null, Modifier.size(Metrics.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (menu) SelectionDialog(title, { menu = false }) {
        units.forEach { option -> SelectionRow(label(option), option == selected, { selected = option; menu = false }) }
    }
}
