package com.javier.fitbitair.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.data.StepGoalStore
import com.javier.fitbitair.domain.DashboardData
import com.javier.fitbitair.domain.DashboardDestination
import com.javier.fitbitair.domain.DashboardState
import com.javier.fitbitair.domain.DashboardUiStateStore
import com.javier.fitbitair.domain.HealthRepository
import com.javier.fitbitair.domain.TrendRange
import com.javier.fitbitair.ui.screens.ActivityScreen
import com.javier.fitbitair.ui.screens.AssistantMessage
import com.javier.fitbitair.ui.screens.AssistantScreen
import com.javier.fitbitair.ui.screens.HomeScreen
import com.javier.fitbitair.ui.screens.SettingsScreen
import com.javier.fitbitair.ui.screens.SleepScreen
import kotlinx.coroutines.launch

private enum class AppTab(
    val destination: DashboardDestination,
    val label: String,
    val icon: ImageVector,
) {
    HOME(DashboardDestination.HOME, "Inicio", Icons.Rounded.Home),
    SLEEP(DashboardDestination.SLEEP, "Sueño", Icons.Rounded.Bedtime),
    ACTIVITY(DashboardDestination.ACTIVITY, "Actividad", Icons.AutoMirrored.Rounded.DirectionsWalk),
    ASSISTANT(DashboardDestination.ASSISTANT, "IA", Icons.Rounded.AutoAwesome),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitbitAirApp(
    repository: HealthRepository,
    stepGoalStore: StepGoalStore,
    backendConfigured: Boolean = false,
    googleConnected: Boolean = false,
    googleOAuthConfigured: Boolean = false,
    assistantConfigured: Boolean = false,
    connectionMessage: String? = null,
    dataRevision: Int = 0,
    updater: com.javier.fitbitair.data.AppUpdater? = null,
    onConnectGoogleHealth: () -> Unit = {},
    onAskAssistant: suspend (String, TrendRange) -> String = { _, _ -> "El asistente todavía no está disponible." },
) {
    val stateStore = remember(stepGoalStore) {
        DashboardUiStateStore().apply { setStepGoal(stepGoalStore.getGoal()) }
    }
    var uiState by remember { mutableStateOf(stateStore.state) }
    var reloadToken by remember { mutableStateOf(0) }
    var assistantBusy by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val chat = remember { mutableStateListOf<AssistantMessage>() }

    fun select(destination: DashboardDestination) {
        stateStore.select(destination)
        uiState = stateStore.state
    }

    fun changeRange(range: TrendRange) {
        stateStore.changeRange(range)
        uiState = stateStore.state
    }

    fun updateStepGoal(value: Int) {
        if (stateStore.setStepGoal(value)) {
            stepGoalStore.saveGoal(value)
            uiState = stateStore.state
        }
    }

    LaunchedEffect(uiState.range, reloadToken, dataRevision, googleConnected) {
        // Keep whatever the last good load painted and refresh behind it; a
        // blank skeleton only shows on a first run with nothing cached.
        stateStore.beginRefresh()
        uiState = stateStore.state
        val loaded = try {
            repository.loadDashboard(uiState.range)
        } catch (error: Exception) {
            DashboardState.Error(error.message ?: "No se pudieron cargar los datos.")
        }
        stateStore.updateDashboard(loaded)
        uiState = stateStore.state
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Salud diaria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (uiState.destination == DashboardDestination.SETTINGS) "Preferencias" else "Fitbit Air",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.destination == DashboardDestination.SETTINGS) {
                        IconButton(onClick = {
                            stateStore.closeSettings()
                            uiState = stateStore.state
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { reloadToken++ },
                        enabled = uiState.dashboard != DashboardState.Loading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar datos")
                    }
                    IconButton(onClick = {
                        if (uiState.destination == DashboardDestination.SETTINGS) {
                            stateStore.closeSettings()
                        } else {
                            stateStore.openSettings()
                        }
                        uiState = stateStore.state
                    }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // Translucent bar over a gradient backdrop gives the
                    // Material You "frosted glass" feel; edge-to-edge is on.
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ),
            )
        },
        bottomBar = {
            if (uiState.destination != DashboardDestination.SETTINGS) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                ) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = uiState.destination == tab.destination,
                            onClick = { select(tab.destination) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DataSourceBanner(backendConfigured, googleConnected)
            AnimatedContent(
                targetState = uiState.destination,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 16 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 20 })
                },
                label = "screen-transition",
            ) { destination ->
                when (destination) {
                    DashboardDestination.SETTINGS -> SettingsScreen(
                        stepGoal = uiState.stepGoal,
                        onStepGoalChange = ::updateStepGoal,
                        backendConfigured = backendConfigured,
                        googleConnected = googleConnected,
                        googleOAuthConfigured = googleOAuthConfigured,
                        assistantConfigured = assistantConfigured,
                        connectionMessage = connectionMessage,
                        onConnectGoogleHealth = onConnectGoogleHealth,
                        updater = updater,
                    )
                    DashboardDestination.HOME -> DashboardContent(uiState.dashboard) { data ->
                        HomeScreen(
                            data = data,
                            stepGoal = uiState.stepGoal,
                            range = uiState.range,
                            onRangeChange = ::changeRange,
                            onStepGoalChange = ::updateStepGoal,
                            onOpenSleep = { select(DashboardDestination.SLEEP) },
                        )
                    }
                    DashboardDestination.SLEEP -> DashboardContent(uiState.dashboard) { data ->
                        SleepScreen(data, uiState.range, ::changeRange)
                    }
                    DashboardDestination.ACTIVITY -> DashboardContent(uiState.dashboard) { data ->
                        ActivityScreen(data, uiState.stepGoal, uiState.range, ::changeRange, ::updateStepGoal)
                    }
                    DashboardDestination.ASSISTANT -> AssistantScreen(
                        messages = chat,
                        backendConfigured = backendConfigured,
                        googleConnected = googleConnected,
                        assistantConfigured = assistantConfigured,
                        isBusy = assistantBusy,
                        onSend = { message ->
                            if (!assistantBusy) {
                                chat += AssistantMessage.User(message)
                                when {
                                    !backendConfigured -> chat += AssistantMessage.Agent(
                                        text = "Esta instalación aún usa datos de muestra. El backend de Cloud Run está pendiente.",
                                        usedData = "Demo · sin datos Fitbit reales",
                                        isDemo = true,
                                    )
                                    !googleConnected -> chat += AssistantMessage.Agent(
                                        text = "Conecta Google Health en Ajustes para consultar tus métricas.",
                                        usedData = "Sin datos de salud enviados",
                                        isDemo = false,
                                    )
                                    !assistantConfigured -> chat += AssistantMessage.Agent(
                                        text = "Hermes todavía no está configurado para esta app.",
                                        usedData = "Google Health conectado · Hermes pendiente",
                                        isDemo = false,
                                    )
                                    else -> {
                                        val selectedRange = uiState.range
                                        chat += AssistantMessage.Agent(
                                            text = "Consultando tus métricas…",
                                            usedData = "Google Health · ${selectedRange.label}",
                                            isDemo = false,
                                        )
                                        val answerIndex = chat.lastIndex
                                        assistantBusy = true
                                        coroutineScope.launch {
                                            val answer = try {
                                                onAskAssistant(message, selectedRange)
                                            } catch (_: Exception) {
                                                "No se pudo consultar el asistente. Inténtalo de nuevo más tarde."
                                            }
                                            chat[answerIndex] = AssistantMessage.Agent(
                                                text = answer,
                                                usedData = "Google Health · ${selectedRange.label}",
                                                isDemo = false,
                                            )
                                            assistantBusy = false
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    content: @Composable (DashboardData) -> Unit,
) {
    when (state) {
        DashboardState.Loading -> LoadingScreen()
        DashboardState.Empty -> EmptyScreen()
        is DashboardState.Error -> ErrorScreen(state.message)
        is DashboardState.Ready -> content(state.data)
    }
}

@Composable
private fun DataSourceBanner(backendConfigured: Boolean, googleConnected: Boolean) {
    val label = when {
        !backendConfigured -> "Prototipo · datos ficticios"
        !googleConnected -> "Google Health no conectado"
        else -> "Google Health · consulta a demanda"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        AssistChip(
            onClick = {},
            label = { Text(label) },
            leadingIcon = { Text("●", color = if (googleConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) },
        )
    }
}

@Composable
private fun LoadingScreen() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.78f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Consultando datos…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        repeat(3) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
            ) {}
        }
    }
}

@Composable
private fun EmptyScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Todavía no hay datos", style = MaterialTheme.typography.titleLarge)
            Text(
                "Conecta Google Health para consultar tus métricas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudieron cargar los datos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
