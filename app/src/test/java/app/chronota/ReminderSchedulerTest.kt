package app.chronota

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chronota.data.db.AppDatabase
import app.chronota.data.entity.*
import app.chronota.data.repository.TimeRepository
import app.chronota.domain.*
import app.chronota.feature.timer.NotificationScheduler
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    @Test fun alarmsRestoreMoveAndCancelWithoutDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        ShadowAlarmManager.setAutoSchedule(false)
        val shadow = shadowOf(context.getSystemService(AlarmManager::class.java))
        val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
        val repo = TimeRepository(db, clock)
        try {
            val date = LocalDate.now(ZoneOffset.UTC).plusDays(1)
            val id = repo.savePlan(Plan(title = "Alert", scheduledDate = date, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), zoneId = "UTC"),
                HistoricalPlanPolicy.IMMUTABLE, listOf(Reminder(planId = 0, minutesBefore = 15), Reminder(planId = 0, minutesBefore = 5)))
            NotificationScheduler(context, repo).reschedule()
            assertEquals(2, shadow.scheduledAlarms.size)
            val restored = NotificationScheduler(context, repo)
            restored.reschedule()
            assertEquals(2, shadow.scheduledAlarms.size)
            val start = date.atTime(11, 0).toInstant(ZoneOffset.UTC)
            repo.schedulePlan(id, start, start.plusSeconds(3600), HistoricalPlanPolicy.IMMUTABLE)
            restored.reschedule()
            assertEquals(listOf(start.minusSeconds(900).toEpochMilli(), start.minusSeconds(300).toEpochMilli()), shadow.scheduledAlarms.map { it.triggerAtMs }.sorted())
            repo.deletePlan(id, HistoricalPlanPolicy.IMMUTABLE)
            restored.reschedule()
            assertTrue(shadow.scheduledAlarms.isEmpty())
        } finally { db.close() }
    }
}
