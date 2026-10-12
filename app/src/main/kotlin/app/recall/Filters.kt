package app.recall

import java.time.LocalDate
import java.time.ZoneId

enum class Shape(val label: String) { ANY("Any shape"), PORTRAIT("Tall"), LANDSCAPE("Wide"), SQUARE("Square") }

enum class Kind(val label: String) { ANY("Everything"), CAMERA("Camera photos"), SCREENSHOTS("Screenshots") }

/**
 * What the gallery and search results are narrowed to. Dates are local days: [from] inclusive, [until] exclusive.
 * [dateLabel] says where the date came from ("2019–2021" from the slider, "December 2025" from the search text).
 */
data class Filters(
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val dateLabel: String? = null,
    val dateFromQuery: Boolean = false,
    val folder: String? = null,
    val favoritesOnly: Boolean = false,
    val shape: Shape = Shape.ANY,
    val kind: Kind = Kind.ANY,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val fromMs: Long? get() = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    private val untilMs: Long? get() = until?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    val activeCount: Int
        get() = listOf(from != null, folder != null, favoritesOnly, shape != Shape.ANY, kind != Kind.ANY).count { it }

    fun matcher(): (Photo) -> Boolean {
        val f = fromMs; val u = untilMs
        return { p ->
            (f == null || p.dateTaken >= f) && (u == null || p.dateTaken < u) &&
                (folder == null || p.bucketId == folder) &&
                (!favoritesOnly || p.favorite) &&
                when (shape) {
                    Shape.ANY -> true
                    Shape.PORTRAIT -> p.height > p.width * 1.1
                    Shape.LANDSCAPE -> p.width > p.height * 1.1
                    Shape.SQUARE -> p.width > 0 && p.height in (p.width * 0.9).toInt()..(p.width * 1.1).toInt()
                } &&
                when (kind) {
                    Kind.ANY -> true
                    Kind.SCREENSHOTS -> p.isScreenshot
                    Kind.CAMERA -> p.bucket.equals("Camera", ignoreCase = true) || p.bucket.equals("DCIM", ignoreCase = true)
                }
        }
    }

    fun withYears(fromYear: Int, toYear: Int, minYear: Int, maxYear: Int): Filters =
        if (fromYear <= minYear && toYear >= maxYear) copy(from = null, until = null, dateLabel = null, dateFromQuery = false)
        else copy(
            from = LocalDate.of(fromYear, 1, 1),
            until = LocalDate.of(toYear + 1, 1, 1),
            dateLabel = if (fromYear == toYear) "$fromYear" else "$fromYear–$toYear",
            dateFromQuery = false,
        )

    fun clearDate() = copy(from = null, until = null, dateLabel = null, dateFromQuery = false)
}
