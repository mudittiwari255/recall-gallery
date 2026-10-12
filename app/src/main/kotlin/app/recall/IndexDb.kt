package app.recall

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stores one embedding per photo, keyed by its MediaStore id. Vectors are cached in memory for search. */
class IndexDb(context: Context) : SQLiteOpenHelper(context, "index.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE photos (
                 id INTEGER PRIMARY KEY,
                 modified INTEGER NOT NULL,
                 budget INTEGER NOT NULL,
                 vec BLOB,
                 failed INTEGER NOT NULL DEFAULT 0
               )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** id -> modified timestamp, for every photo already processed (including failures). */
    fun processed(): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        readableDatabase.rawQuery("SELECT id, modified FROM photos", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1)
        }
        return out
    }

    fun put(id: Long, modified: Long, budget: Int, vec: FloatArray) {
        val bytes = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asFloatBuffer().put(vec)
        writableDatabase.insertWithOnConflict(
            "photos", null,
            ContentValues().apply {
                put("id", id); put("modified", modified); put("budget", budget)
                put("vec", bytes.array()); put("failed", 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        synchronized(this) { cache?.put(id, vec) }
    }

    fun markFailed(id: Long, modified: Long) {
        writableDatabase.insertWithOnConflict(
            "photos", null,
            ContentValues().apply { put("id", id); put("modified", modified); put("budget", 0); put("failed", 1) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteMissing(existing: Set<Long>) {
        val gone = processed().keys - existing
        if (gone.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            gone.forEach { writableDatabase.delete("photos", "id = ?", arrayOf(it.toString())) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        synchronized(this) { gone.forEach { cache?.remove(it) } }
    }

    fun clear() {
        writableDatabase.delete("photos", null, null)
        synchronized(this) { cache = null }
    }

    fun indexedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM photos WHERE failed = 0", null).use { c -> c.moveToFirst(); c.getInt(0) }

    private var cache: HashMap<Long, FloatArray>? = null

    /** Every stored vector, loaded once and then kept up to date in memory. */
    fun vectors(): Map<Long, FloatArray> = synchronized(this) {
        cache ?: HashMap<Long, FloatArray>().also { map ->
            readableDatabase.rawQuery("SELECT id, vec FROM photos WHERE failed = 0 AND vec IS NOT NULL", null).use { c ->
                while (c.moveToNext()) {
                    val fb = ByteBuffer.wrap(c.getBlob(1)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    map[c.getLong(0)] = FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
            cache = map
        }.let { HashMap(it) }
    }
}
