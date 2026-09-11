package app.chronotation

import app.chronotation.data.entity.*
import app.chronotation.domain.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ReviewTest {
    @Test fun rangeClipsCrossDayRecordsAndTotalsPlanAndRecordIndependently() {
        val date = LocalDate.of(2026, 9, 7)
        val start = date.atTime(9, 0).toInstant(ZoneOffset.UTC)
        val plan = Plan(id = 1, title = "Same title", scheduledDate = date, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), zoneId = "UTC")
        val records = listOf(
            Record(id = 1, title = "Same title", startTime = start.minusSeconds(10 * 3600), endTime = start.minusSeconds(8 * 3600)),
            Record(id = 2, title = "Actual", startTime = start.plusSeconds(3600), endTime = start.plusSeconds(5400), sourcePlanId = 1),
            Record(id = 3, title = "Actual", startTime = start.plusSeconds(4500), endTime = start.plusSeconds(6300), sourcePlanId = 1),
        )
        val report = reviewReport(records, listOf(plan), emptyList(), daySpan(date, ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(2 * 3600_000L, report.total)
        assertEquals(3600_000L, report.planned)
        assertEquals(report.total, reviewReport(records, emptyList(), emptyList(), daySpan(date, ZoneOffset.UTC), ZoneOffset.UTC).total)
        assertEquals(report.total, report.trend.single().second)
        assertEquals(start.minusSeconds(10 * 3600), records[0].startTime)
    }
}
