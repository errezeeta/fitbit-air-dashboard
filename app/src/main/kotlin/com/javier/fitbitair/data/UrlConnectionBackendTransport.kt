package com.javier.fitbitair.data

import com.javier.fitbitair.domain.BackendHttpResult
import com.javier.fitbitair.domain.BackendTransport
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

class UrlConnectionBackendTransport(baseUrl: String) : BackendTransport {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(URI(this.baseUrl).scheme == "https") { "Backend URL must use HTTPS" }
    }

    override fun get(path: String, bearerToken: String?): BackendHttpResult =
        request(method = "GET", path = path, body = null, bearerToken = bearerToken)

    override fun post(path: String, body: String, bearerToken: String?): BackendHttpResult =
        request(method = "POST", path = path, body = body, bearerToken = bearerToken)

    /**
     * Server-sent events: the call returns once the stream ends, but every
     * `data:` line is handed over as it arrives, which is what lets the chat
     * show the answer being written.
     */
    override fun postEvents(
        path: String,
        body: String,
        bearerToken: String?,
        onEvent: (String) -> Unit,
    ) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = STREAM_READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Connection", "keep-alive")
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val detail = connection.errorStream
                    ?.bufferedReader(StandardCharsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                throw BackendStreamException(statusCode, detail)
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith(SSE_PREFIX)) onEvent(line.removePrefix(SSE_PREFIX))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?,
    ): BackendHttpResult {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Pragma", "no-cache")
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return BackendHttpResult(statusCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 25_000
        // The agent writes for as long as it needs; the backend stops it.
        const val STREAM_READ_TIMEOUT_MS = 90_000
        const val SSE_PREFIX = "data: "
    }
}

class BackendStreamException(val statusCode: Int, val detail: String) :
    IllegalStateException("Streaming request failed with HTTP $statusCode")
