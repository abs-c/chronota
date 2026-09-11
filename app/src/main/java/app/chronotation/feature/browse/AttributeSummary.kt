package app.chronotation.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.chronotation.R
import app.chronotation.data.entity.PropertyType
import app.chronotation.domain.*
import app.chronotation.feature.WorkspaceState
import app.chronotation.ui.components.*
import app.chronotation.ui.theme.Metrics
import app.chronotation.ui.theme.Space

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AttributeSummary(entry: BrowseEntry, state: WorkspaceState) {
    val values = if (entry.isPlan) state.planValues.filter { it.planId == entry.id }.associate { it.definitionId to it.value }
        else state.values.filter { it.recordId == entry.id }.associate { it.definitionId to it.value }
    val definitions = state.definitions.filter { it.categoryId == entry.categoryId && !values[it.id].isNullOrBlank() }
    val showTitle = LocalDisplayPreferences.current.preferCategoryName && entry.title.isNotBlank()
    if (definitions.isEmpty() && !showTitle) return
    Column(Modifier.fillMaxWidth().padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        if (showTitle) Text(stringResource(R.string.field_value, stringResource(R.string.title), entry.title), style = MaterialTheme.typography.bodyMedium)
        definitions.forEach { definition ->
            val value = values[definition.id].orEmpty()
            Column {
                Text(definition.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                when {
                    definition.type == PropertyType.RATING -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(stringResource(R.string.rating_value, ratingScore(value)), style = MaterialTheme.typography.bodyMedium)
                        RatingStars(ratingStars(ratingScore(value)), compact = true)
                    }
                    definition.type == PropertyType.SELECT || definition.type == PropertyType.MULTISELECT -> {
                        // One option per line left a lot of empty space inside a card; the options flow
                        // side by side and wrap as a group, so several short ones share a row.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            value.lines().filter { it.isNotBlank() }.forEach { option ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                    Box(Modifier.size(Metrics.dotSmall).background(definition.optionColor(definition.options.lines().indexOf(option)) ?: MaterialTheme.colorScheme.primary, CircleShape))
                                    Text(option, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    definition.type == PropertyType.BOOLEAN -> Text(stringResource(if (value == "true") R.string.boolean_yes else R.string.boolean_no), style = MaterialTheme.typography.bodyMedium)
                    else -> Text(value + definition.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
