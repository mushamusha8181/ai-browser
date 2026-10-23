package jp.mushamusha.aibrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BrowserEntry(val id: Long, val title: String, val url: String, val timestamp: Long)
data class WatchEntry(
    val id: Long,
    val title: String,
    val url: String,
    val lastHash: String,
    val lastSnippet: String,
    val enabled: Boolean,
    val timestamp: Long
)

class BrowserDatabase(context: Context) : SQLiteOpenHelper(context, "browser.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_history_ts ON history(ts DESC)")
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE watches(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, last_hash TEXT NOT NULL DEFAULT '', last_snippet TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS watches(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, last_hash TEXT NOT NULL DEFAULT '', last_snippet TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, ts INTEGER NOT NULL)")
        }
    }

    fun addHistory(title: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (AiPrivacy.containsSensitiveUrlData(url)) return
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            var merged = false
            db.rawQuery("SELECT id,url,ts FROM history ORDER BY ts DESC LIMIT 1", null).use { c ->
                if (c.moveToFirst()) {
                    val lastId = c.getLong(0)
                    val lastUrl = c.getString(1)
                    val lastTs = c.getLong(2)
                    if (lastUrl == url && now - lastTs < 15_000L) {
                        db.update("history", ContentValues().apply {
                            put("title", title.ifBlank { url })
                            put("ts", now)
                        }, "id=?", arrayOf(lastId.toString()))
                        merged = true
                    }
                }
            }
            if (!merged) {
                db.insert("history", null, ContentValues().apply {
                    put("title", title.ifBlank { url })
                    put("url", url)
                    put("ts", now)
                })
                db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY ts DESC LIMIT 5000)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank() || AiPrivacy.containsSensitiveUrlData(url)) return
        writableDatabase.insertWithOnConflict("bookmarks", null, ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
            put("ts", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeBookmark(url: String) = writableDatabase.delete("bookmarks", "url=?", arrayOf(url))

    fun isBookmarked(url: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", arrayOf(url)
    ).use { it.moveToFirst() }

    fun list(table: String, limit: Int = 1000): List<BrowserEntry> {
        require(table == "history" || table == "bookmarks")
        return readableDatabase.rawQuery(
            "SELECT id,title,url,ts FROM $table ORDER BY ts DESC LIMIT ?", arrayOf(limit.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(BrowserEntry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        }
    }

    fun clear(table: String) {
        require(table == "history" || table == "bookmarks")
        writableDatabase.delete(table, null, null)
    }

    fun addWatch(title: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (AiPrivacy.containsSensitiveUrlData(url)) return
        writableDatabase.insertWithOnConflict("watches", null, ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
            put("enabled", 1)
            put("ts", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeWatch(url: String) = writableDatabase.delete("watches", "url=?", arrayOf(url))

    fun isWatched(url: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM watches WHERE url=? AND enabled=1 LIMIT 1", arrayOf(url)
    ).use { it.moveToFirst() }

    fun listWatches(): List<WatchEntry> = readableDatabase.rawQuery(
        "SELECT id,title,url,last_hash,last_snippet,enabled,ts FROM watches ORDER BY ts DESC", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    WatchEntry(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getInt(5) != 0, c.getLong(6)
                    )
                )
            }
        }
    }

    fun updateWatchSnapshot(id: Long, hash: String, snippet: String) {
        writableDatabase.update("watches", ContentValues().apply {
            put("last_hash", hash)
            put("last_snippet", snippet.take(2000))
            put("ts", System.currentTimeMillis())
        }, "id=?", arrayOf(id.toString()))
    }
    fun purgeSensitiveUrls() {
        writableDatabase.beginTransaction()
        try {
            listOf("history", "bookmarks", "watches").forEach { table ->
                val ids = mutableListOf<Long>()
                readableDatabase.rawQuery("SELECT id,url FROM $table", null).use { c ->
                    while (c.moveToNext()) {
                        if (AiPrivacy.containsSensitiveUrlData(c.getString(1))) ids += c.getLong(0)
                    }
                }
                ids.forEach { id -> writableDatabase.delete(table, "id=?", arrayOf(id.toString())) }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

}
