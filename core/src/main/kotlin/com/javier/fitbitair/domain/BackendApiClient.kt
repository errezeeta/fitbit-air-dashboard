package com.javier.fitbitair.domain

import com.google.gson.JsonParser
import com.google.gson.Gson
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface BackendTransport {
    fun get(path: String, bearerToken: String? = null): BackendHttpResult
    fun post(path: String, body: String, bearerToken: String? = null): BackendHttpResult

    /** Streams an event stream, handing over each event's data as it arrives. */
    fun postEvents(
        path: String,
        body: String,
        bearerToken: String? = null,
        onEvent: (String) -> Unit,
    )
}

data class BackendHttpResult(val statusCode: Int, val body: String)

data class BackendServiceStatus(
    val googleOAuthConfigured: Boolean,
    val healthConfigured: Boolean,
    val assistantConfigured: Boolean,
)

class BackendApiClient(
    baseUrl: String,
    private val credentials: SessionCredentialStore,
    private val transport: BackendTransport,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val gson = Gson()

    init {
        require(URI(this.baseUrl).scheme == "https") { "Backend must use HTTPS" }
    }

    fun beginGoogleAuthorization(): String {
        val appState = randomUrlSafe(24)
        val verifier = randomUrlSafe(32)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        val path = "/auth/google/start?app_state=${encode(appState)}&code_challenge=${encode(challenge)}"
        val response = transport.get(path)
        val json = successfulJson(response, "Could not start Google authorization")
        val authorizationUrl = json.stringValue("authorizationUrl")
        if (!authorizationUrl.startsWith("https://accounts.google.com/")) {
            throw BackendApiException("Backend returned an invalid Google authorization URL")
        }
        if (json.stringValue("state").isBlank()) {
            throw BackendApiException("Backend returned invalid OAuth state")
        }
        credentials.savePendingOAuth(appState, verifier)
        return authorizationUrl
    }

    fun loadServiceStatus(): BackendServiceStatus {
        val json = successfulJson(
            transport.get("/api/status"),
            "Could not read backend status",
        )
        return BackendServiceStatus(
            googleOAuthConfigured = json.booleanValue("googleOAuthConfigured"),
            healthConfigured = json.booleanValue("healthConfigured"),
            assistantConfigured = json.booleanValue("assistantConfigured"),
        )
    }

    fun completeGoogleAuthorization(callbackUri: String): String {
        val uri = runCatching { URI(callbackUri) }
            .getOrElse { throw BackendApiException("OAuth return link is invalid") }
        if (uri.scheme != "fitbitair" || uri.host != "oauth" || uri.path != "/callback") {
            throw BackendApiException("OAuth return link is not for Fitbit Air")
        }
        val query = parseQuery(uri.rawQuery)
        if (query.containsKey("error")) {
            throw BackendApiException("Google authorization was cancelled")
        }
        val handoff = query["handoff"]?.takeIf(String::isNotBlank)
            ?: throw BackendApiException("OAuth return link is incomplete")
        val appState = query["app_state"]?.takeIf(String::isNotBlank)
            ?: throw BackendApiException("OAuth return link is incomplete")
        val pending = credentials.consumePendingOAuth(appState)
            ?: throw BackendApiException("OAuth return state did not match this app")
        val body = gson.toJson(
            mapOf(
                "handoff" to handoff,
                "app_state" to pending.appState,
                "code_verifier" to pending.codeVerifier,
            ),
        )
        val response = transport.post("/auth/google/exchange", body)
        val json = successfulJson(response, "Could not complete Google authorization")
        val sessionToken = json.stringValue("sessionToken")
            .takeIf(String::isNotBlank)
            ?: throw BackendApiException("Backend returned an invalid app session")
        val email = json.stringValue("email")
        credentials.saveSessionToken(sessionToken)
        return email
    }

    fun loadDashboard(range: TrendRange, timezone: String): DashboardState {
        val session = credentials.loadSessionToken() ?: return DashboardState.Empty
        val rangeName = if (range == TrendRange.WEEK) "week" else "month"
        val path = "/api/dashboard?range=$rangeName&timezone=${encode(timezone)}"
        val response = transport.get(path, bearerToken = session)
        if (response.statusCode == 401) {
            credentials.clearSession()
            return DashboardState.Error("Tu sesión caducó; vuelve a conectar Google Health.")
        }
        if (response.statusCode !in 200..299) {
            return DashboardState.Error("No se pudieron consultar los datos de salud.")
        }
        return BackendDashboardMapper.parse(response.body, range)
    }

    fun askAssistant(question: String, range: TrendRange, timezone: String): AssistantAnswer {
        val session = credentials.loadSessionToken()
            ?: throw BackendApiException("Conecta Google Health antes de usar el asistente")
        val rangeName = if (range == TrendRange.WEEK) "week" else "month"
        val body = gson.toJson(
            mapOf(
                "question" to question,
                "range" to rangeName,
                "timezone" to timezone,
            ),
        )
        val response = transport.post("/api/assistant", body, bearerToken = session)
        val json = successfulJson(response, "No se pudo consultar al asistente")
        val answer = AssistantAnswerParser.parse(json)
        return answer.takeIf { it.text.isNotBlank() }
            ?: throw BackendApiException("El asistente devolvió una respuesta vacía")
    }

    /** Streams the answer: stages, text deltas and the final payload. */
    fun askAssistantStreaming(
        question: String,
        range: TrendRange,
        timezone: String,
        onEvent: (AssistantStreamEvent) -> Unit,
    ) {
        val session = credentials.loadSessionToken()
            ?: throw BackendApiException("Conecta Google Health antes de usar el asistente")
        val rangeName = if (range == TrendRange.WEEK) "week" else "month"
        val body = gson.toJson(
            mapOf(
                "question" to question,
                "range" to rangeName,
                "timezone" to timezone,
            ),
        )
        transport.postEvents("/api/assistant/stream", body, session) { line ->
            AssistantStreamParser.parse(line)?.let(onEvent)
        }
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun successfulJson(response: BackendHttpResult, errorMessage: String) =
        if (response.statusCode in 200..299) {
            runCatching { JsonParser.parseString(response.body).asJsonObject }
                .getOrElse { throw BackendApiException(errorMessage, it) }
        } else {
            throw BackendApiException(errorMessage)
        }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
        }.toMap()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun com.google.gson.JsonObject.booleanValue(name: String): Boolean =
        has(name) && !get(name).isJsonNull && runCatching { get(name).asBoolean }.getOrDefault(false)

    private fun com.google.gson.JsonObject.stringValue(name: String): String =
        if (has(name) && !get(name).isJsonNull) runCatching { get(name).asString }.getOrDefault("") else ""
}

class BackendApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
