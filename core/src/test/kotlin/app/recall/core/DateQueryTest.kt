package app.recall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class DateQueryTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun p(q: String) = DateQuery.parse(q, today)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun check(q: String, text: String, from: LocalDate, until: LocalDate, label: String? = null) {
        val r = p(q)
        assertEquals("text for '$q'", text, r.text)
        assertEquals("from for '$q'", from, r.from)
        assertEquals("until for '$q'", until, r.until)
        if (label != null) assertEquals("label for '$q'", label, r.label)
    }

    @Test fun year() = check("beach 2023", "beach", d(2023, 1, 1), d(2024, 1, 1), "2023")
    @Test fun inYear() = check("Goa trip in 2022", "Goa trip", d(2022, 1, 1), d(2023, 1, 1))
    @Test fun lastMonth() = check("receipts last month", "receipts", d(2026, 9, 1), d(2026, 10, 1), "September 2026")
    @Test fun bareMonthIsMostRecent() = check("december photos", "", d(2025, 12, 1), d(2026, 1, 1), "December 2025")
    @Test fun monthThisYear() = check("rain in july", "rain", d(2026, 7, 1), d(2026, 8, 1))
    @Test fun monthYear() = check("dog in march 2024", "dog", d(2024, 3, 1), d(2024, 4, 1), "March 2024")
    @Test fun shortMonthYear() = check("cake dec 2019", "cake", d(2019, 12, 1), d(2020, 1, 1))
    @Test fun yearRange() = check("photos from 2019 to 2021", "", d(2019, 1, 1), d(2022, 1, 1), "2019–2021")
    @Test fun yesterday() = check("cake yesterday", "cake", d(2026, 10, 11), d(2026, 10, 12), "11 October 2026")
    @Test fun lastNMonths() = check("food last 3 months", "food", d(2026, 7, 12), d(2026, 10, 13))
    @Test fun lastYear() = check("diwali last year", "diwali", d(2025, 1, 1), d(2026, 1, 1), "2025")
    @Test fun thisYearIsClampedToToday() = check("this year", "", d(2026, 1, 1), d(2026, 10, 13))

    @Test fun noDate() {
        val r = p("wedding stage decoration")
        assertFalse(r.hasDate)
        assertEquals("wedding stage decoration", r.text)
    }

    @Test fun mayIsAWordUnlessItLooksLikeAMonth() {
        assertFalse(p("may flowers").hasDate)
        assertEquals(d(2026, 5, 1), p("trek in may").from)
    }

    @Test fun futureAndTinyNumbersAreNotYears() {
        assertFalse(p("room 2045").hasDate)
        assertFalse(p("bill 1234").hasDate)
    }
}
