package com.lyricglow.app.auth

import android.content.Context
import com.lyricglow.app.BuildConfig
import com.lyricglow.app.data.MusicMetadataCache
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional cloud backup for a signed-in LyricGlow account. */
class SupabaseLibrarySync(context: Context) {
    private val auth = SupabaseAuthRepository(context)
    private val cache = MusicMetadataCache(context.applicationContext)

    fun syncSavedTracks(): Result<Int> = runCatching {
        val token = auth.accessToken() ?: error("Sign in to sync your library.")
        val user = request("/auth/v1/user", "GET", token)
        val userId = user.optString("id").takeIf { it.isNotBlank() } ?: error("Could not identify the signed-in account.")
        val local = cache.saved()
        if (local.isNotEmpty()) {
            val rows = JSONArray()
            local.forEach { track -> rows.put(JSONObject()
                .put("user_id", userId).put("provider", "youtube_music").put("provider_id", track.id)
                .put("title", track.title).put("artist", track.artist).put("artwork_url", track.artworkUrl)
                .put("page_url", track.pageUrl)) }
            request("/rest/v1/saved_tracks?on_conflict=user_id,provider,provider_id", "POST", token, rows.toString(),
                "resolution=merge-duplicates,return=minimal")
        }
        local.size
    }

    private fun request(path: String, method: String, token: String, body: String? = null, prefer: String? = null): JSONObject {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); body.let { outputStream.use { stream -> stream.write(it.toByteArray()) } } }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Cloud sync failed ($code): ${response.take(180)}")
            return if (response.startsWith("{")) JSONObject(response) else JSONObject()
        } finally { connection.disconnect() }
    }
}
