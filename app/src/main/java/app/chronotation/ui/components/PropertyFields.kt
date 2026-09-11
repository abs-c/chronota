package app.chronotation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.*
import app.chronotation.R
import app.chronotation.data.entity.*
import app.chronotation.ui.theme.*
import app.chronotation.domain.*

fun PropertyDefinition.optionColor(index: Int): Color? = optionColors.lines().getOrNull(index)?.toLongOrNull(16)?.let { Color(it or 0xFF000000) }
fun propertyInputs(definitions: List<PropertyDefinition>, fields: Map<Long, String>, defaults: Boolean = true): Map<Long, String> =
    definitions.associate { it.id to (fields[it.id] ?: if (defaults) it.defaultValue else "") }

@Composable fun PropertyOptions(definition: PropertyDefinition, text: String, optional: Boolean, dismiss: () -> Unit, change: (String) -> Unit) {
    SelectionDialog(definition.name, dismiss, clear = if (optional) ({ change(""); dismiss() }) else null) {
        definition.options.lines().withIndex().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                row.forEach { (index, key) ->
                    SelectionRow(key, key in text.lines(), {
                        if (definition.type == PropertyType.MULTISELECT) {
                            val selected = text.lines().filter { it.isNotBlank() }.toMutableSet()
                            if (!selected.add(key)) selected.remove(key)
                            change(selected.joinToString("\n"))
                        } else { change(key); dismiss() }
                    }, Modifier.weight(1f), multi = definition.type == PropertyType.MULTISELECT,
                        color = definition.optionColor(index) ?: MaterialTheme.colorScheme.primary)
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PropertyFields(definitions: List<PropertyDefinition>, fields: Map<Long, String>, change: (Long, String) -> Unit, optional: Boolean = false, enabled: Boolean = true) {
    definitions.forEach { definition ->
        val text = fields[definition.id].orEmpty()
        val label = definition.name + if (definition.required && !optional) " *" else ""
        when (definition.type) {
            PropertyType.RATING -> {
                var open by remember { mutableStateOf(false) }
                OptionRow(label, { open = true }, enabled = enabled, valueContent = if (text.isBlank()) null else ({
                    RatingStars(ratingStars(ratingScore(text)), compact = true)
                }))
                if (open) SelectionDialog(label, { open = false }, clear = if (optional || !definition.required) ({ change(definition.id, ""); open = false }) else null) {
                    val score = ratingScore(text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RatingStars(ratingStars(score))
                    }
                    Text(stringResource(R.string.rating_value, score), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.titleLarge)
                    Slider(score.toFloat(), { change(definition.id, ratingValue(it.roundToInt())) }, valueRange = 0f..100f, steps = 99,
                        onValueChangeFinished = { if (text.isBlank()) change(definition.id, ratingValue(0)) },
                        thumb = { Box(Modifier.size(width = Space.xxs, height = Metrics.check).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
                        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
                }
            }
            PropertyType.BOOLEAN -> Row(Modifier.fillMaxWidth().height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Space.md), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(AppIcons.Check, null, Modifier.size(Metrics.icon))
                Text(label, Modifier.weight(1f).padding(start = Space.xs), style = MaterialTheme.typography.bodyLarge)
                TriStateCheckbox(state = when (text) { "true" -> androidx.compose.ui.state.ToggleableState.On; "false" -> androidx.compose.ui.state.ToggleableState.Off; else -> androidx.compose.ui.state.ToggleableState.Indeterminate },
                    onClick = { change(definition.id, when (text) { "true" -> "false"; "false" -> if (optional || !definition.required) "" else "true"; else -> "true" }) }, enabled = enabled)
            }
            PropertyType.SELECT, PropertyType.MULTISELECT -> {
                var open by remember { mutableStateOf(false) }
                val choices = definition.options.lines()
                val selected = choices.withIndex().filter { it.value in text.lines() }
                OptionRow(label, { open = true }, value = selected.joinToString("、") { it.value }.ifBlank { null },
                    valueColor = selected.firstOrNull()?.let { definition.optionColor(it.index) }, enabled = enabled)
                if (open) PropertyOptions(definition, text, optional || !definition.required, { open = false }) { change(definition.id, it) }
            }
            PropertyType.NUMBER -> Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                // Typed attributes keep their name visible after a value is entered.
                Text(label, Modifier.padding(start = Space.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth()) {
                    PlainInput("", text, { change(definition.id, it) }, enabled = enabled, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    if (definition.unit.isNotBlank()) Text(definition.unit, Modifier.align(Alignment.CenterEnd).padding(end = Space.md), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(label, Modifier.padding(start = Space.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlainInput("", text, { change(definition.id, it) }, enabled = enabled, singleLine = definition.type != PropertyType.TEXT || !definition.multiline,
                    keyboardType = if (definition.type in listOf(PropertyType.NUMBER, PropertyType.RATING)) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text)
            }
        }
    }
}

@Composable fun RatingStars(rating: Float, compact: Boolean = false) {
    val description = stringResource(R.string.rating_stars, rating)
    Row(Modifier.semantics { contentDescription = description }) {
        repeat(5) { index -> RatingStar((rating - index).coerceIn(0f, 1f), compact) }
    }
}
@Composable private fun RatingStar(fill: Float, compact: Boolean) {
    val color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(if (compact) Metrics.starCompact else Metrics.starLarge).padding(if (compact) Metrics.hairline else Space.xxs)) {
        val vertices = List(10) { i ->
            val angle = PI * i / 5 - PI / 2
            val radius = size.minDimension * if (i % 2 == 0) .47f else .23f
            androidx.compose.ui.geometry.Offset(size.width / 2 + (cos(angle) * radius).toFloat(), size.height / 2 + (sin(angle) * radius).toFloat())
        }
        val path = Path().apply {
            repeat(10) { i ->
                val point = vertices[i]
                val start = point + (vertices[(i + 9) % 10] - point) * .14f
                val end = point + (vertices[(i + 1) % 10] - point) * .14f
                if (i == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
                quadraticTo(point.x, point.y, end.x, end.y)
            }
            close()
        }
        drawPath(path, color.copy(alpha = .15f))
        clipRect(right = size.width * fill) { drawPath(path, color) }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = Metrics.outline.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
