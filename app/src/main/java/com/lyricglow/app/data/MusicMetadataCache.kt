package com.lyricglow.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Small bounded metadata cache. Playback URLs are intentionally excluded. */
class MusicMetadataCache(context: Context) : SQLiteOpenHelper(context, "lyricglow_cache.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE search_cache (query TEXT NOT NULL, track_id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, duration INTEGER NOT NULL, artwork TEXT, page_url TEXT NOT NULL, cached_at INTEGER NOT NULL, PRIMARY KEY(query, track_id))")
        db.execSQL("CREATE INDEX search_cache_time ON search_cache(cached_at)")
        db.execSQL("CREATE TABLE saved_tracks (track_id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, duration INTEGER NOT NULL, artwork TEXT, page_url TEXT NOT NULL, saved_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists (playlist_id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, track_id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, duration INTEGER NOT NULL, artwork TEXT, page_url TEXT NOT NULL, PRIMARY KEY(playlist_id, position))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun get(query: String): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        readableDatabase.query("search_cache", null, "query=?", arrayOf(query.lowercase()), null, null, "cached_at DESC", "30").use { c ->
            while (c.moveToNext()) result += MusicTrack(
                c.getString(c.getColumnIndexOrThrow("track_id")), c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("artist")), c.getLong(c.getColumnIndexOrThrow("duration")),
                c.getString(c.getColumnIndexOrThrow("artwork")), c.getString(c.getColumnIndexOrThrow("page_url"))
            )
        }
        return result
    }

    @Synchronized fun put(query: String, tracks: List<MusicTrack>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("search_cache", "query=?", arrayOf(query.lowercase()))
            tracks.forEach { track ->
                val values = ContentValues().apply {
                    put("query", query.lowercase()); put("track_id", track.id); put("title", track.title)
                    put("artist", track.artist); put("duration", track.durationSeconds); put("artwork", track.artworkUrl)
                    put("page_url", track.pageUrl); put("cached_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("search_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM search_cache WHERE cached_at < ?", arrayOf(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized fun save(track: MusicTrack) {
        val values = ContentValues().apply {
            put("track_id", track.id); put("title", track.title); put("artist", track.artist)
            put("duration", track.durationSeconds); put("artwork", track.artworkUrl); put("page_url", track.pageUrl)
            put("saved_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("saved_tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun saved(): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        readableDatabase.query("saved_tracks", null, null, null, null, null, "saved_at DESC").use { c ->
            while (c.moveToNext()) result += MusicTrack(
                c.getString(c.getColumnIndexOrThrow("track_id")), c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("artist")), c.getLong(c.getColumnIndexOrThrow("duration")),
                c.getString(c.getColumnIndexOrThrow("artwork")), c.getString(c.getColumnIndexOrThrow("page_url"))
            )
        }
        return result
    }
}
