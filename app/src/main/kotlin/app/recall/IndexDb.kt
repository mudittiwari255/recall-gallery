package app.recall

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.recall.core.VectorIndex
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stores one embedding per photo, keyed by its MediaStore id, plus an in-memory index for search. */
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
        loadedIndex()?.put(id, vec)
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            gone.forEach { db.delete("photos", "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadedIndex()?.let { idx -> gone.forEach(idx::remove) }
    }

    fun clear() {
        writableDatabase.delete("photos", null, null)
        loadedIndex()?.clear()
    }

    fun indexedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM photos WHERE failed = 0", null).use { c -> c.moveToFirst(); c.getInt(0) }

    fun indexedIds(): Set<Long> {
        val out = HashSet<Long>()
        readableDatabase.rawQuery("SELECT id FROM photos WHERE failed = 0", null).use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        return out
    }

    @Volatile private var index: VectorIndex? = null
    private fun loadedIndex(): VectorIndex? = index

    /** The search index, loaded from the database on first use (about a second for 20,000 photos). */
    fun index(): VectorIndex {
        index?.let { return it }
        synchronized(this) {
            index?.let { return it }
            val idx = VectorIndex()
            readableDatabase.rawQuery("SELECT id, vec FROM photos WHERE failed = 0 AND vec IS NOT NULL", null).use { c ->
                while (c.moveToNext()) {
                    val fb = ByteBuffer.wrap(c.getBlob(1)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    idx.put(c.getLong(0), FloatArray(fb.remaining()).also { fb.get(it) })
                }
            }
            index = idx
            return idx
        }
    }
}
