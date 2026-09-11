package app.chronotation.feature.review

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import app.chronotation.R
import app.chronotation.data.entity.*
import app.chronotation.domain.resolveEditedInstant
import app.chronotation.ui.components.*
import java.time.*

@Composable
fun RecordEditor(value: Record, categories: List<Category>, plans: List<Plan>, busy: Boolean,
    dismiss: () -> Unit, save: (Record, Map<Long, String>) -> Unit, delete: () -> Unit,
    definitions: List<PropertyDefinition> = emptyList(), values: List<PropertyValue> = emptyList(),
    onDuplicate: ((Record, Map<Long, String>) -> Unit)? = null) {
    val zone = ZoneId.systemDefault()
    var title by rememberSaveable(value.id) { mutableStateOf(value.title) }
    var category by rememberSaveable(value.id) { mutableStateOf(value.categoryId) }

    var startDate by rememberSaveable(value.id) { mutableStateOf(value.startTime.atZone(zone).toLocalDate()) }
    var endDate by rememberSaveable(value.id) { mutableStateOf(value.endTime.atZone(zone).toLocalDate()) }
    var start by rememberSaveable(value.id) { mutableStateOf(value.startTime.atZone(zone).toLocalTime()) }
    var end by rememberSaveable(value.id) { mutableStateOf(value.endTime.atZone(zone).toLocalTime()) }
    var note by rememberSaveable(value.id) { mutableStateOf(value.note) }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var fields by rememberSaveable(value.id) { mutableStateOf<Map<Long, String>>(HashMap(values.filter { it.recordId == value.id }.associate { it.definitionId to it.value })) }
    val visibleDefinitions = definitions.filter { it.categoryId == category }
    val buildRecord: () -> Record? = {
        try {
            val record = value.copy(title = title, categoryId = category, sourcePlanId = null, note = note,
                startTime = resolveEditedInstant(value.startTime, startDate, start, zone), endTime = resolveEditedInstant(value.endTime, endDate, end, zone))
            error = null
            record
        } catch (_: IllegalArgumentException) { error = R.string.error_time; null }
    }
    EditorSheet(if (value.id == 0L) R.string.new_record else R.string.edit_record, dismiss, footer = {
        EditorActions(busy, onSave = { buildRecord()?.let { save(it, propertyInputs(visibleDefinitions, fields, value.id == 0L || category != value.categoryId)) } },
            onDelete = if (value.id != 0L) ({ confirmDelete = true }) else null,
            onDuplicate = if (onDuplicate == null) null else ({ buildRecord()?.let { onDuplicate(it, propertyInputs(visibleDefinitions, fields, true)) } }))
    }) {
        CategoryChoice(categories, category, { category = it })
        OptionalTitle(title, { title = it }, "record_title", value.id == 0L)
        PropertyFields(visibleDefinitions, propertyInputs(visibleDefinitions, fields, value.id == 0L || category != value.categoryId), { id, text -> fields = HashMap(fields).apply { put(id, text) } })
        OptionGroup {
            DateTimeField(R.string.start_at, startDate, start) { d, t -> startDate = d; start = t }
            AppDivider()
            DateTimeField(R.string.end_at, endDate, end) { d, t -> endDate = d; end = t }
        }

        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

    }
    if (confirmDelete) ConfirmAction(R.string.delete, R.string.confirm_delete, { confirmDelete = false }, { confirmDelete = false; delete() })
}
