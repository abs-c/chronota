package app.chronota.feature.category

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.chronota.R
import app.chronota.data.entity.*
import app.chronota.ui.components.*
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import kotlin.math.roundToInt

val categoryPalette = app.chronota.ui.theme.categoryColorRows.drop(1).map { it[3] }

@Composable fun IconEditor(icon: String, color: Long, change: (String, Long) -> Unit, dismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SelectionDialog(stringResource(R.string.edit_icon), dismiss, header = {
        SegmentedTabs(listOf(R.string.icon_tab, R.string.color_tab), tab, { tab = it }, "icon_tab", Modifier.fillMaxWidth())
    }) {
        if (tab == 0) categoryIcons.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (key, vector) -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { change(key, color) }, modifier = Modifier.size(app.chronota.ui.theme.Metrics.controlHeight),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (key == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                        Icon(vector, stringResource(R.string.icon_number, categoryIcons.indexOfFirst { it.first == key } + 1), tint = Color(color), modifier = Modifier.size(Metrics.iconLarge))
                    }
                } }
                repeat(5-row.size) { Spacer(Modifier.weight(1f)) }
            }
        } else ColorPalette(color) { change(icon, it) }
    }
}
@Composable fun ColorPalette(color: Long, select: (Long) -> Unit) {
    app.chronota.ui.theme.categoryColorRows.forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { item -> Box(Modifier.weight(1f).height(Metrics.controlHeight).clickable { select(item) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(Metrics.iconLarge).background(Color(item), MaterialTheme.shapes.small).border(if (color == item) Metrics.outline * 2 else 0.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small))
            } }
        }
    }
}

