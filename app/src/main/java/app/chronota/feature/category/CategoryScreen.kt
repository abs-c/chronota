package app.chronota.feature.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chronota.data.entity.*
import app.chronota.domain.droppedValues
import app.chronota.feature.WorkspaceState
import app.chronota.feature.WorkspaceViewModel
import app.chronota.feature.workspaceModel
import app.chronota.ui.components.*
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import app.chronota.R
import kotlin.math.roundToInt

/**
 * Values already saved that editing a category into [definitions] would drop: only attributes whose
 * choices or type change — or that disappear — lose data.
 */
private fun droppedPropertyValues(id: Long, definitions: List<PropertyDefinition>, state: WorkspaceState): Int =
    state.definitions.filter { it.categoryId == id }.sumOf { old ->
        val next = definitions.firstOrNull { it.id == old.id }
        val stored = state.values.filter { it.definitionId == old.id }.map { it.value } +
            state.planValues.filter { it.definitionId == old.id }.map { it.value }
        droppedValues(old, next, stored).size
    }

@Composable
fun CategoryScreen(onBack: () -> Unit, model: WorkspaceViewModel = workspaceModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var newParent by rememberSaveable { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    // A category edit that would drop stored values waits here for the user to accept the loss.
    var pending by remember { mutableStateOf<Pair<Category, List<PropertyDefinition>>?>(null) }
    var pendingLoss by remember { mutableIntStateOf(0) }
    val bounds = remember { mutableMapOf<Long, Rect>() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(dragging) {
        while (dragging != null) {
            val edge = 90f
            val speed = when { dragPoint.y < listBounds.top + edge -> -12f; dragPoint.y > listBounds.bottom - edge -> 12f; else -> 0f }
            if (speed != 0f) listState.scrollBy(speed)
            kotlinx.coroutines.delay(16)
        }
    }
    val currentCategories by rememberUpdatedState(state.categories)
    fun dragModifier(category: Category): Modifier = Modifier.onGloballyPositioned { bounds[category.id] = it.boundsInRoot() }
        .pointerInput(category.id) {
            detectDragGesturesAfterLongPress(onDragStart = { point ->
                dragging = category.id; dragDelta = Offset.Zero; dragPoint = (bounds[category.id]?.topLeft ?: Offset.Zero) + point
            }, onDrag = { change, amount -> change.consume(); dragDelta += amount; dragPoint += amount },
                onDragCancel = { dragging = null; dragDelta = Offset.Zero },
                onDragEnd = {
                    val targets = currentCategories.filter { it.id != category.id && (category.parentId != null || it.parentId == null) }
                    val target = targets.filter { bounds[it.id]?.contains(dragPoint) == true }.minByOrNull { bounds[it.id]!!.width * bounds[it.id]!!.height }
                    if (target != null) model.perform { model.repository.moveCategory(category.id, target.id) }
                    dragging = null; dragDelta = Offset.Zero
                })
        }
    LaunchedEffect(model) { model.messages.collect { notice = it } }
    PageColumn {
        // Same banner metrics as PageHeader: banner height, 8dp inset, titleLarge.
        Row(Modifier.fillMaxWidth().heightIn(min = Metrics.pageHeader).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, stringResource(R.string.back), Modifier.size(Metrics.icon)) }
            Text(stringResource(R.string.category_management), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f).topFade().testTag("category_list").onGloballyPositioned { listBounds = it.boundsInRoot() }, state = listState, contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {

            state.categories.filter { it.parentId == null }.forEach { parent ->
                item(key = parent.id) {
                    var expanded by rememberSaveable(parent.id) { mutableStateOf(true) }
                    DisposableEffect(parent.id) { onDispose { bounds.remove(parent.id) } }
                    Column(Modifier.fillMaxWidth().zIndex(if (dragging == parent.id) 2f else 0f)
                        .animateItem()
                        .graphicsLayer { if (dragging == parent.id) { translationY = dragDelta.y; alpha = .8f } }
                        .animateContentSize()
                        .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(dragModifier(parent), verticalAlignment = Alignment.CenterVertically) {
                            OptionRow(parent.name, if (expanded) AppIcons.Down else AppIcons.Next, { expanded = !expanded }, Modifier.weight(1f), arrow = false)
                            IconButton(onClick = { editing = parent.id }) { Icon(AppIcons.Edit, stringResource(R.string.edit_group), Modifier.size(Metrics.icon)) }
                        }
                        if (expanded) {
                            val children = state.categories.filter { it.parentId == parent.id }
                            children.chunked(4).forEach { group ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = Space.xxs)) {
                                    group.forEach { category ->
                                        DisposableEffect(category.id) { onDispose { bounds.remove(category.id) } }
                                        CategoryTile(category, modifier = Modifier.weight(1f)
                                            .zIndex(if (dragging == category.id) 3f else 0f)
                                            .then(dragModifier(category))
                                            .graphicsLayer { if (dragging == category.id) { translationX = dragDelta.x; translationY = dragDelta.y; alpha = .6f } }) { editing = category.id }
                                    }
                                    repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            AddTextRow(R.string.add_subcategory, { newParent = parent.id; editing = 0 })

                        }
                    }
                }
            }
            item { AddRow(R.string.new_category, { newParent = null; editing = 0 }, Modifier.testTag("add_category")) }
        }
    }
    if (!state.loading && !state.failed) editing?.let { id ->
        CategoryEditor(state.categories.firstOrNull { it.id == id } ?: Category(name = "", parentId = newParent, color = 0xFF537DC1, icon = "book"),
            state.definitions.filter { it.categoryId == id }, busy, {
                val ids = state.categories.filter { it.id == id || it.parentId == id }.map { it.id }.toSet()
                stringResource(R.string.delete_category_warning, state.categories.count { it.parentId == id },
                    state.plans.count { it.categoryId in ids }, state.records.count { it.categoryId in ids }) +
                    if (state.timer?.categoryId in ids) stringResource(R.string.delete_category_timer_warning) else ""
            }, { editing = null },
            { category, definitions ->
                val loss = droppedPropertyValues(id, definitions, state)
                if (loss == 0) model.perform({ editing = null }) { model.repository.saveCategoryWithDefinitions(category, definitions, confirm = true) }
                else { pendingLoss = loss; pending = category to definitions }
            },
            { model.deleteCategory(id) { editing = null } })
    }
    pending?.let { (category, definitions) -> PanelDialog(stringResource(R.string.property_in_use), { pending = null }, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            TextButton(enabled = !busy, onClick = {
                pending = null
                model.perform({ editing = null }) { model.repository.saveCategoryWithDefinitions(category, definitions, confirm = true) }
            }, modifier = Modifier.testTag("confirm_property_loss")) { Text(stringResource(R.string.save)) }
        }
    }) { Text(pluralStringResource(R.plurals.property_used_warning, pendingLoss, pendingLoss), style = MaterialTheme.typography.bodyLarge) } }
    notice?.let { message -> NoticeDialog(message) { notice = null } }
}

