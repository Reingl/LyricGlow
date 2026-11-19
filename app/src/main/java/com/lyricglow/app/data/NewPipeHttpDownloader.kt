package com.lyricglow.app.data

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Android/JVM transport for NewPipeExtractor. */
class NewPipeHttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            request.headers().forEach { (key, values) -> setRequestProperty(key, values.joinToString(",")) }
            request.dataToSend()?.let {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { output -> output.write(it) }
            }
        }
        try {
            val code = connection.responseCode
            val body = runCatching {
                (if (code >= 400) connection.errorStream else connection.inputStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            val headers = connection.headerFields.filterKeys { it != null }
                .mapKeys { it.key!! }.mapValues { it.value ?: emptyList() }
            return Response(code, connection.responseMessage ?: "", headers, body, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    }
}
