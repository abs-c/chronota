package app.chronotation.data.repository

import androidx.room.withTransaction
import app.chronotation.data.db.AppDatabase
import app.chronotation.data.entity.*
import app.chronotation.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RuleViolation(val reason: String) : IllegalArgumentException(reason)

class TimeRepository(val database: AppDatabase, val clock: Clock = Clock.systemUTC()) {
    val dao = database.dao()
    suspend fun installDefaultCategories() = database.withTransaction {
        suspend fun group(name: String): Long = dao.allCategories().firstOrNull { it.parentId == null && it.name == name }?.id
            ?: saveCategory(Category(name = name))
        suspend fun child(parent: Long, name: String, icon: String, color: Long): Long =
            dao.allCategories().firstOrNull { it.parentId == parent && it.name == name }?.id
                ?: saveCategory(Category(parentId = parent, name = name, icon = icon, color = color))
        child(group("Sonata"), "学习", "book-open", 0xFF537DC1)
        val cinema = child(group("Nocturne"), "观影", "clapperboard", 0xFF9876B8)
        child(group("Waltz"), "睡眠", "moon", 0xFF658E9C)
        listOf(
            PropertyDefinition(categoryId = cinema, name = "类型", type = PropertyType.SELECT, required = true,
                options = "电影\n电视剧\n动画\n纪录片\n直播", optionColors = "9876B8\n537DC1\nBE8C4D\n588E76\nB85F7A"),
            PropertyDefinition(categoryId = cinema, name = "评分", type = PropertyType.RATING),
            PropertyDefinition(categoryId = cinema, name = "观后感", type = PropertyType.TEXT, multiline = true),
        ).forEachIndexed { index, definition ->
            if (dao.allDefinitions().none { it.categoryId == cinema && it.name == definition.name }) saveDefinition(definition.copy(sortOrder = index))
        }
    }
    suspend fun saveCategory(value: Category): Long = database.withTransaction {
        if (value.name.isBlank()) throw RuleViolation("required")
        if (value.parentId == value.id && value.id != 0L) throw RuleViolation("category_depth")
        value.parentId?.let { parent ->
            if (dao.category(parent)?.parentId != null || dao.category(parent) == null) throw RuleViolation("category_depth")
            if (value.color == null || value.icon.isNullOrBlank()) throw RuleViolation("required")
        }
        val old = value.id.takeIf { it != 0L }?.let { dao.category(it) }
        if (old != null && (old.parentId == null) != (value.parentId == null) && dao.categoryUsage(value.id) > 0) throw RuleViolation("category_used")
        val clean = if (value.parentId == null) value.copy(name = value.name.trim(), color = null, icon = null) else value.copy(name = value.name.trim())
        val inserted = dao.putCategory(if (value.id == 0L) clean.copy(sortOrder = (dao.allCategories().filter { it.parentId == value.parentId }.maxOfOrNull { it.sortOrder } ?: -1) + 1) else clean)
        if (value.id == 0L) inserted else value.id
    }

    suspend fun deleteCategory(id: Long) = database.withTransaction {
        val categories = dao.allCategories()
        val children = categories.filter { it.parentId == id }
        val ids = children.map { it.id }.toSet() + id
        if (dao.currentTimer()?.categoryId in ids) {
            dao.clearSlices()
            dao.clearTimer()
        }
        dao.allRecords().filter { it.categoryId in ids }.forEach { dao.deleteRecord(it.id) }
        dao.allPlans().filter { it.categoryId in ids }.forEach { dao.deletePlan(it.id) }
        dao.allDefinitions().filter { it.categoryId in ids }.forEach { dao.deleteDefinition(it.id) }
        children.forEach { dao.deleteCategory(it.id) }
        dao.deleteCategory(id)
    }

    /** [confirm] accepts that attributes dropped in this edit lose the values stored for them. */
    suspend fun saveCategoryWithDefinitions(value: Category, definitions: List<PropertyDefinition>, confirm: Boolean = false): Long = database.withTransaction {
        val id = saveCategory(value)
        val old = dao.allDefinitions().filter { it.categoryId == id }
        old.filter { definition -> definitions.none { it.id == definition.id } }.forEach { deleteDefinition(it.id, confirm) }
        definitions.forEachIndexed { index, definition ->
            saveDefinition(definition.copy(id = definition.id.coerceAtLeast(0), categoryId = id, sortOrder = index), confirm)
        }
        id
    }