@Composable fun PropertyEditor(value: PropertyDefinition, busy: Boolean, dismiss: () -> Unit, save: (PropertyDefinition) -> Unit, delete: (() -> Unit)? = null) {
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var type by rememberSaveable(value.id) { mutableStateOf(value.type) }
    var multiline by rememberSaveable(value.id) { mutableStateOf(value.multiline) }
    var required by rememberSaveable(value.id) { mutableStateOf(value.required) }
    var options by rememberSaveable(value.id) { mutableStateOf(value.options.lines().filter { it.isNotBlank() }) }
    var colors by rememberSaveable(value.id) { mutableStateOf(options.indices.map { value.optionColors.lines().getOrNull(it)?.toLongOrNull(16)?.or(0xFF000000) ?: categoryPalette[it % categoryPalette.size] }) }
    var defaultValue by rememberSaveable(value.id) { mutableStateOf(value.defaultValue) }
    var unit by rememberSaveable(value.id) { mutableStateOf(value.unit) }
    var defaultOpen by remember { mutableStateOf(false) }
    var colorIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    EditorSheet(if (value.name.isEmpty()) R.string.new_property else R.string.edit_property, dismiss) {
        FormField(R.string.name, name, { name = it })
        ChoiceField(R.string.property_type, type, PropertyType.entries.map { it to stringResource(it.label()) }, { type = it })
        SwitchRow(R.string.required_property, required, { required = it })
        if (type == PropertyType.NUMBER) FormField(R.string.property_unit, unit, { unit = it.take(8) })
        if (type == PropertyType.TEXT) ChoiceField(R.string.text_length, multiline,
            listOf(false to stringResource(R.string.short_text), true to stringResource(R.string.long_text)), { multiline = it })
        if (type in listOf(PropertyType.SELECT, PropertyType.MULTISELECT)) {
            // Stable ids keep the rows animatable while their order changes.
            var ids by remember(value.id) { mutableStateOf(options.indices.map { it.toLong() }) }
            var nextId by remember(value.id) { mutableLongStateOf(options.size.toLong()) }
            fun <T> MutableList<T>.move(from: Int, to: Int) { add(to, removeAt(from)) }
            ReorderableRows(options, key = { index, _ -> ids[index] }, pitch = Metrics.controlHeight + Space.xs, onMove = { from, to ->
                options = options.toMutableList().apply { move(from, to) }
                colors = colors.toMutableList().apply { move(from, to) }
                ids = ids.toMutableList().apply { move(from, to) }
            }) { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { colorIndex = index }) { Box(Modifier.size(Metrics.check).background(Color(colors[index]), CircleShape)) }
                    Box(Modifier.weight(1f)) { FormField(R.string.option_name, option, { text -> options = options.toMutableList().apply { set(index, text) } }) }
                    IconButton(onClick = { options = options.filterIndexed { i, _ -> i != index }; colors = colors.filterIndexed { i, _ -> i != index }; ids = ids.filterIndexed { i, _ -> i != index } }) { Icon(AppIcons.Close, stringResource(R.string.delete), Modifier.size(Metrics.icon)) }
                }
            }
            AddRow(R.string.add_option, { options = options + ""; colors = colors + categoryPalette[options.size % categoryPalette.size]; ids = ids + nextId; nextId++ })
            OptionRow(stringResource(R.string.default_option), { defaultOpen = true }, value = defaultValue.lines().joinToString("、").ifBlank { null }, enabled = options.any { it.isNotBlank() })
        }
        if (error) Text(stringResource(R.string.error_required), color = MaterialTheme.colorScheme.error)
        EditorActions(busy, {
            val choices = options.map { it.trim() }
            val hasChoices = type in listOf(PropertyType.SELECT, PropertyType.MULTISELECT)
            if (name.isBlank() || (hasChoices && (choices.isEmpty() || choices.any { it.isBlank() } || choices.distinct().size != choices.size))) error = true
            else save(value.copy(name = name.trim(), type = type, required = required, multiline = multiline, defaultValue = if (hasChoices) defaultValue.lines().filter { it in choices }.joinToString("\n") else "", options = if (hasChoices) choices.joinToString("\n") else "",
                optionColors = if (hasChoices) colors.joinToString("\n") { "%06X".format(it and 0xFFFFFF) } else "", unit = if (type == PropertyType.NUMBER) unit.trim().take(8) else ""))
        }, if (delete != null) ({ confirmDelete = true }) else null)
    }
    if (confirmDelete && delete != null) ConfirmAction(R.string.delete, R.string.delete_property_warning, { confirmDelete = false }, { confirmDelete = false; delete() })
    if (defaultOpen) PropertyOptions(value.copy(name = stringResource(R.string.default_option), type = type, options = options.joinToString("\n"), optionColors = colors.joinToString("\n") { "%06X".format(it and 0xFFFFFF) }), defaultValue, true, { defaultOpen = false }) { defaultValue = it }
    colorIndex?.let { index -> SelectionDialog(stringResource(R.string.color_hex), { colorIndex = null }) {
        ColorPalette(colors[index]) { color -> colors = colors.toMutableList().apply { set(index, color) }; colorIndex = null }
    } }
}

@Composable fun PropertyManager(category: Long, definitions: List<PropertyDefinition>, busy: Boolean, dismiss: () -> Unit,
    save: (PropertyDefinition, () -> Unit) -> Unit, delete: (Long) -> Unit) {
    var editing by remember { mutableStateOf<PropertyDefinition?>(null) }
    EditorSheet(R.string.properties, dismiss) {
        definitions.filter { it.categoryId == category }.forEach { definition -> OptionRow(definition.name, { editing = definition }) }
        AddRow(R.string.task_properties, { editing = PropertyDefinition(categoryId = category, name = "", type = PropertyType.TEXT) })
    }
    editing?.let { value -> PropertyEditor(value, busy, { editing = null }, { save(it) { editing = null } }, if (value.id == 0L) null else ({ delete(value.id); editing = null })) }
}

fun PropertyType.label(): Int = when (this) {
    PropertyType.TEXT -> R.string.property_text
    PropertyType.NUMBER -> R.string.property_number
    PropertyType.BOOLEAN -> R.string.property_boolean
    PropertyType.SELECT -> R.string.property_select
    PropertyType.MULTISELECT -> R.string.property_multiselect
    PropertyType.RATING -> R.string.property_rating
}
