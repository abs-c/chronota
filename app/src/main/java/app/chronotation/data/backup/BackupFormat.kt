package app.chronotation.data.backup

import app.chronotation.data.entity.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup format: one JSON document holding every table the user's data lives in.
 *
 * The header names the app and the format so a file from somewhere else, or from a newer build, is
 * refused rather than half-read. [SCHEMA] records the Room version at export time for diagnostics; the
 * mapping below is by field name, so a restore into a different schema version still works.
 *
 * The running timer is deliberately left out: it is a session in progress, not stored data, and a
 * restore starts with a clean slate.
 */
object BackupFormat {
    const val APP = "chronota"
    const val FORMAT = 1

    /** Everything the user has recorded, in the order a restore has to insert it. */
    data class Data(
        val categories: List<Category>,
        val plans: List<Plan>,
        val records: List<Record>,
        val reminders: List<Reminder>,
        val goals: List<Goal>,
        val definitions: List<PropertyDefinition>,
        val values: List<PropertyValue>,
        val planValues: List<PlanPropertyValue>,
    ) {
        val itemCount: Int get() = categories.size + plans.size + records.size + reminders.size + goals.size
    }

    /** Thrown when the text is not one of our files, or is one we are too old to read. */
    class Unreadable(val reason: String) : Exception(reason)

