package com.javier.fitbitair

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.javier.fitbitair.data.AppUpdater
import com.javier.fitbitair.data.BackendHealthRepository
import com.javier.fitbitair.data.SampleHealthRepository
import com.javier.fitbitair.data.SessionTokenStore
import com.javier.fitbitair.data.StepGoalStore
import com.javier.fitbitair.data.UrlConnectionBackendTransport
import com.javier.fitbitair.domain.BackendApiClient
import com.javier.fitbitair.domain.BackendServiceStatus
import com.javier.fitbitair.domain.HealthRepository
import com.javier.fitbitair.ui.FitbitAirApp
import com.javier.fitbitair.ui.theme.FitbitAirTheme
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val googleConnected = MutableStateFlow(false)
    private val backendStatus = MutableStateFlow(
        BackendServiceStatus(
            googleOAuthConfigured = false,
            healthConfigured = false,
            assistantConfigured = false,
        ),
    )
    private val connectionNotice = MutableStateFlow<String?>(null)
    private val dataRevision = MutableStateFlow(0)

    private lateinit var sessionStore: SessionTokenStore
    private lateinit var updater: AppUpdater
    private var backendClient: BackendApiClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionStore = SessionTokenStore(applicationContext)
        updater = AppUpdater(applicationContext)
        googleConnected.value = sessionStore.hasSession()
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trim()
        if (baseUrl.isNotBlank()) {
            backendClient = BackendApiClient(
                baseUrl = baseUrl,
                credentials = sessionStore,
                transport = UrlConnectionBackendTransport(baseUrl),
            )
        }
        val repository: HealthRepository = backendClient?.let(::BackendHealthRepository)
            ?: SampleHealthRepository()
        val stepGoalStore = StepGoalStore(applicationContext)

        setContent {
            val connected by googleConnected.collectAsState()
            val services by backendStatus.collectAsState()
            val notice by connectionNotice.collectAsState()
            val revision by dataRevision.collectAsState()
            FitbitAirTheme {
                FitbitAirApp(
                    repository = repository,
                    stepGoalStore = stepGoalStore,
                    backendConfigured = backendClient != null,
                    googleConnected = connected,
                    googleOAuthConfigured = services.googleOAuthConfigured,
                    assistantConfigured = services.assistantConfigured,
                    connectionMessage = notice,
                    dataRevision = revision,
                    updater = updater,
                    onConnectGoogleHealth = ::beginGoogleAuthorization,
                    onAskAssistant = { question, range ->
                        withContext(Dispatchers.IO) {
                            backendClient?.askAssistant(
                                question = question,
                                range = range,
                                timezone = ZoneId.systemDefault().id,
                            ) ?: throw IllegalStateException("El backend de Cloud Run aún no está configurado.")
                        }
                    },
                )
            }
        }

        refreshBackendStatus()
        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun beginGoogleAuthorization() {
        val api = backendClient
        if (api == null) {
            connectionNotice.value = "Cloud Run aún no está configurado."
            return
        }
        if (!backendStatus.value.googleOAuthConfigured) {
            connectionNotice.value = "La configuración OAuth del servidor aún está pendiente."
            return
        }
        uiScope.launch {
            try {
                val authorizationUrl = withContext(Dispatchers.IO) {
                    api.beginGoogleAuthorization()
                }
                connectionNotice.value = null
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
            } catch (_: Exception) {
                connectionNotice.value = "No se pudo iniciar la conexión con Google. Inténtalo de nuevo."
            }
        }
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "fitbitair" || uri.host != "oauth" || uri.path != "/callback") return
        val api = backendClient ?: run {
            connectionNotice.value = "Cloud Run aún no está configurado."
            return
        }
        uiScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.completeGoogleAuthorization(uri.toString())
                }
                googleConnected.value = true
                connectionNotice.value = "Google Health conectado."
                dataRevision.value += 1
                refreshBackendStatus()
            } catch (_: Exception) {
                googleConnected.value = sessionStore.hasSession()
                connectionNotice.value = "No se pudo completar la autorización. Vuelve a intentarlo."
            }
        }
    }

    private fun refreshBackendStatus() {
        val api = backendClient ?: return
        uiScope.launch {
            val status = runCatching { withContext(Dispatchers.IO) { api.loadServiceStatus() } }
                .getOrElse {
                    BackendServiceStatus(
                        googleOAuthConfigured = false,
                        healthConfigured = false,
                        assistantConfigured = false,
                    )
                }
            backendStatus.value = status
        }
    }
}
