package app.chronota

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chronota.data.db.AppDatabase
import app.chronota.data.entity.*
import app.chronota.data.repository.*
import app.chronota.domain.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TimeRepository
    private val now = Instant.parse("2026-09-07T04:00:00Z")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = TimeRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }
    @After fun close() { db.close() }

    @Test fun optionalTitlesRemainEmptyForPlansRecordsAndTimerSessions() = runBlocking {
        val plan = repo.savePlan(Plan(title = "  "), HistoricalPlanPolicy.IMMUTABLE)
        val record = repo.saveRecord(Record(title = "", startTime = now.minusSeconds(60), endTime = now))
        assertEquals("", db.dao().plan(plan)!!.title)
        assertEquals("", db.dao().record(record)!!.title)
        TimerRepository(repo).start("", null, null)
        assertEquals("", db.dao().currentTimer()!!.title)
        TimerRepository(TimeRepository(db, Clock.fixed(now.plusSeconds(120), ZoneOffset.UTC))).finish()
        assertTrue(db.dao().allRecords().all { it.title.isEmpty() && it.sourcePlanId == null })
    }

    @Test fun zeroDurationRecordsAreAccepted() = runBlocking {
        val instant = now.minusSeconds(60)
        val id = repo.saveRecord(Record(title = "Instant", startTime = instant, endTime = instant))
        assertTrue(id > 0)
        assertEquals(instant, db.dao().record(id)!!.startTime)
        assertEquals(instant, db.dao().record(id)!!.endTime)
    }

    @Test fun storedMomentsAreCutBackToTheMinute() = runBlocking {
        // A timer stops on a second, but one minute is the smallest unit that gets recorded, so the
        // seconds go before the row is written — on plans as much as on records.
        val plan = repo.savePlan(Plan(title = "Rounded", scheduledDate = LocalDate.of(2026, 9, 7),
            startTime = LocalTime.of(10, 0, 59), endTime = LocalTime.of(11, 0, 30), zoneId = "UTC"), HistoricalPlanPolicy.IMMUTABLE)
        val record = repo.saveRecord(Record(title = "Rounded", startTime = now.minusSeconds(1259).plusMillis(432), endTime = now.minusSeconds(59).plusMillis(987)))
        val storedPlan = db.dao().plan(plan)!!
        assertEquals(LocalTime.of(10, 0), storedPlan.startTime)
        assertEquals(LocalTime.of(11, 0), storedPlan.endTime)
        val stored = db.dao().record(record)!!
        assertEquals(Instant.parse("2026-09-07T03:39:00Z"), stored.startTime)
        assertEquals(Instant.parse("2026-09-07T03:59:00Z"), stored.endTime)
    }

    @Test fun aBackwardsRangeIsStillRefusedBeforeRounding() = runBlocking {
        // 03:59:50 to 03:59:10 is backwards, yet both fall in the 03:59 minute: checking the range
        // before rounding is what keeps this refused instead of quietly becoming a zero-length record.
        try {
            repo.saveRecord(Record(title = "Backwards", startTime = now.minusSeconds(10), endTime = now.minusSeconds(50))); fail("Backwards range accepted")
        } catch (_: RuleViolation) { }
    }

    @Test fun defaultsMergeWithoutDuplicatesAndRatingErrorsAreSpecific() = runBlocking {
        val nocturne = repo.saveCategory(Category(name = "Nocturne"))
        repo.installDefaultCategories()
        repo.installDefaultCategories()
        val all = db.dao().allCategories()
        assertEquals(6, all.size)
        assertEquals(nocturne, all.single { it.name == "Nocturne" }.id)
        val cinema = all.single { it.name == "观影" }
        val rating = db.dao().allDefinitions().single { it.type == PropertyType.RATING }
        val type = db.dao().allDefinitions().single { it.name == "类型" }
        val record = Record(title = "Film", categoryId = cinema.id, startTime = now.minusSeconds(60), endTime = now)
        for (value in listOf("0", "0.5", "4.5", "5")) repo.saveRecord(record, mapOf(rating.id to value, type.id to "电影"))
        for (value in listOf("12", "-1", "4.222", "NaN")) {
            try { repo.saveRecord(record, mapOf(rating.id to value, type.id to "电影")); fail("Invalid rating") }
            catch (error: RuleViolation) { assertEquals("property_value", error.reason) }
        }
        try { repo.saveDefinition(rating.copy(id = 0)); fail("Duplicate accepted") }
        catch (error: RuleViolation) { assertEquals("property_duplicate", error.reason) }
    }

    @Test fun defaultOptionsValidateAndPersistWithoutRewritingExistingValues() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Study"))
        val category = repo.saveCategory(Category(name = "Reading", parentId = parent, color = 0xFF537DC1, icon = "book"))
        val definition = PropertyDefinition(categoryId = category, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper", required = true, optionColors = "537DC1\n52936E", defaultValue = "Book")
        val id = repo.saveDefinition(definition)
        assertEquals("Book", db.dao().allDefinitions().single().defaultValue)
        try { repo.saveDefinition(definition.copy(id = id, defaultValue = "Unknown")); fail("Invalid default accepted") } catch (_: RuleViolation) { }
        val record = repo.saveRecord(Record(title = "Read", categoryId = category, startTime = now.minusSeconds(60), endTime = now), mapOf(id to "Paper"))
        repo.saveDefinition(definition.copy(id = id, defaultValue = "Paper"))
        assertEquals("Paper", db.dao().recordValues(record).single().value)
    }

    @Test fun timerStartsWithoutRequiredAttributesButRecordsDemandThem() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Study"))
        val category = repo.saveCategoryWithDefinitions(Category(name = "Reading", parentId = parent, color = 0xFF537DC1, icon = "book"),
            listOf(PropertyDefinition(id = -1, categoryId = 0, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper", required = true, optionColors = "537DC1\n52936E")))
        val definition = db.dao().allDefinitions().single()
        val timers = TimerRepository(repo)
        // Starting a timer never asks for required attributes.
        timers.start("Reading", category, null)
        assertNotNull(db.dao().currentTimer())
        assertTrue(db.dao().allRecords().isEmpty())
        TimerRepository(TimeRepository(db, Clock.fixed(now.plusSeconds(120), ZoneOffset.UTC))).finish()
        val record = db.dao().allRecords().single()
        assertTrue(db.dao().recordValues(record.id).isEmpty())
        // Editing that record still enforces them.
        val editable = record.copy(title = "Edited", endTime = minOf(record.endTime, now))
        try { repo.saveRecord(editable, emptyMap()); fail("Missing required value accepted") } catch (e: RuleViolation) { assertEquals("property_required", e.reason) }
        repo.saveRecord(editable, mapOf(definition.id to "Book"))
        assertEquals("Book", db.dao().recordValues(record.id).single().value)
    }

    @Test fun requiredAttributesAreOptionalForPlansAndPersistForTimerRecords() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Study"))
        val category = repo.saveCategoryWithDefinitions(Category(name = "Reading", parentId = parent, color = 0xFF537DC1, icon = "book"),
            listOf(PropertyDefinition(id = -1, categoryId = 0, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper", required = true, optionColors = "537DC1\n52936E")))
        val definition = db.dao().allDefinitions().single()
        val plan = repo.savePlan(Plan(title = "Read", categoryId = category, allDay = true, scheduledDate = LocalDate.of(2026, 9, 8)), HistoricalPlanPolicy.IMMUTABLE, properties = emptyMap())
        assertTrue(db.dao().valuesForPlan(plan).isEmpty())
        val value = Record(title = "Read", categoryId = category, startTime = now.minusSeconds(3600), endTime = now)
        try { repo.saveRecord(value, emptyMap()); fail("Missing required value accepted") } catch (e: RuleViolation) { assertEquals("property_required", e.reason) }
        assertTrue(db.dao().allRecords().isEmpty())
        repo.savePlan(db.dao().plan(plan)!!, HistoricalPlanPolicy.IMMUTABLE, properties = mapOf(definition.id to "Paper"))
        assertEquals("Paper", db.dao().valuesForPlan(plan).single().value)
        val timers = TimerRepository(repo)
        timers.start("Reading", category, plan, properties = mapOf(definition.id to "Book"))
        assertNull(db.dao().currentTimer()!!.sourcePlanId)
        val later = TimerRepository(TimeRepository(db, Clock.fixed(now.plusSeconds(120), ZoneOffset.UTC)))
        later.finish()
        val record = db.dao().allRecords().single()
        assertNull(record.sourcePlanId)
        assertEquals("Book", db.dao().recordValues(record.id).single().value)
    }

    @Test fun reorderingAndMovingChildrenPreservesTheirTaskReferences() = runBlocking {
        val a = repo.saveCategory(Category(name = "A"))
        val b = repo.saveCategory(Category(name = "B"))
        val first = repo.saveCategory(Category(name = "First", parentId = a, icon = "book", color = 1))
        val second = repo.saveCategory(Category(name = "Second", parentId = a, icon = "code", color = 2))
        val plan = repo.savePlan(Plan(title = "Read", categoryId = first), HistoricalPlanPolicy.IMMUTABLE)
        repo.moveCategory(a, b)
        assertTrue(db.dao().category(a)!!.sortOrder > db.dao().category(b)!!.sortOrder)
        repo.moveCategory(first, second)
        assertTrue(db.dao().category(first)!!.sortOrder > db.dao().category(second)!!.sortOrder)
        repo.moveCategory(first, b)
        assertEquals(b, db.dao().category(first)!!.parentId)
        assertEquals(first, db.dao().plan(plan)!!.categoryId)
    }

    @Test fun firstSchemaReopensWithoutLosingBusinessData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "schema-reopen-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        first.dao().putPlan(Plan(title = "Survives reopen"))
        first.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try { assertEquals("Survives reopen", reopened.dao().allPlans().single().title) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun categoryHierarchyAndDeletionCascadeOnlyInsideTarget() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Research", color = 123, icon = "book"))
        assertNull(db.dao().category(parent)!!.color)
        val child = repo.saveCategory(Category(name = "Simulation", parentId = parent, color = 0xFF6688CC, icon = "code"))
        val planId = repo.savePlan(Plan(title = "Run", categoryId = child), HistoricalPlanPolicy.IMMUTABLE)
        assertNotEquals(0L, planId)

        try { repo.saveCategory(Category(name = "Third", parentId = child, color = 1, icon = "code")); fail("Third level accepted") } catch (_: RuleViolation) { }
        val other = repo.saveCategory(Category(name = "Keep"))
        val definition = repo.saveDefinition(PropertyDefinition(categoryId = child, name = "Thoughts", type = PropertyType.TEXT, multiline = true))
        val record = repo.saveRecord(Record(title = "Old", categoryId = child, startTime = now.minusSeconds(60), endTime = now), mapOf(definition to "line 1\nline 2"))
        db.dao().putPlanValue(PlanPropertyValue(planId, definition, "draft"))
        db.dao().putReminder(Reminder(planId = planId, anchor = ReminderAnchor.START, minutesBefore = 0))
        TimerRepository(repo).start("Active", child, null)
        repo.deleteCategory(parent)
        assertEquals(listOf(other), db.dao().allCategories().map { it.id })
        assertNull(db.dao().plan(planId))
        assertNull(db.dao().record(record))
        assertTrue(db.dao().allDefinitions().isEmpty())
        assertTrue(db.dao().allReminders().isEmpty())
        assertTrue(db.dao().recordValues(record).isEmpty())
        assertTrue(db.dao().valuesForPlan(planId).isEmpty())
        assertNull(db.dao().currentTimer())
        assertTrue(db.dao().slices().isEmpty())
    }

    @Test fun recordsStayIndependentWhenPlanIsEditedOrDeleted() = runBlocking {
        val id = repo.savePlan(Plan(title = "Plan"), HistoricalPlanPolicy.IMMUTABLE)
        val record = Record(title = "Actual", sourcePlanId = id, startTime = now.minusSeconds(3600), endTime = now)
        val recordId = repo.saveRecord(record)
        repo.savePlan(db.dao().plan(id)!!.copy(title = "New intention"), HistoricalPlanPolicy.EDITABLE)
        assertEquals("Actual", db.dao().record(recordId)!!.title)
        repo.deletePlan(id, HistoricalPlanPolicy.IMMUTABLE)
        assertNull(db.dao().record(recordId)!!.sourcePlanId)
        assertEquals(1, db.dao().allRecords().size)
    }

    @Test fun immutableHistoryIsEnforcedInsideRepository() = runBlocking {
        val id = repo.savePlan(Plan(title = "Past", scheduledDate = LocalDate.of(2026, 9, 6), zoneId = "UTC"), HistoricalPlanPolicy.IMMUTABLE)
        try { repo.savePlan(db.dao().plan(id)!!.copy(title = "Rewrite"), HistoricalPlanPolicy.IMMUTABLE); fail("History changed") } catch (_: RuleViolation) { }
        repo.savePlan(db.dao().plan(id)!!.copy(title = "Explicit edit"), HistoricalPlanPolicy.EDITABLE)
        assertEquals(0, db.dao().allRecords().size)
    }

    @Test fun activePlanCanOnlyBeResizedThroughRemainingTimeAction() = runBlocking {
        val id = repo.savePlan(Plan(title = "Active", scheduledDate = LocalDate.of(2026, 9, 7), startTime = LocalTime.of(3, 0), endTime = LocalTime.of(5, 0), zoneId = "UTC"), HistoricalPlanPolicy.IMMUTABLE)
        try { repo.schedulePlan(id, now, now.plusSeconds(3600), HistoricalPlanPolicy.IMMUTABLE); fail("Active block moved") } catch (_: RuleViolation) { }
        repo.adjustRemaining(id, now.plusSeconds(7200), HistoricalPlanPolicy.IMMUTABLE)
        assertEquals(LocalTime.of(3, 0), db.dao().plan(id)!!.startTime)
        assertEquals(LocalTime.of(6, 0), db.dao().plan(id)!!.endTime)
        assertTrue(db.dao().allRecords().isEmpty())
    }

    @Test fun remindersFollowReschedulingAndAreRemovedWithTheirPlan() = runBlocking {
        val plan = Plan(title = "Future", scheduledDate = LocalDate.of(2026, 9, 8), startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), zoneId = "UTC", deadline = now.plusSeconds(100_000))
        val id = repo.savePlan(plan, HistoricalPlanPolicy.IMMUTABLE, listOf(Reminder(planId = 0, minutesBefore = 15), Reminder(planId = 0, anchor = ReminderAnchor.DEADLINE, minutesBefore = 60)))
        val first = db.dao().allReminders().first { it.anchor == ReminderAnchor.START }
        assertEquals(Instant.parse("2026-09-08T08:45:00Z"), first.trigger(db.dao().plan(id)!!))
        repo.schedulePlan(id, Instant.parse("2026-09-08T11:00:00Z"), Instant.parse("2026-09-08T12:00:00Z"), HistoricalPlanPolicy.IMMUTABLE)
        assertEquals(2, db.dao().allReminders().size)
        assertEquals(Instant.parse("2026-09-08T10:45:00Z"), db.dao().allReminders().first { it.anchor == ReminderAnchor.START }.trigger(db.dao().plan(id)!!))
        repo.deletePlan(id, HistoricalPlanPolicy.IMMUTABLE)
        assertTrue(db.dao().allReminders().isEmpty())
    }

    @Test fun propertiesAreValidatedAtomicallyAndDefinitionsProtectHistory() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Research"))
        val category = repo.saveCategory(Category(name = "Simulation", parentId = parent, icon = "code", color = 0xFF6688CC))
        val definition = repo.saveDefinition(PropertyDefinition(categoryId = category, name = "N", type = PropertyType.NUMBER))
        val id = repo.saveRecord(Record(title = "Simulation", categoryId = category, startTime = now.minusSeconds(3600), endTime = now), mapOf(definition to "64"))
        assertEquals("64", db.dao().recordValues(id).single().value)
        try { repo.saveRecord(db.dao().record(id)!!.copy(title = "Invalid edit"), mapOf(definition to "NaN")); fail("Invalid value accepted") } catch (_: RuleViolation) { }
        assertEquals("Simulation", db.dao().record(id)!!.title)
        try { repo.deleteDefinition(definition); fail("Used definition deleted") } catch (_: RuleViolation) { }
        try { repo.saveRecord(db.dao().record(id)!!.copy(categoryId = null), emptyMap()); fail("Attributes silently lost") } catch (_: RuleViolation) { }
        repo.saveRecord(db.dao().record(id)!!, emptyMap())
        repo.saveRecord(db.dao().record(id)!!.copy(categoryId = null), emptyMap())
        repo.deleteDefinition(definition)
        assertTrue(db.dao().recordValues(id).isEmpty())
    }

    @Test fun attributeEditsOnlyWarnWhenSavedValuesLoseTheirShape() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Study"))
        val category = repo.saveCategory(Category(name = "Reading", parentId = parent, color = 0xFF537DC1, icon = "book"))
        val definition = PropertyDefinition(categoryId = category, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper", optionColors = "537DC1\n52936E")
        val id = repo.saveDefinition(definition)
        val record = repo.saveRecord(Record(title = "Read", categoryId = category, startTime = now.minusSeconds(60), endTime = now), mapOf(id to "Book"))
        // Adding a choice and recolouring keep every saved value, so they save without confirmation.
        repo.saveDefinition(definition.copy(id = id, options = "Book\nPaper\nFilm", optionColors = "111111\n222222\n333333", required = true))
        assertEquals("Book", db.dao().recordValues(record).single().value)
        // Dropping a choice that is in use asks first, and then removes exactly that value.
        try { repo.saveDefinition(definition.copy(id = id, options = "Paper", optionColors = "111111")); fail("Choice in use dropped") }
        catch (error: RuleViolation) { assertEquals("property_used", error.reason) }
        assertEquals("Book", db.dao().recordValues(record).single().value)
        repo.saveDefinition(definition.copy(id = id, options = "Paper", optionColors = "111111"), confirm = true)
        assertTrue(db.dao().recordValues(record).isEmpty())
        assertEquals("Paper", db.dao().allDefinitions().single().options)
        // Deleting an attribute with saved values follows the same rule.
        repo.saveRecord(db.dao().record(record)!!, mapOf(id to "Paper"))
        try { repo.deleteDefinition(id); fail("Used definition deleted") } catch (error: RuleViolation) { assertEquals("property_used", error.reason) }
        repo.deleteDefinition(id, confirm = true)
        assertTrue(db.dao().allDefinitions().isEmpty())
        assertTrue(db.dao().allValues().isEmpty())
    }

    @Test fun goalRolloverFilesRetiredInstancesAndKeepsTheRunningOne() = runBlocking {
        val parent = repo.saveCategory(Category(name = "Study"))
        val category = repo.saveCategory(Category(name = "Reading", parentId = parent, color = 0xFF537DC1, icon = "book"))
        val start = now.minus(Duration.ofDays(3))
        val goal = repo.saveGoal(Goal(name = "Read", categoryId = category, metric = GoalMetric.COUNT, target = 2,
            periodUnit = GoalUnit.DAY, repeat = GoalRepeat.CYCLE, startAt = start))
        repo.saveRecord(Record(title = "a", categoryId = category, startTime = start.plusSeconds(60), endTime = start.plusSeconds(120)))
        repo.rolloverGoals(now, ZoneOffset.UTC)
        // Three daily instances ran out; each became a goal of its own, and the running one is new.
        val goals = db.dao().allGoals().sortedBy { it.startAt }
        assertEquals(4, goals.size)
        assertEquals(listOf(start, start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(2))), goals.dropLast(1).map { it.startAt })
        assertEquals(listOf(start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(2)), start.plus(Duration.ofDays(3))), goals.dropLast(1).map { it.expiredAt })
        assertEquals(now, goals.last().startAt)
        assertNull(goals.last().expiredAt)
        // Rolling again changes nothing, and deleting one instance leaves the others alone.
        repo.rolloverGoals(now, ZoneOffset.UTC)
        assertEquals(4, db.dao().allGoals().size)
        repo.deleteGoal(goals.first().id)
        repo.rolloverGoals(now, ZoneOffset.UTC)
        assertEquals(3, db.dao().allGoals().size)
        assertTrue(db.dao().allGoals().none { it.id == goals.first().id })
    }

    @Test fun oneOffGoalExpiresWithoutASuccessor() = runBlocking {
        val category = repo.saveCategory(Category(name = "Study"))
        val goal = repo.saveGoal(Goal(name = "Trip", categoryId = category, metric = GoalMetric.COUNT, target = 1,
            periodUnit = GoalUnit.DAY, repeat = GoalRepeat.NONE, startAt = now.minus(Duration.ofDays(2))))
        repo.rolloverGoals(now, ZoneOffset.UTC)
        val expired = db.dao().goal(goal)!!
        assertEquals(now.minus(Duration.ofDays(2)), expired.startAt)
        assertEquals(now.minus(Duration.ofDays(1)), expired.expiredAt)
        // A one-off goal has no successor, so it leaves the running list for good.
        assertEquals(1, db.dao().allGoals().size)
        repo.rolloverGoals(now, ZoneOffset.UTC)
        assertEquals(1, db.dao().allGoals().size)
    }

    @Test fun recurrenceNeedsAnAnchorAndOrderedEndAndNormalizesWhenOff() = runBlocking {
        val base = Plan(title = "Daily", scheduledDate = LocalDate.of(2026, 9, 7), startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0), zoneId = "UTC")
        try { repo.savePlan(base.copy(recurrence = Recurrence.DAILY, recurrenceInterval = 0), HistoricalPlanPolicy.IMMUTABLE); fail("Interval accepted") }
        catch (error: RuleViolation) { assertEquals("invalid_recurrence", error.reason) }
        try { repo.savePlan(base.copy(scheduledDate = null, startTime = null, endTime = null, recurrence = Recurrence.DAILY), HistoricalPlanPolicy.IMMUTABLE); fail("Missing anchor accepted") }
        catch (error: RuleViolation) { assertEquals("invalid_recurrence", error.reason) }
        try { repo.savePlan(base.copy(recurrence = Recurrence.WEEKLY, recurrenceUntil = LocalDate.of(2026, 9, 1)), HistoricalPlanPolicy.IMMUTABLE); fail("Reversed end accepted") }
        catch (error: RuleViolation) { assertEquals("invalid_recurrence", error.reason) }

        val id = repo.savePlan(base.copy(recurrence = Recurrence.WEEKLY, recurrenceInterval = 2, recurrenceUntil = LocalDate.of(2026, 10, 31)), HistoricalPlanPolicy.IMMUTABLE)
        val stored = db.dao().plan(id)!!
        assertEquals(Recurrence.WEEKLY, stored.recurrence)
        assertEquals(2, stored.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 10, 31), stored.recurrenceUntil)

        val cleared = repo.savePlan(stored.copy(recurrence = Recurrence.NONE, recurrenceInterval = 5, recurrenceUntil = LocalDate.of(2026, 12, 31)), HistoricalPlanPolicy.EDITABLE)
        assertEquals(1, db.dao().plan(cleared)!!.recurrenceInterval)
        assertNull(db.dao().plan(cleared)!!.recurrenceUntil)
    }

    @Test fun recurringPlanStaysEditableAndKeepsItsSeriesOnReschedule() = runBlocking {
        val id = repo.savePlan(Plan(title = "Standup", scheduledDate = LocalDate.of(2026, 9, 1), startTime = LocalTime.of(1, 0), endTime = LocalTime.of(2, 0),
            zoneId = "UTC", recurrence = Recurrence.DAILY, recurrenceUntil = LocalDate.of(2026, 9, 30)), HistoricalPlanPolicy.IMMUTABLE)
        repo.savePlan(db.dao().plan(id)!!.copy(title = "Daily standup"), HistoricalPlanPolicy.IMMUTABLE)
        assertEquals("Daily standup", db.dao().plan(id)!!.title)
        assertEquals(Recurrence.DAILY, db.dao().plan(id)!!.recurrence)
        assertEquals(0, db.dao().allRecords().size)
    }
}