    fun write(data: Data, schema: Int, exportedAt: Instant): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("format", FORMAT)
        root.put("schema", schema)
        root.put("exportedAt", exportedAt.toEpochMilli())
        root.put("categories", data.categories.map(::category).let(::array))
        root.put("plans", data.plans.map(::plan).let(::array))
        root.put("records", data.records.map(::record).let(::array))
        root.put("reminders", data.reminders.map(::reminder).let(::array))
        root.put("goals", data.goals.map(::goal).let(::array))
        root.put("propertyDefinitions", data.definitions.map(::definition).let(::array))
        root.put("propertyValues", data.values.map(::value).let(::array))
        root.put("planPropertyValues", data.planValues.map(::planValue).let(::array))
        return root.toString(2)
    }

    fun read(text: String): Data = try {
        val root = JSONObject(text)
        if (root.optString("app") != APP) throw Unreadable("foreign")
        val format = root.optInt("format", 0)
        if (format !in 1..FORMAT) throw Unreadable("format")
        Data(
            categories = root.list("categories", ::category),
            plans = root.list("plans", ::plan),
            records = root.list("records", ::record),
            reminders = root.list("reminders", ::reminder),
            goals = root.list("goals", ::goal),
            definitions = root.list("propertyDefinitions", ::definition),
            values = root.list("propertyValues", ::value),
            planValues = root.list("planPropertyValues", ::planValue),
        )
    } catch (error: Unreadable) {
        throw error
    } catch (error: Exception) {
        throw Unreadable("malformed")
    }

    // ---- writing ---------------------------------------------------------------------------------

    private fun array(items: List<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }

    private fun <T> JSONObject.list(key: String, read: (JSONObject) -> T): List<T> {
        val items = optJSONArray(key) ?: return emptyList()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it) }.map(read)
    }

    private fun category(value: Category) = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("parentId", value.parentId)
        put("color", value.color); put("icon", value.icon); put("sortOrder", value.sortOrder)
    }

    private fun plan(value: Plan) = JSONObject().apply {
        put("id", value.id); put("title", value.title); put("categoryId", value.categoryId)
        put("scheduledDate", value.scheduledDate?.toEpochDay()); put("startTime", value.startTime?.toSecondOfDay())
        put("endTime", value.endTime?.toSecondOfDay()); put("endDayOffset", value.endDayOffset)
        put("zoneId", value.zoneId); put("estimatedDuration", value.estimatedDuration)
        put("deadline", value.deadline?.toEpochMilli()); put("note", value.note)
        put("createdAt", value.createdAt.toEpochMilli()); put("updatedAt", value.updatedAt.toEpochMilli())
        put("allDay", value.allDay); put("recurrence", value.recurrence.name)
        put("recurrenceInterval", value.recurrenceInterval); put("recurrenceUntil", value.recurrenceUntil?.toEpochDay())
        put("skipDates", value.skipDates)
    }

    private fun record(value: Record) = JSONObject().apply {
        put("id", value.id); put("title", value.title); put("categoryId", value.categoryId)
        put("startTime", value.startTime.toEpochMilli()); put("endTime", value.endTime.toEpochMilli())
        put("sourcePlanId", value.sourcePlanId); put("note", value.note); put("createdBy", value.createdBy.name)
        put("createdAt", value.createdAt.toEpochMilli()); put("timerToken", value.timerToken); put("timerPart", value.timerPart)
    }

    private fun reminder(value: Reminder) = JSONObject().apply {
        put("id", value.id); put("planId", value.planId); put("anchor", value.anchor.name)
        put("minutesBefore", value.minutesBefore); put("deliveredAt", value.deliveredAt?.toEpochMilli())
    }

    private fun goal(value: Goal) = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("categoryId", value.categoryId)
        put("titleFilter", value.titleFilter); put("propertyDefinitionId", value.propertyDefinitionId)
        put("propertyValue", value.propertyFilter); put("metric", value.metric.name)
        put("direction", value.direction.name); put("target", value.target); put("period", value.periodUnit.name)
        put("periodValue", value.periodValue); put("repeat", value.repeat.name); put("enabled", value.enabled)
        put("startAt", value.startAt.toEpochMilli()); put("createdAt", value.createdAt.toEpochMilli())
        put("sortOrder", value.sortOrder); put("expiredAt", value.expiredAt?.toEpochMilli())
    }

    private fun definition(value: PropertyDefinition) = JSONObject().apply {
        put("id", value.id); put("categoryId", value.categoryId); put("name", value.name)
        put("type", value.type.name); put("options", value.options); put("sortOrder", value.sortOrder)
        put("required", value.required); put("multiline", value.multiline)
        put("optionColors", value.optionColors); put("unit", value.unit); put("defaultValue", value.defaultValue)
    }

    private fun value(value: PropertyValue) = JSONObject().apply {
        put("recordId", value.recordId); put("definitionId", value.definitionId); put("value", value.value)
    }

    private fun planValue(value: PlanPropertyValue) = JSONObject().apply {
        put("planId", value.planId); put("definitionId", value.definitionId); put("value", value.value)
    }

    // ---- reading ---------------------------------------------------------------------------------

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun <T : Enum<T>> JSONObject.enumOf(key: String, values: Array<T>, fallback: T): T =
        values.firstOrNull { it.name == optString(key) } ?: fallback

    private fun category(value: JSONObject) = Category(
        id = value.getLong("id"), name = value.optString("name"), parentId = value.longOrNull("parentId"),
        color = value.longOrNull("color"), icon = if (value.isNull("icon")) null else value.optString("icon"),
        sortOrder = value.optInt("sortOrder"),
    )

    private fun plan(value: JSONObject) = Plan(
        id = value.getLong("id"), title = value.optString("title"), categoryId = value.longOrNull("categoryId"),
        scheduledDate = value.longOrNull("scheduledDate")?.let(LocalDate::ofEpochDay),
        startTime = value.intOrNull("startTime")?.let { LocalTime.ofSecondOfDay(it.toLong()) },
        endTime = value.intOrNull("endTime")?.let { LocalTime.ofSecondOfDay(it.toLong()) },
        endDayOffset = value.optInt("endDayOffset"), zoneId = value.optString("zoneId"),
        estimatedDuration = value.longOrNull("estimatedDuration"), deadline = value.longOrNull("deadline")?.let(Instant::ofEpochMilli),
        note = value.optString("note"), createdAt = Instant.ofEpochMilli(value.optLong("createdAt")),
        updatedAt = Instant.ofEpochMilli(value.optLong("updatedAt", value.optLong("createdAt"))), allDay = value.optBoolean("allDay"),
        recurrence = value.enumOf("recurrence", Recurrence.entries.toTypedArray(), Recurrence.NONE),
        recurrenceInterval = value.optInt("recurrenceInterval", 1),
        recurrenceUntil = value.longOrNull("recurrenceUntil")?.let(LocalDate::ofEpochDay),
        skipDates = value.optString("skipDates"),
    )

    private fun record(value: JSONObject) = Record(
        id = value.getLong("id"), title = value.optString("title"), categoryId = value.longOrNull("categoryId"),
        startTime = Instant.ofEpochMilli(value.getLong("startTime")), endTime = Instant.ofEpochMilli(value.getLong("endTime")),
        sourcePlanId = value.longOrNull("sourcePlanId"), note = value.optString("note"),
        createdBy = value.enumOf("createdBy", RecordSource.entries.toTypedArray(), RecordSource.MANUAL),
        createdAt = Instant.ofEpochMilli(value.optLong("createdAt")),
        timerToken = if (value.isNull("timerToken")) null else value.optString("timerToken"),
        timerPart = value.intOrNull("timerPart"),
    )

    private fun reminder(value: JSONObject) = Reminder(
        id = value.getLong("id"), planId = value.getLong("planId"),
        anchor = value.enumOf("anchor", ReminderAnchor.entries.toTypedArray(), ReminderAnchor.START),
        minutesBefore = value.optInt("minutesBefore", 15),
        deliveredAt = value.longOrNull("deliveredAt")?.let(Instant::ofEpochMilli),
    )

    private fun goal(value: JSONObject) = Goal(
        id = value.getLong("id"), name = value.optString("name"), categoryId = value.longOrNull("categoryId"),
        titleFilter = value.optString("titleFilter"), propertyDefinitionId = value.longOrNull("propertyDefinitionId"),
        propertyFilter = value.optString("propertyValue"), metric = value.enumOf("metric", GoalMetric.entries.toTypedArray(), GoalMetric.TIME),
        direction = value.enumOf("direction", GoalDirection.entries.toTypedArray(), GoalDirection.AT_LEAST),
        target = value.optLong("target"), periodUnit = value.enumOf("period", GoalUnit.entries.toTypedArray(), GoalUnit.WEEK),
        periodValue = value.optInt("periodValue", 1), repeat = value.enumOf("repeat", GoalRepeat.entries.toTypedArray(), GoalRepeat.CYCLE),
        enabled = value.optBoolean("enabled", true), startAt = Instant.ofEpochMilli(value.optLong("startAt")),
        createdAt = Instant.ofEpochMilli(value.optLong("createdAt")), sortOrder = value.optInt("sortOrder"),
        expiredAt = value.longOrNull("expiredAt")?.let(Instant::ofEpochMilli),
    )

    private fun definition(value: JSONObject) = PropertyDefinition(
        id = value.getLong("id"), categoryId = value.getLong("categoryId"), name = value.optString("name"),
        type = value.enumOf("type", PropertyType.entries.toTypedArray(), PropertyType.TEXT),
        options = value.optString("options"), sortOrder = value.optInt("sortOrder"),
        required = value.optBoolean("required"), multiline = value.optBoolean("multiline"),
        optionColors = value.optString("optionColors"), unit = value.optString("unit"),
        defaultValue = value.optString("defaultValue"),
    )

    private fun value(value: JSONObject) = PropertyValue(
        recordId = value.getLong("recordId"), definitionId = value.getLong("definitionId"), value = value.optString("value"),
    )

    private fun planValue(value: JSONObject) = PlanPropertyValue(
        planId = value.getLong("planId"), definitionId = value.getLong("definitionId"), value = value.optString("value"),
    )
}
