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
import com.javier.fitbitair.domain.snapshotFor
import com.javier.fitbitair.ui.screens.ActivityScreen
import com.javier.fitbitair.ui.screens.AssistantMessage
import com.javier.fitbitair.ui.screens.AssistantScreen
import com.javier.fitbitair.ui.screens.DayDetailSheet
import com.javier.fitbitair.ui.screens.HomeScreen
import com.javier.fitbitair.ui.screens.MetricDetailScreen
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
    onAskAssistant: suspend (String, TrendRange) -> com.javier.fitbitair.domain.AssistantAnswer = { _, _ ->
        com.javier.fitbitair.domain.AssistantAnswer(text = "El asistente todavía no está disponible.")
    },
    /** Streamed variant: stages and text as the agent produces them. */
    onAskAssistantStreaming: suspend (String, TrendRange, (com.javier.fitbitair.domain.AssistantStreamEvent) -> Unit) -> Unit = { _, _, _ -> },
) {
    val stateStore = remember(stepGoalStore) {
        DashboardUiStateStore().apply { setStepGoal(stepGoalStore.getGoal()) }
    }
    var uiState by remember { mutableStateOf(stateStore.state) }
    var reloadToken by remember { mutableStateOf(0) }
    var assistantBusy by remember { mutableStateOf(false) }
    var detailMetric by remember { mutableStateOf<com.javier.fitbitair.domain.MetricKind?>(null) }
    var daySheet by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val chat = remember { mutableStateListOf<AssistantMessage>() }

    fun select(destination: DashboardDestination) {
        detailMetric = null
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
                    if (detailMetric != null) {
                        IconButton(onClick = { detailMetric = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                        }
                    } else if (uiState.destination == DashboardDestination.SETTINGS) {
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
            // One place for the day sheet, so holding a bar works in every screen.
            (uiState.dashboard as? DashboardState.Ready)?.let { ready ->
                daySheet?.let { day ->
                    DayDetailSheet(
                        snapshot = ready.data.snapshotFor(day),
                        onDismiss = { daySheet = null },
                    )
                }
            }
            val openDetail = detailMetric
            if (openDetail != null) {
                AnimatedContent(targetState = uiState.dashboard, modifier = Modifier.weight(1f), label = "detail-transition") { state ->
                    DashboardContent(state, isRefreshing = uiState.refreshing, onRefresh = { reloadToken++ }) { data ->
                        MetricDetailScreen(
                            kind = openDetail,
                            data = data,
                            range = uiState.range,
                            stepGoal = uiState.stepGoal,
                            onRangeChange = ::changeRange,
                            onOpenDay = { daySheet = it },
                        )
                    }
                }
                return@Column
            }
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
                    DashboardDestination.HOME -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { reloadToken++ }) { data ->
                        HomeScreen(
                            data = data,
                            stepGoal = uiState.stepGoal,
                            range = uiState.range,
                            onRangeChange = ::changeRange,
                            onStepGoalChange = ::updateStepGoal,
                            onOpenSleep = { select(DashboardDestination.SLEEP) },
                            onOpenDetail = { detailMetric = it },
                            onOpenDay = { daySheet = it },
                        )
                    }
                    DashboardDestination.SLEEP -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { reloadToken++ }) { data ->
                        SleepScreen(
                            data = data,
                            range = uiState.range,
                            onRangeChange = ::changeRange,
                            onOpenDetail = { detailMetric = it },
                            onOpenDay = { daySheet = it },
                        )
                    }
                    DashboardDestination.ACTIVITY -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { reloadToken++ }) { data ->
                        ActivityScreen(
                            data = data,
                            stepGoal = uiState.stepGoal,
                            range = uiState.range,
                            onRangeChange = ::changeRange,
                            onStepGoalChange = ::updateStepGoal,
                            onOpenDetail = { detailMetric = it },
                            onOpenDay = { daySheet = it },
                        )
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
                                            text = "",
                                            usedData = "Google Health · ${selectedRange.label}",
                                            isDemo = false,
                                            streaming = true,
                                            stageLabel = "Preparando la consulta",
                                        )
                                        val answerIndex = chat.lastIndex
                                        assistantBusy = true
                                        val events = kotlinx.coroutines.channels.Channel<
                                            com.javier.fitbitair.domain.AssistantStreamEvent
                                            >(kotlinx.coroutines.channels.Channel.UNLIMITED)
                                        // The transport runs on IO and only pushes events;
                                        // the bubble is updated on the main thread in order.
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                onAskAssistantStreaming(message, selectedRange) { event ->
                                                    events.trySend(event)
                                                }
                                            } catch (_: Exception) {
                                                events.trySend(
                                                    com.javier.fitbitair.domain.AssistantStreamEvent.Failure(
                                                        "No se pudo consultar el asistente. Inténtalo de nuevo más tarde."
                                                    )
                                                )
                                            } finally {
                                                events.close()
                                            }
                                        }
                                        coroutineScope.launch {
                                            val streamed = StringBuilder()
                                            var closed = false
                                            for (event in events) {
                                                val bubble = chat.getOrNull(answerIndex) as? AssistantMessage.Agent
                                                    ?: break
                                                when (event) {
                                                    is com.javier.fitbitair.domain.AssistantStreamEvent.Stage ->
                                                        chat[answerIndex] = bubble.copy(
                                                            stage = event.id,
                                                            stageLabel = event.label,
                                                        )
                                                    is com.javier.fitbitair.domain.AssistantStreamEvent.Delta -> {
                                                        streamed.append(event.text)
                                                        chat[answerIndex] = bubble.copy(
                                                            text = streamed.toString(),
                                                            stage = "escribiendo",
                                                        )
                                                    }
                                                    is com.javier.fitbitair.domain.AssistantStreamEvent.Done -> {
                                                        closed = true
                                                        chat[answerIndex] = bubble.copy(
                                                            text = event.answer.text.ifBlank { streamed.toString() },
                                                            chart = event.answer.chart,
                                                            suggestions = event.answer.suggestions,
                                                            streaming = false,
                                                        )
                                                    }
                                                    is com.javier.fitbitair.domain.AssistantStreamEvent.Failure -> {
                                                        closed = true
                                                        chat[answerIndex] = bubble.copy(
                                                            text = event.message,
                                                            isDemo = true,
                                                            streaming = false,
                                                        )
                                                    }
                                                }
                                            }
                                            if (!closed) {
                                                val bubble = chat.getOrNull(answerIndex) as? AssistantMessage.Agent
                                                if (bubble != null) {
                                                    chat[answerIndex] = bubble.copy(
                                                        text = streamed.toString().ifBlank {
                                                            "El asistente no devolvió respuesta. Inténtalo de nuevo."
                                                        },
                                                        streaming = false,
                                                        isDemo = streamed.isEmpty(),
                                                    )
                                                }
                                            }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable (DashboardData) -> Unit,
) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            DashboardState.Loading -> LoadingScreen()
            DashboardState.Empty -> EmptyScreen(onRefresh)
            is DashboardState.Error -> ErrorScreen(state.message, onRefresh)
            is DashboardState.Ready -> content(state.data)
        }
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
private fun EmptyScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Todavía no hay datos", style = MaterialTheme.typography.titleLarge)
            Text(
                "Conecta Google Health en Ajustes o desliza hacia abajo para volver a consultar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Volver a consultar") }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No se pudieron cargar los datos", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}
