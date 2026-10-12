package app.recall

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

class RecallApp : Application() {
    val models by lazy { ModelManager(this) }
    val db by lazy { IndexDb(this) }
    val media by lazy { MediaRepo(contentResolver) }
    val settings by lazy { Settings(this) }

    /** Indexing pauses until this time, so a search runs at full speed. */
    @Volatile var searchBusyUntil: Long = 0L

    /** Android's heat level (PowerManager.THERMAL_STATUS_*); 2+ means the phone is being slowed down. */
    fun thermalStatus(): Int =
        runCatching { getSystemService(android.os.PowerManager::class.java).currentThermalStatus }.getOrDefault(0)

    /** Full or partial (Android 14 "selected photos") access both count. */
    fun hasPhotoPermission(): Boolean = photoPermissions().any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFullPhotoAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** The photos the user chose to index, in the order they should be indexed. */
    fun indexSelection(all: List<Photo>): List<Photo> {
        val s = settings
        val folders = s.folders
        val since = s.dateRange.since()
        val picked = all.filter { (folders == null || it.bucketId in folders) && it.dateTaken >= since }
        return if (s.oldestFirst) picked.sortedBy { it.dateTaken } else picked.sortedByDescending { it.dateTaken }
    }

    companion object {
        fun photoPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        /**
         * Number of fast cores. Phones mix big and little cores; spreading inference across little cores makes every
         * step wait for the slowest one, so we use only cores within 70% of the top clock speed.
         */
        fun performanceCores(): Int {
            val total = Runtime.getRuntime().availableProcessors()
            val freqs = (0 until total).mapNotNull { cpu ->
                runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
            }
            if (freqs.isEmpty()) return (total / 2).coerceIn(2, 4)
            val max = freqs.max()
            return freqs.count { it >= max * 0.7 }.coerceIn(2, 8)
        }
    }
}

enum class DateRange(val label: String, val years: Int) {
    ALL("All time", 0), Y1("1 year", 1), Y3("3 years", 3), Y5("5 years", 5);

    fun since(): Long = if (years == 0) 0L else System.currentTimeMillis() - years * 365L * 24 * 3600 * 1000
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Vision token budget per photo: 70 fast, 140 balanced, 280 detailed. */
    var photoDetail: Int
        get() = prefs.getInt("photo_detail", 70)
        set(value) = prefs.edit().putInt("photo_detail", value).apply()

    /** Folder (bucket) ids to index, or null for every folder. */
    var folders: Set<String>?
        get() = if (prefs.getBoolean("all_folders", true)) null else prefs.getStringSet("folders", emptySet())!!.toSet()
        set(value) = prefs.edit().putBoolean("all_folders", value == null).putStringSet("folders", value ?: emptySet()).apply()

    var dateRange: DateRange
        get() = runCatching { DateRange.valueOf(prefs.getString("date_range", "ALL")!!) }.getOrDefault(DateRange.ALL)
        set(value) = prefs.edit().putString("date_range", value.name).apply()

    var oldestFirst: Boolean
        get() = prefs.getBoolean("oldest_first", false)
        set(value) = prefs.edit().putBoolean("oldest_first", value).apply()

    /** Set once the periodic job has been switched to the single-runner version. */
    var periodicV2: Boolean
        get() = prefs.getBoolean("periodic_v2", false)
        set(value) = prefs.edit().putBoolean("periodic_v2", value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    /** Only index while the phone is charging (saves battery; slower to finish). */
    var chargingOnly: Boolean
        get() = prefs.getBoolean("charging_only", false)
        set(value) = prefs.edit().putBoolean("charging_only", value).apply()

    /** Most recent first, at most 8. */
    var recentSearches: List<String>
        get() = prefs.getString("recent", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("recent", value.take(8).joinToString("\n")).apply()

    fun addRecent(q: String) {
        recentSearches = listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }
    }
}
