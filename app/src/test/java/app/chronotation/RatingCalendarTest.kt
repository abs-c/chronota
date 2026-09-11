package app.chronotation

import app.chronotation.domain.*
import app.chronotation.ui.components.monthCells
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RatingCalendarTest {
    @Test fun percentagePreservesOldRatingsAndEveryIntegerScore() {
        assertEquals(90, ratingScore("4.5"))
        for (score in 0..100) {
            assertTrue(validRating(ratingValue(score)))
            assertEquals(score, ratingScore(ratingValue(score)))
        }
        assertEquals(4.5f, ratingStars(90))
        assertEquals(4f, ratingStars(84))
        assertEquals(4.5f, ratingStars(85))
        assertEquals(5f, ratingStars(100))
        listOf("-0.05", "5.05", "4.222", "NaN").forEach { assertFalse(validRating(it)) }
    }
    @Test fun monthGridHasSevenColumnsAndCorrectWeekdaysIncludingLeapDay() {
        for (year in listOf(2024, 2025, 2026)) for (month in 1..12) {
            val value = YearMonth.of(year, month)
            val cells = monthCells(value)
            assertEquals(0, cells.size % 7)
            assertEquals(value.lengthOfMonth(), cells.filterNotNull().size)
            cells.forEachIndexed { index, day -> if (day != null) assertEquals(day.dayOfWeek.value - 1, index % 7) }
        }
        assertTrue(LocalDate.of(2024, 2, 29) in monthCells(YearMonth.of(2024, 2)))
    }
}
