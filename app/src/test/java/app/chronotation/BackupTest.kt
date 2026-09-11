package app.chronotation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chronotation.data.backup.BackupFormat
import app.chronotation.data.backup.BackupRepository
import app.chronotation.data.backup.BackupScheduler
import app.chronotation.data.backup.WebDavClient
import app.chronotation.data.backup.WebDavFailure
import app.chronotation.data.backup.WebDavTarget
import app.chronotation.data.db.AppDatabase
import app.chronotation.data.entity.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup has to come back the way it went in, and a file that is not ours — or is from a newer
 * build — has to be refused without touching what is already stored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    private lateinit var db: AppDatabase
    private lateinit var backups: BackupRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        backups = BackupRepository(db, Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC))
    }

    @After fun close() = db.close()

    private fun seed() = runBlocking {
        val dao = db.dao()
        val parent = dao.putCategory(Category(name = "Sonata"))
        val child = dao.putCategory(Category(name = "Study", parentId = parent, color = 4283669953, icon = "book", sortOrder = 2))
        val definition = dao.putDefinition(PropertyDefinition(categoryId = parent, name = "Format", type = PropertyType.SELECT,
            options = "Book\nPaper", required = true, unit = "pages", defaultValue = "Book", sortOrder = 1))
        val plan = dao.putPlan(Plan(title = "Evening", categoryId = parent, scheduledDate = LocalDate.of(2026, 9, 10),
            startTime = LocalTime.of(21, 0), endTime = LocalTime.of(21, 30), zoneId = "UTC", note = "note",
            recurrence = Recurrence.WEEKLY, recurrenceInterval = 2, recurrenceUntil = LocalDate.of(2026, 12, 31),
            allDay = false, deadline = Instant.parse("2026-09-10T20:00:00Z"), estimatedDuration = 1800))
        val record = dao.putRecord(Record(title = "Reading", categoryId = child, startTime = Instant.parse("2026-09-10T21:00:00Z"),
            endTime = Instant.parse("2026-09-10T21:30:00Z"), createdBy = RecordSource.TIMER, timerToken = "token", timerPart = 1))
        dao.putReminder(Reminder(planId = plan, anchor = ReminderAnchor.DEADLINE, minutesBefore = 30))
        dao.putValue(PropertyValue(record, definition, "Paper"))
        dao.putPlanValue(PlanPropertyValue(plan, definition, "Book"))
        dao.putGoal(Goal(name = "Focus", categoryId = parent, titleFilter = "Read", propertyDefinitionId = definition,
            propertyFilter = "Book", metric = GoalMetric.TIME, direction = GoalDirection.AT_LEAST, target = 600_000,
            periodUnit = GoalUnit.WEEK, periodValue = 3, repeat = GoalRepeat.RESET, startAt = Instant.parse("2026-09-01T00:00:00Z"),
            expiredAt = Instant.parse("2026-09-08T00:00:00Z")))
        parent
    }

    @Test fun aBackupComesBackExactlyAsItWentIn() = runBlocking {
        val parent = seed()
        val text = backups.export()
        val summary = backups.restore(text)
        val dao = db.dao()
        assertEquals(2, summary.categories)
        assertEquals(2, dao.allCategories().size)
        assertEquals("Study", dao.allCategories().single { it.parentId == parent }.name)
        assertEquals(2, dao.allCategories().single { it.name == "Study" }.sortOrder)
        val plan = dao.allPlans().single()
        assertEquals("Evening", plan.title)
        assertEquals(LocalDate.of(2026, 9, 10), plan.scheduledDate)
        assertEquals(LocalTime.of(21, 0), plan.startTime)
        assertEquals(Recurrence.WEEKLY, plan.recurrence)
        assertEquals(2, plan.recurrenceInterval)
        assertEquals(LocalDate.of(2026, 12, 31), plan.recurrenceUntil)
        assertEquals(Instant.parse("2026-09-10T20:00:00Z"), plan.deadline)
        assertEquals(1800L, plan.estimatedDuration)
        assertEquals("note", plan.note)
        val record = dao.allRecords().single()
        assertEquals(RecordSource.TIMER, record.createdBy)
        assertEquals("token", record.timerToken)
        assertEquals(1, record.timerPart)
        assertEquals(Instant.parse("2026-09-10T21:30:00Z"), record.endTime)
        assertEquals("Paper", dao.allValues().single().value)
        assertEquals("Book", dao.allPlanValues().single().value)
        assertEquals(RecordSource.TIMER, record.createdBy)
        val goal = dao.allGoals().single()
        assertEquals("Focus", goal.name)
        assertEquals(GoalUnit.WEEK, goal.periodUnit)
        assertEquals(3, goal.periodValue)
        assertEquals(Instant.parse("2026-09-08T00:00:00Z"), goal.expiredAt)
        val definition = dao.allDefinitions().single()
        assertEquals("Format", definition.name)
        assertTrue(definition.required)
        assertEquals("pages", definition.unit)
        assertEquals("Book", definition.defaultValue)
    }

    @Test fun aRestoreReplacesWhatWasThereAndKeepsEveryRow() = runBlocking {
        seed()
        val text = backups.export()
        // A second, different world: one category, one record. Restoring must not merge the two.
        db.dao().clearPlanValueRows(); db.dao().clearValueRows(); db.dao().clearReminderRows()
        db.dao().clearGoalRows(); db.dao().clearRecordRows(); db.dao().clearPlanRows()
        db.dao().clearDefinitionRows(); db.dao().clearChildCategories(); db.dao().clearRootCategories()
        db.dao().putCategory(Category(name = "Only"))
        backups.restore(text)
        assertEquals(listOf("Sonata", "Study"), db.dao().allCategories().map { it.name }.sorted())
        assertEquals(1, db.dao().allRecords().size)
        assertEquals(1, db.dao().allPlans().size)
        assertEquals(1, db.dao().allRecords().size)
        assertEquals(1, db.dao().allReminders().size)
        assertEquals(1, db.dao().allGoals().size)
        assertEquals(1, db.dao().allDefinitions().size)
    }

    @Test fun aFileThatIsNotOursOrIsTooNewIsRefused() = runBlocking {
        seed()
        val before = backups.export()
        for (bad in listOf("{}", "not json", """{"app":"someone-else","format":1}""", """{"app":"chronota","format":99}""")) {
            try { backups.restore(bad); fail("Accepted: $bad") }
            catch (error: BackupFormat.Unreadable) { assertTrue(error.reason.isNotBlank()) }
        }
        // Nothing was touched: the refusal happens before the tables are emptied.
        assertEquals(before, backups.export())
    }

    @Test fun aBackupIntoAMissingParentStillRestores() = runBlocking {
        // A hand-edited file with a child whose parent is absent must not lose the child.
        val text = BackupFormat.write(
            BackupFormat.Data(
                categories = listOf(Category(id = 1, name = "Orphan", parentId = 9)),
                plans = emptyList(), records = emptyList(), reminders = emptyList(), goals = emptyList(),
                definitions = emptyList(), values = emptyList(), planValues = emptyList(),
            ),
            schema = 11, exportedAt = Instant.parse("2026-09-11T12:00:00Z"),
        )
        backups.restore(text)
        val stored = db.dao().allCategories().single()
        assertEquals("Orphan", stored.name)
        assertNull(stored.parentId)
    }

    @Test fun theAlarmLandsOnTheNextTimeOfDay() {
        val zone = ZoneOffset.UTC
        // 03:00 tomorrow, because today's has gone.
        assertEquals(Instant.parse("2026-09-12T03:00:00Z"), BackupScheduler.nextTrigger(3 * 60, Instant.parse("2026-09-11T12:00:00Z"), zone))
        // Still today when it has not gone yet, and again tomorrow one second later.
        assertEquals(Instant.parse("2026-09-11T20:00:00Z"), BackupScheduler.nextTrigger(20 * 60, Instant.parse("2026-09-11T12:00:00Z"), zone))
        assertEquals(Instant.parse("2026-09-12T20:00:00Z"), BackupScheduler.nextTrigger(20 * 60, Instant.parse("2026-09-11T20:00:00Z"), zone))
    }

    @Test fun webDavAddressesArePutTogetherWithoutDoublingSlashes() {
        val client = WebDavClient(WebDavTarget("https://dav.example.com/dav/", "me", "secret"), "chronota/inner")
        assertEquals("https://dav.example.com/dav/chronota/inner", client.folderUrl())
        assertEquals("https://dav.example.com/dav/chronota/inner/chronota-latest.json", client.fileUrl("chronota-latest.json"))
        // A blank folder means the account root, and an unconfigured account fails before any request.
        assertEquals("https://dav.example.com/dav", WebDavClient(WebDavTarget("https://dav.example.com/dav/", "me", ""), "").folderUrl())
        try {
            WebDavClient(WebDavTarget("", "", "")).probe()
            fail("Probed an unconfigured account")
        } catch (error: app.chronotation.data.backup.WebDavException) {
            assertEquals(WebDavFailure.NOT_CONFIGURED, error.failure)
        }
    }

    @Test fun anUnreachableAddressIsReportedAsSuch() {
        // No server listens on port 1, so the failure has to come back as a network one rather than a crash.
        val client = WebDavClient(WebDavTarget("http://127.0.0.1:1/dav/", "me", "secret"), "chronota")
        try {
            client.probe()
            fail("Reached a server that is not there")
        } catch (error: app.chronotation.data.backup.WebDavException) {
            assertTrue(error.failure == WebDavFailure.NETWORK || error.failure == WebDavFailure.ADDRESS)
        }
    }
}
