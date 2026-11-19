package com.lyricglow.app.auth

import android.content.Context
import com.lyricglow.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** LyricGlow account auth. This is separate from Google/YouTube account authentication. */
class SupabaseAuthRepository(context: Context) {
    private val secure = SecureTokenStore(context)
    val isSignedIn: Boolean get() = !secure.get("access_token").isNullOrBlank()
    val email: String? get() = secure.get("email")

    fun signIn(email: String, password: CharArray): Result<Unit> = runCatching {
        val payload = JSONObject().put("email", email.trim()).put("password", String(password))
        val response = request("/auth/v1/token?grant_type=password", payload)
        saveSession(response, email.trim())
    }.also { password.fill('\u0000') }

    fun signUp(email: String, password: CharArray): Result<Boolean> = runCatching {
        val payload = JSONObject().put("email", email.trim()).put("password", String(password))
        val response = request("/auth/v1/signup", payload)
        if (response.has("access_token")) {
            saveSession(response, email.trim()); true
        } else false // Email confirmation may be required by the Supabase project.
    }.also { password.fill('\u0000') }

    fun signOut() {
        val token = secure.get("access_token")
        if (!token.isNullOrBlank()) runCatching {
            request("/auth/v1/logout", JSONObject(), token)
        }
        secure.clear()
    }

    @Synchronized fun accessToken(): String? {
        val current = secure.get("access_token") ?: return null
        val expiry = secure.get("expires_at")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() < expiry - 60_000L) return current
        val refresh = secure.get("refresh_token") ?: return current
        return runCatching {
            val response = request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
            saveSession(response, email.orEmpty())
            secure.get("access_token")
        }.getOrNull() ?: current
    }

    private fun saveSession(json: JSONObject, email: String) {
        val access = json.optString("access_token")
        require(access.isNotBlank()) { json.optString("msg", "Authentication did not return a session.") }
        secure.put("access_token", access)
        secure.put("refresh_token", json.optString("refresh_token"))
        secure.put("email", email)
        secure.put("expires_at", (System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L).toString())
    }

    private fun request(path: String, body: JSONObject, bearer: String? = null): JSONObject {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) throw IllegalStateException(
                json.optString("msg").ifBlank { json.optString("message", "Account request failed ($code).") }
            )
            return json
        } finally { connection.disconnect() }
    }
}
