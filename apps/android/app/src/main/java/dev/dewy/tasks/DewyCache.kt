package dev.dewy.tasks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.dewy.net.Box
import dev.dewy.net.Entry
import dev.dewy.net.Task
import dev.dewy.net.wireJson
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Where the event stream left off: its position, valid for one orchestrator start. */
data class StreamPosition(val seq: Long, val epoch: Long)

/** A thread as the cache holds it; stale when the stream missed changes to it. */
class CachedThread(val entries: List<Entry>, val stale: Boolean)

/**
 * The board and the threads the app has loaded, kept on the phone so the
 * task list and a thread show before the orchestrator answers
 * (docs/adr/0002-thread-cache-and-gzip.md). Entries are stored deflated, and
 * past [limit] bytes of them the threads opened longest ago go. Calls block;
 * callers run them off the main thread, one at a time.
 */
class DewyCache(context: Context, private val limit: Long = 50L shl 20) :
    SQLiteOpenHelper(context, "dewy-cache.db", null, 2) {
    private var writesSinceTrim = 0

    // A replay writes thousands of small changes; WAL without a sync per
    // commit keeps that fast, and a crash loses at most the last few, which
    // the next replay brings back.
    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE threads (task TEXT PRIMARY KEY, stale INTEGER NOT NULL, opened INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE entries (task TEXT NOT NULL, pos INTEGER NOT NULL, id TEXT NOT NULL, data BLOB NOT NULL, " +
                "size INTEGER NOT NULL, PRIMARY KEY (task, id))",
        )
        db.execSQL("CREATE INDEX entries_by_pos ON entries (task, pos)")
    }

    // A cache: an older layout is dropped rather than migrated.
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS meta")
        db.execSQL("DROP TABLE IF EXISTS threads")
        db.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    fun boxes(): List<Box>? = meta("boxes")?.let { wireJson.decodeFromString(ListSerializer(Box.serializer()), it) }

    fun tasks(): List<Task>? = meta("tasks")?.let { wireJson.decodeFromString(ListSerializer(Task.serializer()), it) }

    fun saveBoard(boxes: List<Box>, tasks: List<Task>) = writableDatabase.transaction {
        setMeta(this, "boxes", wireJson.encodeToString(ListSerializer(Box.serializer()), boxes))
        setMeta(this, "tasks", wireJson.encodeToString(ListSerializer(Task.serializer()), tasks))
    }

    fun position(): StreamPosition? {
        val seq = meta("seq")?.toLongOrNull() ?: return null
        val epoch = meta("epoch")?.toLongOrNull() ?: return null
        return StreamPosition(seq, epoch)
    }

    fun savePosition(p: StreamPosition) = writableDatabase.transaction {
        setMeta(this, "seq", p.seq.toString())
        setMeta(this, "epoch", p.epoch.toString())
    }

    fun forgetPosition() = writableDatabase.delete("meta", "key IN ('seq', 'epoch')", null)

    fun thread(task: String): CachedThread? {
        val db = readableDatabase
        val stale = db.rawQuery("SELECT stale FROM threads WHERE task = ?", arrayOf(task)).use { c ->
            if (!c.moveToFirst()) return null
            c.getInt(0) != 0
        }
        val entries = db.rawQuery("SELECT data FROM entries WHERE task = ? ORDER BY pos", arrayOf(task)).use { c ->
            buildList { while (c.moveToNext()) add(inflateEntry(c.getBlob(0))) }
        }
        writableDatabase.execSQL("UPDATE threads SET opened = ? WHERE task = ?", arrayOf<Any>(System.currentTimeMillis(), task))
        return CachedThread(entries, stale)
    }

    fun saveThread(task: String, entries: List<Entry>) {
        writableDatabase.transaction {
            delete("entries", "task = ?", arrayOf(task))
            entries.forEachIndexed { i, e -> insertEntry(this, task, i.toLong(), e) }
            insertWithOnConflict(
                "threads",
                null,
                ContentValues().apply {
                    put("task", task)
                    put("stale", 0)
                    put("opened", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        trim(keep = task)
    }

    /** Adds an entry to a cached thread, or merges it into the one with its id. */
    fun putEntry(task: String, entry: Entry) {
        writeEntry(task, entry)
        // Streamed entries grow threads slowly; checking the size now and then is enough.
        if (++writesSinceTrim >= 200) trim(keep = task)
    }

    private fun writeEntry(task: String, entry: Entry) = writableDatabase.transaction {
        val cached = rawQuery("SELECT 1 FROM threads WHERE task = ?", arrayOf(task)).use { it.moveToFirst() }
        if (!cached) return@transaction
        val old = rawQuery("SELECT pos, data FROM entries WHERE task = ? AND id = ?", arrayOf(task, entry.id)).use { c ->
            if (c.moveToFirst()) c.getLong(0) to inflateEntry(c.getBlob(1)) else null
        }
        if (old != null) {
            insertEntry(this, task, old.first, old.second.mergedWith(entry))
        } else {
            val next = rawQuery("SELECT COALESCE(MAX(pos) + 1, 0) FROM entries WHERE task = ?", arrayOf(task)).use {
                it.moveToFirst()
                it.getLong(0)
            }
            insertEntry(this, task, next, entry)
        }
    }

    /** Drops the threads opened longest ago until the entries fit in [limit], never [keep]. */
    fun trim(keep: String? = null) {
        writesSinceTrim = 0
        val db = writableDatabase
        var total = db.rawQuery("SELECT COALESCE(SUM(size), 0) FROM entries", null).use {
            it.moveToFirst()
            it.getLong(0)
        }
        if (total <= limit) return
        val oldest = db.rawQuery(
            "SELECT t.task, COALESCE(SUM(e.size), 0) FROM threads t LEFT JOIN entries e ON e.task = t.task " +
                "GROUP BY t.task ORDER BY t.opened",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }
        db.transaction {
            for ((task, size) in oldest) {
                if (total <= limit) break
                if (task == keep) continue
                delete("entries", "task = ?", arrayOf(task))
                delete("threads", "task = ?", arrayOf(task))
                total -= size
            }
        }
    }

    fun markAllStale() = writableDatabase.execSQL("UPDATE threads SET stale = 1")

    fun clear() = writableDatabase.transaction {
        delete("meta", null, null)
        delete("threads", null, null)
        delete("entries", null, null)
    }

    private fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun setMeta(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "meta",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun insertEntry(db: SQLiteDatabase, task: String, pos: Long, e: Entry) {
        val data = deflateEntry(e)
        db.insertWithOnConflict(
            "entries",
            null,
            ContentValues().apply {
                put("task", task)
                put("pos", pos)
                put("id", e.id)
                put("data", data)
                put("size", data.size)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private inline fun SQLiteDatabase.transaction(body: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            body()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
}

// Raw deflate: the rows are small, so a zlib or gzip header would be a
// noticeable share of each.
fun deflateEntry(e: Entry): ByteArray {
    val deflater = Deflater(6, true)
    try {
        deflater.setInput(wireJson.encodeToString(Entry.serializer(), e).encodeToByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}

fun inflateEntry(data: ByteArray): Entry {
    val inflater = Inflater(true)
    try {
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            // Raw inflate can want one more input byte before it reports the end.
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        return wireJson.decodeFromString(Entry.serializer(), out.toString(Charsets.UTF_8))
    } finally {
        inflater.end()
    }
}
