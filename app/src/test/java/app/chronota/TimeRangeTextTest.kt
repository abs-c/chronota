package app.chronota

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import app.chronota.domain.TimeSpan
import app.chronota.ui.components.axisClockText
import app.chronota.ui.components.axisHourText
import app.chronota.ui.components.axisRangeEndsText
import app.chronota.ui.components.rangeEndsText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How a range is written down: bare clock times while it stays inside one calendar date, dates on
 * both ends once it crosses one, and the year only when it crosses a year as well.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
class TimeRangeTextTest {
    @get:Rule val compose = createComposeRule()

    private fun endsFrom(start: String, end: String): Pair<String, String> {
        var text: Pair<String, String>? = null
        compose.setContent { text = rangeEndsText(TimeSpan(Instant.parse(start), Instant.parse(end)), ZoneOffset.UTC) }
        return text!!
    }

    @Test fun aRangeInsideOneDateShowsOnlyClockTimes() {
        assertEquals("21:00" to "21:30", endsFrom("2026-09-10T21:00:00Z", "2026-09-10T21:30:00Z"))
    }

    @Test fun aRangeCrossingMidnightCarriesBothDates() {
        assertEquals("Sep 10 23:00" to "Sep 11 01:00", endsFrom("2026-09-10T23:00:00Z", "2026-09-11T01:00:00Z"))
    }

    @Test fun aRangeCrossingNewYearCarriesTheYearToo() {
        assertEquals("Dec 31, 2026 23:00" to "Jan 1, 2027 01:00", endsFrom("2026-12-31T23:00:00Z", "2027-01-01T01:00:00Z"))
    }

    @Test fun aMomentRepeatsItsOneTimeSoCallersPrintItAlone() {
        assertEquals("00:42" to "00:42", endsFrom("2026-09-11T00:42:00Z", "2026-09-11T00:42:00Z"))
    }

    /** A gutter label carries the next-date marker as a raised "+1" at its top right. */
    @Test fun axisLabelsMarkTheNextCalendarDate() {
        var hour: AnnotatedString? = null
        var clock: AnnotatedString? = null
        compose.setContent {
            hour = axisHourText(LocalTime.of(1, 0), 240)
            clock = axisClockText(Instant.parse("2026-09-11T00:42:00Z"), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
        }
        assertEquals("01:00 +1", hour?.text)
        assertEquals("00:42 +1", clock?.text)
    }

    @Test fun axisLabelsInTheDayItselfCarryNoMarker() {
        var hour: AnnotatedString? = null
        var clock: AnnotatedString? = null
        compose.setContent {
            hour = axisHourText(LocalTime.of(20, 0), 240)
            clock = axisClockText(Instant.parse("2026-09-10T20:42:00Z"), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
        }
        assertEquals("20:00", hour?.text)
        assertEquals("20:42", clock?.text)
    }

    /** The week gutter and the day gutter are the same column, down to the next-date marker. */
    @Test fun theWeekAndDayGuttersReadAlike() {
        var day: AnnotatedString? = null
        var week: AnnotatedString? = null
        var dayNext: AnnotatedString? = null
        var weekNext: AnnotatedString? = null
        compose.setContent {
            day = axisClockText(Instant.parse("2026-09-10T20:00:00Z"), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
            week = axisHourText(LocalTime.of(20, 0), 0)
            dayNext = axisClockText(Instant.parse("2026-09-11T02:00:00Z"), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
            weekNext = axisHourText(LocalTime.of(2, 0), 240)
        }
        assertEquals("20:00", day?.text)
        assertEquals(day?.text, week?.text)
        assertEquals("02:00 +1", dayNext?.text)
        assertEquals(dayNext?.text, weekNext?.text)
    }

    /** An agenda row's two ends: dates when it crosses a day, a raised "+1" when it does not. */
    @Test fun agendaEndsMarkTheNextDateOnRangesToo() {
        var smallHours: Pair<AnnotatedString, AnnotatedString>? = null
        compose.setContent {
            // 01:11-01:41 on the 10th, filed under the logical day of the 9th.
            smallHours = axisRangeEndsText(TimeSpan(Instant.parse("2026-09-10T01:11:00Z"), Instant.parse("2026-09-10T01:41:00Z")), LocalDate.of(2026, 9, 9), ZoneOffset.UTC)
        }
        assertEquals("01:11 +1", smallHours?.first?.text)
        assertEquals("01:41 +1", smallHours?.second?.text)
    }

    @Test fun agendaEndsOnTheirOwnDayCarryNoMarker() {
        var ends: Pair<AnnotatedString, AnnotatedString>? = null
        compose.setContent {
            ends = axisRangeEndsText(TimeSpan(Instant.parse("2026-09-10T21:00:00Z"), Instant.parse("2026-09-10T21:30:00Z")), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
        }
        assertEquals("21:00", ends?.first?.text)
        assertEquals("21:30", ends?.second?.text)
    }

    @Test fun agendaEndsThatCrossADayCarryDatesInsteadOfAMarker() {
        var ends: Pair<AnnotatedString, AnnotatedString>? = null
        compose.setContent {
            ends = axisRangeEndsText(TimeSpan(Instant.parse("2026-09-10T23:00:00Z"), Instant.parse("2026-09-11T01:00:00Z")), LocalDate.of(2026, 9, 10), ZoneOffset.UTC)
        }
        assertEquals("Sep 10 23:00", ends?.first?.text)
        assertEquals("Sep 11 01:00", ends?.second?.text)
    }
}