@Composable private fun CategoryEditor(value: Category, definitions: List<PropertyDefinition>, busy: Boolean, deletionWarning: @Composable () -> String, dismiss: () -> Unit, save: (Category, List<PropertyDefinition>) -> Unit, delete: () -> Unit) {
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var color by rememberSaveable(value.id) { mutableLongStateOf(value.color ?: 0xFF537DC1) }
    var icon by rememberSaveable(value.id) { mutableStateOf(value.icon ?: "book") }
    var iconOpen by remember { mutableStateOf(false) }
    var properties by remember(value.id) { mutableStateOf(definitions) }
    var property by remember { mutableStateOf<PropertyDefinition?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    EditorSheet(if (value.id == 0L) R.string.new_category else R.string.edit_category, dismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.parentId != null) IconButton(onClick = { iconOpen = true }) {
                Icon(categoryIcons.firstOrNull { it.first == icon }?.second ?: AppIcons.Categories, stringResource(R.string.edit_icon), tint = androidx.compose.ui.graphics.Color(color))
            }
            Box(Modifier.weight(1f)) { FormField(R.string.name, name, { name = it }, "category_name") }
        }
        if (value.parentId != null) {
            ReorderableRows(properties, key = { _, definition -> definition.id }, pitch = Metrics.controlHeight + Space.xs, onMove = { from, to ->
                properties = properties.toMutableList().apply { add(to, removeAt(from)) }
            }) { _, definition ->
                OptionRow(definition.name, { property = definition }, description = stringResource(definition.type.label()) + " · " + stringResource(if (definition.required) R.string.required_property else R.string.optional_property))
            }
            AddRow(R.string.task_properties, { property = PropertyDefinition(id = (properties.minOfOrNull { it.id } ?: 0).coerceAtMost(0) - 1, categoryId = value.id, name = "", type = PropertyType.TEXT) })
        }
        EditorActions(busy, { save(value.copy(name = name, color = color, icon = icon), properties) }, if (value.id == 0L) null else ({ confirmDelete = true }))
    }
    if (iconOpen) IconEditor(icon, color, { key, tint -> icon = key; color = tint }, { iconOpen = false })
    property?.let { item -> PropertyEditor(item, busy, { property = null }, { updated ->
        properties = if (properties.any { it.id == updated.id }) properties.map { if (it.id == updated.id) updated else it } else properties + updated
        property = null
    }, if (properties.none { it.id == item.id }) null else ({ properties = properties.filter { it.id != item.id }; property = null })) }
    if (confirmDelete) PanelDialog(stringResource(R.string.delete_category_title), { confirmDelete = false }, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { confirmDelete = false; delete() }, enabled = !busy) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }) { Text(deletionWarning(), style = MaterialTheme.typography.bodyLarge) }
}
