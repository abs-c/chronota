package app.chronota

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chronota.data.db.AppDatabase
import app.chronota.data.entity.*
import app.chronota.data.repository.*
import app.chronota.domain.elapsed
import app.chronota.domain.LIVE_RECORD_ID
import app.chronota.domain.liveRecord
import app.chronota.domain.phaseEnd
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class MutableClock(var value: Instant) : Clock() {
    override fun instant(): Instant = value
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    fun advance(seconds: Long) { value = value.plusSeconds(seconds) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerTest {
    @Test fun pomodoroCatchesUpAfterProcessAbsenceAndExcludesBreaks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val clock = MutableClock(Instant.parse("2026-09-07T12:00:00Z"))
        val timer = TimerRepository(TimeRepository(db, clock))
        try {
            timer.start("Focus", null, null, TimerMode.POMODORO, 2, 1, 2)
            // The process was away for all of this: only the clock moved.
            clock.advance(1090)
            assertTrue(timer.advance())
            assertFalse(timer.advance())
            assertNull(db.dao().currentTimer())
            val records = db.dao().allRecords()
            assertEquals(240L, records.sumOf { Duration.between(it.startTime, it.endTime).seconds })
            assertTrue(records.all { it.createdBy == RecordSource.POMODORO })
            assertEquals(2, records.size)
        } finally { db.close() }
    }
    @Test fun cancellingATimerKeepsNothingAndFinishingIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "timer-recovery-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val clock = MutableClock(Instant.parse("2026-09-07T23:50:00Z"))
        var repo = TimeRepository(db, clock)
        var timer = TimerRepository(repo)
        try {
            timer.start("Night work", null, null)
            clock.advance(60)
            timer.cancel()
            // A cancelled run is not a moment that happened: no record, no timer.
            assertNull(db.dao().currentTimer())
            assertTrue(db.dao().allRecords().isEmpty())

            timer.start("Night work", null, null)
            clock.advance(1200)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repo = TimeRepository(db, clock)
            timer = TimerRepository(repo)
            // The run survives a restart and keeps counting from the clock alone.
            assertEquals(1200_000L, db.dao().currentTimer()!!.elapsed(clock.instant()))
            clock.advance(900)
            assertEquals(2100_000L, db.dao().currentTimer()!!.elapsed(clock.instant()))
            assertEquals(1, timer.finish())
            assertEquals(0, timer.finish())
            val records = db.dao().allRecords()
            assertEquals(1, records.size)
            assertEquals(2100L, Duration.between(records.first().startTime, records.first().endTime).seconds)
            assertTrue(records.all { it.createdBy == RecordSource.TIMER && it.sourcePlanId == null })
            // The one interval crosses midnight, yet is still a single record.
            assertNotEquals(records.first().startTime.atZone(ZoneOffset.UTC).toLocalDate(), records.first().endTime.atZone(ZoneOffset.UTC).toLocalDate())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** The running timer is shown as the record it will become, without being stored. */
    @Test fun aRunningTimerContributesALiveRecord() {
        val start = Instant.parse("2026-09-07T09:00:00Z")
        val now = Instant.parse("2026-09-07T09:40:00Z")
        val running = TimerSession(title = "Reading", startTimestamp = start, segmentStartedAt = start)
        val live = running.liveRecord(now)!!
        assertEquals(LIVE_RECORD_ID, live.id)
        assertEquals("Reading", live.title)
        assertEquals(start, live.startTime)
        // It ends at "now", so it grows while the timer runs.
        assertEquals(now, live.endTime)
        assertEquals(RecordSource.TIMER, live.createdBy)
        // A pomodoro's break is not recorded, so there is nothing to show during one.
        val breaking = running.copy(mode = TimerMode.POMODORO, phase = TimerPhase.BREAK)
        assertNull(breaking.liveRecord(now))
        // Its work phase is, and it starts where the current segment did.
        val working = running.copy(mode = TimerMode.POMODORO, phase = TimerPhase.WORK, phaseStartedAt = start, segmentStartedAt = null)
        assertEquals(start, working.liveRecord(now)!!.startTime)
        assertEquals(RecordSource.POMODORO, working.liveRecord(now)!!.createdBy)
    }
}
