package com.javier.fitbitair.domain

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendApiClientTest {
    private class FakeCredentials : SessionCredentialStore {
        var token: String? = null
        var pending: PendingOAuth? = null

        override fun loadSessionToken(): String? = token
        override fun saveSessionToken(token: String) { this.token = token }
        override fun clearSession() { token = null }
        override fun savePendingOAuth(appState: String, codeVerifier: String) {
            pending = PendingOAuth(appState, codeVerifier)
        }
        override fun consumePendingOAuth(appState: String): PendingOAuth? {
            val saved = pending
            pending = null
            return saved?.takeIf { it.appState == appState }
        }
    }

    private class FakeTransport : BackendTransport {
        val gets = mutableListOf<Triple<String, String?, BackendHttpResult>>()
        val posts = mutableListOf<Triple<String, String?, String>>()
        val responses = ArrayDeque<BackendHttpResult>()

        override fun get(path: String, bearerToken: String?): BackendHttpResult {
            val response = responses.removeFirst()
            gets += Triple(path, bearerToken, response)
            return response
        }

        override fun post(path: String, body: String, bearerToken: String?): BackendHttpResult {
            posts += Triple(path, bearerToken, body)
            return responses.removeFirst()
        }

        val eventStreams = mutableListOf<Pair<String, List<String>>>()

        override fun postEvents(
            path: String,
            body: String,
            bearerToken: String?,
            onEvent: (String) -> Unit,
        ) {
            val lines = eventStreams.removeFirstOrNull()?.second ?: emptyList()
            eventStreamsUsed += path
            lines.forEach(onEvent)
        }

        val eventStreamsUsed = mutableListOf<String>()
    }

    @Test
    fun oauthHandoffStoresOnlyAppSessionAndLoadsDashboardWithIt() {
        val credentials = FakeCredentials()
        val transport = FakeTransport().apply {
            responses += BackendHttpResult(
                200,
                """{"authorizationUrl":"https://accounts.google.com/auth?client_id=public","state":"signed-server-state"}""",
            )
            responses += BackendHttpResult(
                200,
                """{"sessionToken":"app-session-jwt","email":"javier@example.com"}""",
            )
            responses += BackendHttpResult(
                200,
                """{"status":"ready","sleep":{"asleepMinutes":450,"inBedMinutes":480,"bedtime":"23:00","wakeTime":"07:00","stages":[]},"stepsToday":6100,"restingHeartRate":58,"previousRestingHeartRate":59,"stepsTrend":[],"sleepTrend":[],"heartRateTrend":[]}""",
            )
        }
        val client = BackendApiClient(
            baseUrl = "https://fitbit-air.run.app",
            credentials = credentials,
            transport = transport,
        )

        val authUrl = client.beginGoogleAuthorization()
        assertTrue(authUrl.startsWith("https://accounts.google.com/auth"))
        val pending = requireNotNull(credentials.pending)
        val authQuery = transport.gets.single().first.substringAfter("?")
            .split("&").associate { pair ->
                val parts = pair.split("=", limit = 2)
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            }
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pending.codeVerifier.toByteArray())
        )
        assertEquals(pending.appState, authQuery["app_state"])
        assertEquals(challenge, authQuery["code_challenge"])

        val email = client.completeGoogleAuthorization(
            "fitbitair://oauth/callback?handoff=short-ticket&app_state=${pending.appState}"
        )
        assertEquals("javier@example.com", email)
        assertEquals("app-session-jwt", credentials.token)
        val exchangeBody = transport.posts.single().third
        assertTrue(exchangeBody.contains("short-ticket"))
        assertTrue(exchangeBody.contains(pending.codeVerifier))
        assertFalse(exchangeBody.contains("google-refresh-token"))

        val state = client.loadDashboard(TrendRange.WEEK, "Europe/Madrid")
        assertTrue(state is DashboardState.Ready)
        assertEquals("app-session-jwt", transport.gets.last().second)
        assertTrue(transport.gets.last().first.startsWith("/api/dashboard?range=week&timezone="))
    }

    @Test
    fun dashboardWithoutSessionDoesNotMakeNetworkRequest() {
        val transport = FakeTransport()
        val client = BackendApiClient(
            baseUrl = "https://fitbit-air.run.app",
            credentials = FakeCredentials(),
            transport = transport,
        )

        assertEquals(DashboardState.Empty, client.loadDashboard(TrendRange.WEEK, "UTC"))
        assertTrue(transport.gets.isEmpty())
    }

    @Test
    fun serviceStatusContainsOnlyFlagsAndNeedsNoUserSession() {
        val transport = FakeTransport().apply {
            responses += BackendHttpResult(
                200,
                """{"googleOAuthConfigured":true,"healthConfigured":true,"assistantConfigured":false}""",
            )
        }
        val client = BackendApiClient(
            baseUrl = "https://fitbit-air.run.app",
            credentials = FakeCredentials(),
            transport = transport,
        )

        val status = client.loadServiceStatus()

        assertEquals(BackendServiceStatus(true, true, false), status)
        assertEquals("/api/status", transport.gets.single().first)
        assertEquals(null, transport.gets.single().second)
    }
}
