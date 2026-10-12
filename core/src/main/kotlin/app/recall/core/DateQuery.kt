package app.recall.core

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pulls a date range out of a search like "beach 2023", "receipts last month" or "december photos", so the
 * date becomes a filter and the rest ("beach", "receipts") goes to the image search.
 */
object DateQuery {
    /** [from] inclusive, [until] exclusive. [text] is the query with the date words removed (may be empty). */
    data class Parsed(val text: String, val from: LocalDate?, val until: LocalDate?, val label: String?) {
        val hasDate get() = from != null
    }

    private val months: Map<String, Int> = buildMap {
        for (m in 1..12) {
            val month = java.time.Month.of(m)
            put(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(), m)
            put(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(), m)
        }
        put("sept", 9)
    }
    private val monthAlt = months.keys.sortedByDescending { it.length }.joinToString("|")
    private const val PREP = "(?:(?:in|from|during|of|on|taken|since)\\s+)?"
    private const val UNIT = "(day|days|week|weeks|month|months|year|years)"

    private fun label(from: LocalDate, until: LocalDate): String {
        val last = until.minusDays(1)
        fun monthName(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return when {
            from == last -> "${from.dayOfMonth} ${monthName(from)} ${from.year}"
            from.dayOfMonth == 1 && until == from.plusMonths(1) -> "${monthName(from)} ${from.year}"
            from.dayOfYear == 1 && until == from.plusYears(1) -> "${from.year}"
            from.dayOfYear == 1 && until.dayOfYear == 1 -> "${from.year}–${last.year}"
            else -> "${from.dayOfMonth} ${monthName(from).take(3)} ${from.year} – ${last.dayOfMonth} ${monthName(last).take(3)} ${last.year}"
        }
    }

    fun parse(query: String, today: LocalDate = LocalDate.now()): Parsed {
        val q = query.trim()
        val lower = q.lowercase(Locale.ENGLISH)
        val maxYear = today.year
        fun validYear(y: Int) = y in 1900..maxYear

        data class Hit(val range: IntRange, val from: LocalDate, val until: LocalDate)
        fun rule(re: Regex, f: (MatchResult) -> Pair<LocalDate, LocalDate>?) = re to f

        val rules: List<Pair<Regex, (MatchResult) -> Pair<LocalDate, LocalDate>?>> = listOf(
            // 2019 to 2021, 2019-2021, between 2019 and 2021
            rule(Regex("\\b(?:between\\s+|from\\s+)?(\\d{4})\\s*(?:-|–|to|and|till|until)\\s*(\\d{4})\\b")) { m ->
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                if (validYear(a) && validYear(b)) LocalDate.of(minOf(a, b), 1, 1) to LocalDate.of(maxOf(a, b) + 1, 1, 1) else null
            },
            // december 2023, dec 2023
            rule(Regex("\\b$PREP($monthAlt)\\.?\\s+(\\d{4})\\b")) { m ->
                val y = m.groupValues[2].toInt()
                if (validYear(y)) LocalDate.of(y, months[m.groupValues[1]]!!, 1).let { it to it.plusMonths(1) } else null
            },
            // last 3 months, past 2 years
            rule(Regex("\\b(?:in\\s+the\\s+)?(?:last|past)\\s+(\\d{1,3})\\s+$UNIT\\b")) { m ->
                val n = m.groupValues[1].toLong()
                val from = when (m.groupValues[2].trimEnd('s')) {
                    "day" -> today.minusDays(n)
                    "week" -> today.minusWeeks(n)
                    "month" -> today.minusMonths(n)
                    else -> today.minusYears(n)
                }
                from to today.plusDays(1)
            },
            rule(Regex("\\btoday\\b")) { _ -> today to today.plusDays(1) },
            rule(Regex("\\byesterday\\b")) { _ -> today.minusDays(1) to today },
            rule(Regex("\\bthis\\s+week\\b")) { _ ->
                val start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                start to today.plusDays(1)
            },
            rule(Regex("\\blast\\s+week\\b")) { _ ->
                val start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1)
                start to start.plusWeeks(1)
            },
            rule(Regex("\\bthis\\s+month\\b")) { _ -> today.withDayOfMonth(1) to today.plusDays(1) },
            rule(Regex("\\blast\\s+month\\b")) { _ -> today.withDayOfMonth(1).minusMonths(1).let { it to it.plusMonths(1) } },
            rule(Regex("\\bthis\\s+year\\b")) { _ -> today.withDayOfYear(1) to today.plusDays(1) },
            rule(Regex("\\blast\\s+year\\b")) { _ -> today.withDayOfYear(1).minusYears(1).let { it to it.plusYears(1) } },
            // december, in march: the most recent one that has started
            rule(Regex("\\b$PREP($monthAlt)\\b")) { m ->
                val month = months[m.groupValues[1]]!!
                // "may" and "mar" are also ordinary words; only treat them as months next to a preposition
                val word = m.groupValues[1]
                if ((word == "may" || word == "mar") && !m.value.trim().contains(' ')) null
                else {
                    val year = if (month <= today.monthValue) today.year else today.year - 1
                    LocalDate.of(year, month, 1).let { it to it.plusMonths(1) }
                }
            },
            // 2023, in 2023
            rule(Regex("\\b$PREP(\\d{4})\\b")) { m ->
                val y = m.groupValues[1].toInt()
                if (validYear(y)) LocalDate.of(y, 1, 1) to LocalDate.of(y + 1, 1, 1) else null
            },
        )

        var found: Hit? = null
        for ((re, toRange) in rules) {
            val m = re.find(lower) ?: continue
            val r = toRange(m) ?: continue
            found = Hit(m.range, r.first, r.second)
            break
        }
        if (found == null) return Parsed(q, null, null, null)

        val rest = (q.substring(0, found.range.first) + " " + q.substring(found.range.last + 1))
            .replace(Regex("\\s+"), " ").trim().trim(',', '-', ' ')
        // "photos from 2023" means everything from 2023: drop leftovers that are only filler words
        val leftover = rest.lowercase(Locale.ENGLISH).split(Regex("\\W+")).filter { it.isNotEmpty() }
        val text = if (leftover.all { it in filler }) "" else rest
        val until = if (found.until.isAfter(today.plusDays(1))) today.plusDays(1) else found.until
        return Parsed(text, found.from, until.coerceAtLeast(found.from.plusDays(1)), label(found.from, found.until))
    }

    private val filler = setOf(
        "photo", "photos", "picture", "pictures", "pics", "image", "images", "my", "all", "show", "me",
        "in", "from", "during", "of", "on", "taken", "since", "the", "and",
    )
}

private fun LocalDate.coerceAtLeast(other: LocalDate): LocalDate = if (isBefore(other)) other else this
