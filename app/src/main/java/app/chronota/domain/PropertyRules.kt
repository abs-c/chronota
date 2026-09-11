package app.chronota.domain

import app.chronota.data.entity.PropertyDefinition
import app.chronota.data.entity.PropertyType

/** Whether [value] is a legal entry for [definition]. */
fun validProperty(definition: PropertyDefinition, value: String): Boolean = when (definition.type) {
    PropertyType.TEXT -> true
    PropertyType.NUMBER -> value.toDoubleOrNull()?.isFinite() == true
    PropertyType.BOOLEAN -> value == "true" || value == "false"
    PropertyType.SELECT -> value in definition.options.lines()
    PropertyType.MULTISELECT -> value.lines().all { it in definition.options.lines() }
    PropertyType.RATING -> validRating(value)
}

/**
 * Saved [stored] values that turning [old] into [next] would drop, or that deleting it would drop
 * when [next] is null.
 *
 * Only edits that change what the attribute accepts lose data: removing a choice, switching the
 * type, deleting the attribute. Adding choices, recolouring them, renaming, reordering or toggling
 * "required" keeps every stored value, so those edits save without any warning.
 */
fun droppedValues(old: PropertyDefinition, next: PropertyDefinition?, stored: List<String>): List<String> =
    stored.filterNot { next != null && validProperty(next, it) }
