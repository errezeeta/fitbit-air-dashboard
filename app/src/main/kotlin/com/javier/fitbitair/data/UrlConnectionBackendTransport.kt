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
    }
}