    suspend fun moveCategory(id: Long, targetId: Long) = database.withTransaction {
        if (id == targetId) return@withTransaction
        val categories = dao.allCategories().sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.id })
        val source = categories.firstOrNull { it.id == id } ?: throw RuleViolation("missing")
        val target = categories.firstOrNull { it.id == targetId } ?: throw RuleViolation("missing")
        if (source.parentId == null && target.parentId != null) throw RuleViolation("category_depth")
        val parent = if (source.parentId == null) null else target.parentId ?: target.id
        val siblings = categories.filter { it.parentId == parent && it.id != id }.toMutableList()
        val position = (if (source.parentId == parent) categories.filter { it.parentId == parent }.indexOfFirst { it.id == targetId }
            else siblings.indexOfFirst { it.id == targetId }).takeIf { it >= 0 }?.coerceAtMost(siblings.size) ?: siblings.size
        siblings.add(position, source)
        siblings.forEachIndexed { index, category -> dao.placeCategory(category.id, index, parent) }
    }

    internal suspend fun validateCategory(id: Long?) {
        if (id != null && dao.category(id)?.parentId == null) throw RuleViolation("secondary_required")
    }

    suspend fun savePlan(value: Plan, policy: HistoricalPlanPolicy, reminders: List<Reminder>? = null, properties: Map<Long, String>? = null): Long = database.withTransaction {
        val old = dao.plan(value.id)
        if (old != null && !old.canEdit(clock.instant(), policy)) throw RuleViolation("history_locked")
        validateCategory(value.categoryId)

        if ((value.startTime == null) != (value.endTime == null) || (value.startTime != null && value.scheduledDate == null)) throw RuleViolation("invalid_time")
        if (value.endDayOffset !in 0..3660 || (value.estimatedDuration != null && value.estimatedDuration <= 0)) throw RuleViolation("invalid_time")
        try { value.timeSpan() } catch (_: IllegalArgumentException) { throw RuleViolation("invalid_time") }
        if (value.recurrenceInterval !in 1..999) throw RuleViolation("invalid_recurrence")
        if (value.recurrence != Recurrence.NONE && (value.scheduledDate == null ||
                (value.recurrenceUntil != null && value.recurrenceUntil < value.scheduledDate))) throw RuleViolation("invalid_recurrence")
        val normalized = if (value.recurrence == Recurrence.NONE) value.copy(recurrenceInterval = 1, recurrenceUntil = null) else value
        // A plan's clock times are kept to the minute, like every other stored moment.
        val cleaned = normalized.copy(title = normalized.title.trim(), createdAt = old?.createdAt ?: clock.instant(), updatedAt = clock.instant(),
            startTime = normalized.startTime?.wholeMinute(), endTime = normalized.endTime?.wholeMinute())
        val inserted = dao.putPlan(cleaned)
        val id = if (value.id == 0L) inserted else value.id
        if (properties != null) {
            val validated = validateProperties(value.categoryId, properties, required = false)
            dao.clearPlanValues(id)
            validated.forEach { (definition, text) -> dao.putPlanValue(PlanPropertyValue(id, definition, text)) }
        }
        if (reminders != null) {
            dao.clearReminders(id)
            reminders.distinctBy { it.anchor to it.minutesBefore }.forEach {
                if (it.minutesBefore !in 0..525600 || it.trigger(cleaned, clock.instant()) == null) throw RuleViolation("invalid_reminder")
                dao.putReminder(it.copy(id = 0, planId = id, deliveredAt = null))
            }
        }
        id
    }

    suspend fun schedulePlan(id: Long, start: Instant, end: Instant, policy: HistoricalPlanPolicy) {
        val plan = dao.plan(id) ?: throw RuleViolation("missing")
        if (plan.timeSpan() != null && plan.temporalState(clock.instant()) == PlanTemporalState.ACTIVE) throw RuleViolation("invalid_time")
        val zone = java.time.ZoneId.of(plan.zoneId)
        val localStart = start.atZone(zone)
        val localEnd = end.atZone(zone)
        val updated = plan.copy(allDay = false, scheduledDate = localStart.toLocalDate(), startTime = localStart.toLocalTime(), endTime = localEnd.toLocalTime(), endDayOffset = java.time.temporal.ChronoUnit.DAYS.between(localStart.toLocalDate(), localEnd.toLocalDate()).toInt())
        savePlan(updated, policy, dao.allReminders().filter { it.planId == id })
    }

    suspend fun adjustRemaining(id: Long, end: Instant, policy: HistoricalPlanPolicy) {
        val plan = dao.plan(id) ?: throw RuleViolation("missing")
        if (plan.temporalState(clock.instant()) != PlanTemporalState.ACTIVE || end <= clock.instant()) throw RuleViolation("invalid_time")
        val date = plan.scheduledDate ?: throw RuleViolation("invalid_time")
        val localEnd = end.atZone(java.time.ZoneId.of(plan.zoneId))
        savePlan(plan.copy(endTime = localEnd.toLocalTime(), endDayOffset = java.time.temporal.ChronoUnit.DAYS.between(date, localEnd.toLocalDate()).toInt()), policy,
            dao.allReminders().filter { it.planId == id })
    }

    suspend fun deletePlan(id: Long, policy: HistoricalPlanPolicy) = database.withTransaction {
        if (dao.plan(id)?.canEdit(clock.instant(), policy) == false) throw RuleViolation("history_locked")
        dao.deletePlan(id)
    }

    suspend fun saveRecord(value: Record, properties: Map<Long, String>? = null, enforceRequired: Boolean = true): Long = database.withTransaction {
        validateCategory(value.categoryId)

        // Checked before rounding so a genuinely backwards range is still refused.
        if (value.endTime < value.startTime || value.endTime > clock.instant()) throw RuleViolation("record_future")

        val old = dao.record(value.id)
        if (old != null && old.categoryId != value.categoryId && dao.recordValues(value.id).isNotEmpty()) throw RuleViolation("property_category")
        // A timer stops on a second, but the moment it records is kept to the minute it fell in.
        val cleaned = value.copy(sourcePlanId = null, title = value.title.trim(), createdBy = old?.createdBy ?: value.createdBy, createdAt = old?.createdAt ?: clock.instant(), timerToken = old?.timerToken ?: value.timerToken, timerPart = old?.timerPart ?: value.timerPart,
            startTime = value.startTime.wholeMinute(), endTime = value.endTime.wholeMinute())
        val inserted = dao.putRecord(cleaned)
        val id = if (value.id == 0L) inserted else value.id
        val inputs = properties ?: dao.recordValues(id).associate { it.definitionId to it.value }
        val validated = validateProperties(value.categoryId, inputs, enforceRequired)
        if (properties != null) {
            dao.clearValues(id)
            validated.forEach { (definition, text) -> dao.putValue(PropertyValue(id, definition, text)) }
        }
        id
    }

    internal suspend fun validateProperties(category: Long?, properties: Map<Long, String>, required: Boolean): Map<Long, String> {
        val definitions = dao.allDefinitions().associateBy { it.id }
        if (required && definitions.values.any { it.categoryId == category && it.required && properties[it.id].isNullOrBlank() }) throw RuleViolation("property_required")
        return properties.filterValues { it.isNotBlank() }.onEach { (id, value) ->
            val definition = definitions[id] ?: throw RuleViolation("missing")
            if (definition.categoryId != category || !validProperty(definition, value)) throw RuleViolation("property_value")
        }
    }

    /**
     * Saves a definition. An edit that would drop values already stored for it is refused until the
     * caller passes [confirm], because only the user can accept that loss; a confirmed save applies
     * the edit and removes exactly the values it no longer accepts.
     */
    suspend fun saveDefinition(value: PropertyDefinition, confirm: Boolean = false): Long = database.withTransaction {
        validateCategory(value.categoryId)
        if (value.name.isBlank()) throw RuleViolation("required")
        val cleaned = value.copy(name = value.name.trim(), options = value.options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
        if (cleaned.optionColors.isNotBlank() && (cleaned.optionColors.lines().size != cleaned.options.lines().size || cleaned.optionColors.lines().any { it.length != 6 || it.toLongOrNull(16) == null })) throw RuleViolation("property_value")
        if (cleaned.type in listOf(PropertyType.SELECT, PropertyType.MULTISELECT) && cleaned.options.isBlank()) throw RuleViolation("property_value")
        if (cleaned.defaultValue.isNotEmpty() && !validProperty(cleaned, cleaned.defaultValue)) throw RuleViolation("property_value")
        val old = dao.allDefinitions().firstOrNull { it.id == value.id }
        if (old != null) {
            val dropped = droppedValues(old, cleaned, storedValues(old.id))
            if (dropped.isNotEmpty()) {
                if (!confirm) throw RuleViolation("property_used")
                dao.deleteDefinitionValues(old.id, dropped)
                dao.deleteDefinitionPlanValues(old.id, dropped)
                pruneTimerValue(old.id) { validProperty(cleaned, it) }
            }
        }
        if (dao.allDefinitions().any { it.id != value.id && it.categoryId == value.categoryId && it.name.equals(cleaned.name, ignoreCase = true) }) throw RuleViolation("property_duplicate")
        val inserted = dao.putDefinition(cleaned)
        if (value.id == 0L) inserted else value.id
    }

    /** Deletes a definition; [confirm] accepts losing the values stored for it. */
    suspend fun deleteDefinition(id: Long, confirm: Boolean = false) = database.withTransaction {
        val old = dao.allDefinitions().firstOrNull { it.id == id }
        if (old != null) {
            if (storedValues(id).isNotEmpty() && !confirm) throw RuleViolation("property_used")
            dao.dropDefinitionValues(id)
            dao.dropDefinitionPlanValues(id)
            pruneTimerValue(id) { false }
        }
        dao.deleteDefinition(id)
    }

    /** Every value saved for [id], in records and in plans. */
    private suspend fun storedValues(id: Long): List<String> =
        dao.allValues().filter { it.definitionId == id }.map { it.value } +
            dao.allPlanValues().filter { it.definitionId == id }.map { it.value }

    /** Drops the value a running timer is still holding for [id] when [keep] rejects it. */
    private suspend fun pruneTimerValue(id: Long, keep: (String) -> Boolean) {
        val session = dao.currentTimer() ?: return
        if (session.propertiesJson.isBlank()) return
        val json = org.json.JSONObject(session.propertiesJson)
        val key = id.toString()
        if (!json.has(key) || keep(json.getString(key))) return
        json.remove(key)
        dao.putTimer(session.copy(propertiesJson = json.toString()))
    }

    suspend fun deleteRecord(id: Long) { dao.deleteRecord(id) }

    suspend fun saveGoal(value: Goal): Long = database.withTransaction {
        if (value.name.isBlank() || value.target <= 0) throw RuleViolation("required")
        value.propertyDefinitionId?.let { if (dao.allDefinitions().none { definition -> definition.id == it }) throw RuleViolation("property_value") }
        dao.putGoal(value)
    }

    suspend fun deleteGoal(id: Long) { dao.deleteGoal(id) }


    /**
     * Rolls every running goal forward to the instance that contains [now].
     *
     * An instance that ran out becomes a goal of its own (marked [Goal.expiredAt]) and the successor
     * is inserted as a new row, exactly as if the user had created it: nothing links the two, so
     * deleting either of them leaves the other one alone.
     */
    suspend fun rolloverGoals(now: Instant, zone: ZoneId) = database.withTransaction {
        val goals = dao.allGoals().filter { it.expiredAt == null }
        if (goals.isEmpty()) return@withTransaction
        val definitions = dao.allDefinitions()
        val values = dao.allValues()
        val records = dao.allRecords()
        goals.forEach { goal ->
            val rollover = goalRollover(goal, records, definitions, values, now, zone)
            if (!rollover.moved) return@forEach
            rollover.instances.forEachIndexed { index, instance ->
                // This row is the first instance of the chain; any later one came from repeating.
                if (index == 0) dao.putGoal(goal.copy(expiredAt = instance.end))
                else dao.putGoal(goal.copy(id = 0, startAt = instance.start, expiredAt = instance.end))
            }
            if (!rollover.retired) dao.putGoal(goal.copy(id = 0, startAt = rollover.startAt, createdAt = now))
        }
    }
}

