package app.chronotation

import app.chronotation.data.entity.*
import app.chronotation.domain.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class TimeRulesTest {
    @Test fun timelineSelectionRestrictsCreationAndPrefillsDraggedRange() {
        val day = daySpan(LocalDate.of(2026, 9, 8), ZoneOffset.UTC)
        val now = day.start.plusSeconds(12 * 3600L + 7 * 60)
        assertNull(timelineSelection(day, 600f, 630f, 15, true, now))
        assertNull(timelineSelection(day, 780f, 810f, 15, false, now))
        val plan = timelineSelection(day, 800f, 860f, 15, true, now)!!
        assertEquals(day.start.plusSeconds(795 * 60), plan.start)
        assertEquals(day.start.plusSeconds(870 * 60), plan.end)
        val record = timelineSelection(day, 700f, 750f, 15, false, now)!!
        assertEquals(now, record.end)
        assertTrue(record.start < record.end)
        assertNull(timelineSelection(day, 727f, 727f, 15, false, now))
    }

    @Test fun allDayUsesCalendarDaysAcrossDst() {
        val allDay = Plan(title = "Travel", scheduledDate = LocalDate.of(2026, 3, 8), allDay = true, zoneId = "America/New_York")
        assertEquals(23 * 3600_000L, allDay.timeSpan()!!.millis)
        assertEquals(47 * 3600_000L, allDay.copy(endDayOffset = 1).timeSpan()!!.millis)
    }
    private val start = Instant.parse("2026-09-07T09:00:00Z")
    private val plan = Plan(title = "Work", scheduledDate = LocalDate.of(2026, 9, 7), startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), zoneId = "UTC")
    @Test fun exactBoundariesAndUnscheduled() {
        assertEquals(PlanTemporalState.FUTURE, plan.temporalState(start.minusMillis(1)))
        assertEquals(PlanTemporalState.ACTIVE, plan.temporalState(start))
        assertEquals(PlanTemporalState.PAST, plan.temporalState(start.plusSeconds(3600)))
        assertEquals(PlanTemporalState.UNSCHEDULED, Plan(title = "Inbox").temporalState(start))
    }
    @Test fun crossDayAndDstPreserveRealDuration() {
        assertEquals(23 * 3600_000L, daySpan(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York")).millis)
        assertEquals(25 * 3600_000L, daySpan(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York")).millis)
        assertEquals(2 * 3600_000L, plan.copy(startTime = LocalTime.of(23, 0), endTime = LocalTime.of(1, 0), endDayOffset = 1).timeSpan()!!.millis)
        try { resolveLocal(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ZoneId.of("America/New_York")); fail("DST gap accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun draftCopiesContentWithoutLinkAndOverlapLayoutKeepsAllItems() {
        val draft = plan.copy(id = 8).recordDraft(start.plusSeconds(7200))
        assertNull(draft.sourcePlanId)
        assertEquals(plan.timeSpan()!!.end, draft.endTime)
        val spans = listOf(1L to TimeSpan(start, start.plusSeconds(3600)), 2L to TimeSpan(start.plusSeconds(1800), start.plusSeconds(5400)), 3L to TimeSpan(start.plusSeconds(5400), start.plusSeconds(6000)))
        val result = arrangeOverlaps(spans)
        assertEquals(listOf(2, 2, 1), result.map { it.lanes })
    }

    @Test fun editingLaterRepeatedHourKeepsAbsoluteTime() {
        val zone = ZoneId.of("America/New_York")
        val original = Instant.parse("2026-11-01T06:30:00Z")
        assertEquals(original, resolveEditedInstant(original, LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), zone))
        assertEquals(Instant.parse("2026-11-01T07:30:00Z"), resolveEditedInstant(original, LocalDate.of(2026, 11, 1), LocalTime.of(2, 30), zone))
    }
}
