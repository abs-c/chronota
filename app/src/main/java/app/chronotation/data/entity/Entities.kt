package app.chronotation.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Entity(tableName = "categories", foreignKeys = [ForeignKey(Category::class, ["id"], ["parentId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("parentId")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val color: Long? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
)

/** Recurrence of a plan. The anchor is the plan's own scheduled date. */
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "plans", foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId"), Index("scheduledDate")])
data class Plan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long? = null,
    val scheduledDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val endDayOffset: Int = 0,
    val zoneId: String = ZoneId.systemDefault().id,
    val estimatedDuration: Long? = null,
    val deadline: Instant? = null,
    val note: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    @ColumnInfo(defaultValue = "0") val allDay: Boolean = false,
    @ColumnInfo(defaultValue = "'NONE'") val recurrence: Recurrence = Recurrence.NONE,
    @ColumnInfo(defaultValue = "1") val recurrenceInterval: Int = 1,
    val recurrenceUntil: LocalDate? = null,
    @ColumnInfo(defaultValue = "''") val skipDates: String = "",
)

enum class RecordSource { MANUAL, TIMER, POMODORO }

@Entity(tableName = "records", foreignKeys = [
    ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(Plan::class, ["id"], ["sourcePlanId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index("categoryId"), Index("sourcePlanId"), Index("startTime"), Index(value = ["timerToken", "timerPart"], unique = true)])
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long? = null,
    val startTime: Instant,
    val endTime: Instant,
    val sourcePlanId: Long? = null,
    val note: String = "",
    val createdBy: RecordSource = RecordSource.MANUAL,
    val createdAt: Instant = Instant.now(),
    val timerToken: String? = null,
    val timerPart: Int? = null,
)

enum class TimerMode { TIMER, POMODORO }
enum class TimerPhase { WORK, BREAK }

@Entity(tableName = "timer_sessions", foreignKeys = [
    ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(Plan::class, ["id"], ["sourcePlanId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index("categoryId"), Index("sourcePlanId")])
data class TimerSession(
    @PrimaryKey val id: Int = 1,
    val token: String = UUID.randomUUID().toString(),
    val title: String,
    val categoryId: Long? = null,
    val sourcePlanId: Long? = null,
    val mode: TimerMode = TimerMode.TIMER,
    val startTimestamp: Instant,
    val segmentStartedAt: Instant? = startTimestamp,
    val pausedAt: Instant? = null,
    val pausedDuration: Long = 0,
    val phase: TimerPhase = TimerPhase.WORK,
    val phaseStartedAt: Instant = startTimestamp,
    val phasePausedDuration: Long = 0,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val cycles: Int = 4,
    val cycle: Int = 1,
    val nextPart: Int = 0,
    @ColumnInfo(defaultValue = "''") val propertiesJson: String = "",
)

// Pauses delimit actual work. Slices stay private to the timer until it finishes.
@Entity(tableName = "timer_slices")
data class TimerSlice(@PrimaryKey(autoGenerate = true) val id: Long = 0, val start: Instant, val end: Instant)

enum class ReminderAnchor { START, DEADLINE }

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(Plan::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE)], indices = [Index("planId")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val anchor: ReminderAnchor = ReminderAnchor.START,
    val minutesBefore: Int = 15,
    val deliveredAt: Instant? = null,
)

enum class PropertyType { TEXT, NUMBER, BOOLEAN, SELECT, MULTISELECT, RATING }@Entity(tableName = "property_definitions", foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class PropertyDefinition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val type: PropertyType,
    val options: String = "",
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val required: Boolean = false,
    @ColumnInfo(defaultValue = "0") val multiline: Boolean = false,
    @ColumnInfo(defaultValue = "''") val optionColors: String = "",
    @ColumnInfo(defaultValue = "''") val unit: String = "",
    @ColumnInfo(defaultValue = "''") val defaultValue: String = "",
)

@Entity(tableName = "property_values", primaryKeys = ["recordId", "definitionId"], foreignKeys = [
    ForeignKey(Record::class, ["id"], ["recordId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(PropertyDefinition::class, ["id"], ["definitionId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("definitionId")])
data class PropertyValue(val recordId: Long, val definitionId: Long, val value: String)

@Entity(tableName = "plan_property_values", primaryKeys = ["planId", "definitionId"], foreignKeys = [
    ForeignKey(Plan::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(PropertyDefinition::class, ["id"], ["definitionId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("definitionId")])
data class PlanPropertyValue(val planId: Long, val definitionId: Long, val value: String)

enum class GoalMetric { TIME, COUNT }
enum class GoalDirection { AT_LEAST, AT_MOST }
enum class GoalRepeat { NONE, CYCLE, RESET }

/** The smallest unit the app measures is one minute. */
enum class GoalUnit { MONTH, WEEK, DAY, HOUR, MINUTE }

/**
 * One instance of a target over records, optionally narrowed by title or one property filter.
 *
 * Each instance is a goal of its own: [startAt] is when it began, and [expiredAt] is set once it is
 * over, which moves it into the ended list. Repeating never links instances — the rule only creates
 * them — so deleting one goal never touches another.
 */
@Entity(tableName = "goals", foreignKeys = [
    ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(PropertyDefinition::class, ["id"], ["propertyDefinitionId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index("categoryId"), Index("propertyDefinitionId")])
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val name: String = "",
    val categoryId: Long? = null,
    val titleFilter: String = "",
    val propertyDefinitionId: Long? = null,
    @ColumnInfo(name = "propertyValue") val propertyFilter: String = "",
    val metric: GoalMetric = GoalMetric.TIME,
    val direction: GoalDirection = GoalDirection.AT_LEAST,
    val target: Long = 0,
    @ColumnInfo(name = "period") val periodUnit: GoalUnit = GoalUnit.WEEK,
    @ColumnInfo(defaultValue = "1") val periodValue: Int = 1,
    val repeat: GoalRepeat = GoalRepeat.CYCLE,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val startAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now(),
    val sortOrder: Int = 0,
    /** When this instance ran out; null while it is still the running one. */
    val expiredAt: Instant? = null,
)
