package com.javier.fitbitair.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.javier.fitbitair.ui.components.BrandMark
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private enum class AppTab(
    val destination: DashboardDestination,
    val label: String,
    val icon: ImageVector,
) {
    HOME(DashboardDestination.HOME, "Inicio", Icons.Rounded.Home),
    SLEEP(DashboardDestination.SLEEP, "Sueño", Icons.Rounded.Bedtime),
    ACTIVITY(DashboardDestination.ACTIVITY, "Actividad", Icons.AutoMirrored.Rounded.DirectionsWalk),
    ASSISTANT(DashboardDestination.ASSISTANT, "Asistente", Icons.Rounded.AutoAwesome),
}

/** What the app bar calls the place you are standing in. */
private fun sectionLabel(
    destination: DashboardDestination,
    detail: com.javier.fitbitair.domain.MetricKind?,
): String = when {
    detail != null -> detail.title
    destination == DashboardDestination.SETTINGS -> "Ajustes"
    else -> AppTab.entries.firstOrNull { it.destination == destination }?.label ?: "Inicio"
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

    // Back should walk the app back, not kill it: a detail view closes, settings
    // close, another tab returns Home, and only then does the system take over.
    BackHandler(
        enabled = daySheet != null ||
            detailMetric != null ||
            uiState.destination != DashboardDestination.HOME,
    ) {
        when {
            daySheet != null -> daySheet = null
            detailMetric != null -> detailMetric = null
            uiState.destination == DashboardDestination.SETTINGS -> {
                stateStore.closeSettings()
                uiState = stateStore.state
            }
            else -> select(DashboardDestination.HOME)
        }
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

    var handledReloadToken by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.range, reloadToken, dataRevision, googleConnected) {
        val forced = reloadToken != handledReloadToken
        if (!forced &&
            !stateStore.needsReload(revision = dataRevision, connected = googleConnected)
        ) {
            // Data loaded moments ago: sliding between sections or flipping the
            // range back must not turn into another four Google round trips.
            return@LaunchedEffect
        }
        handledReloadToken = reloadToken
        val requestedRange = uiState.range
        // Keep whatever the last good load painted and refresh behind it; a
        // blank skeleton only shows on a first load of a range.
        stateStore.beginRefresh()
        uiState = stateStore.state
        val loaded = try {
            repository.loadDashboard(requestedRange)
        } catch (cancelled: CancellationException) {
            // Range changes cancel the old request. Never turn that cancellation
            // into an error state or let its result overwrite the newer range.
            throw cancelled
        } catch (error: Exception) {
            DashboardState.Error(error.message ?: "No se pudieron cargar los datos.")
        }
        currentCoroutineContext().ensureActive()
        if (stateStore.state.range != requestedRange) return@LaunchedEffect
        stateStore.updateDashboard(loaded, revision = dataRevision, connected = googleConnected)
        uiState = stateStore.state
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            BrandMark(modifier = Modifier.size(21.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            // Brand up top, section underneath: the page headers
                            // already name the section, so a static "salud
                            // personal" repeated it and said nothing.
                            Text("Salud diaria", style = MaterialTheme.typography.titleMedium)
                            Text(
                                sectionLabel(uiState.destination, detailMetric),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    // Solid, not translucent: with no blur behind it, a 0.72
                    // surface let the scrolling content bleed through as mud.
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (uiState.destination != DashboardDestination.SETTINGS) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = uiState.destination == tab.destination,
                            onClick = { select(tab.destination) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            // Labels for the active destination only: four
                            // permanent labels were doing the work of one.
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
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
                com.javier.fitbitair.ui.components.SlideInFromEnd(modifier = Modifier.weight(1f)) {
                    // No AnimatedContent here: DashboardContent already crossfades
                    // when the state kind changes, and wrapping it again re-ran a
                    // full-screen transition on every refresh.
                    DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { stateStore.markStale(); reloadToken++ }) { data ->
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
            // Tabs live in a horizontal pager: the same screens, but you can
            // slide between them instead of only tapping the bar. Settings is
            // the only other state, so swiping never rebuilds the pager (which
            // used to re-run a screen transition on every slide).
            AnimatedContent(
                targetState = uiState.destination == DashboardDestination.SETTINGS,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 8 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 12 })
                },
                label = "settings-transition",
            ) { settingsOpen ->
                if (settingsOpen) {
                    com.javier.fitbitair.ui.components.SlideInFromEnd {
                        SettingsScreen(
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
                    }
                } else {
                    val tabs = appTabs
                    val currentTab = tabs.indexOf(uiState.destination).coerceAtLeast(0)
                    val pagerState = rememberPagerState(initialPage = currentTab) { tabs.size }
                    // Swiping and the bottom bar move the same state, one hop.
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            val next = tabs.getOrNull(page) ?: return@collect
                            if (next != uiState.destination) select(next)
                        }
                    }
                    LaunchedEffect(uiState.destination) {
                        val target = tabs.indexOf(uiState.destination)
                        // targetPage, not currentPage: after a swipe the pager is
                        // already heading there and must not animate twice.
                        if (target >= 0 && target != pagerState.targetPage) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { page -> tabs[page] },
                    ) { page ->
                        when (tabs[page]) {
                            DashboardDestination.HOME -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { stateStore.markStale(); reloadToken++ }) { data ->
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
                    DashboardDestination.SLEEP -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { stateStore.markStale(); reloadToken++ }) { data ->
                        SleepScreen(
                            data = data,
                            range = uiState.range,
                            onRangeChange = ::changeRange,
                            onOpenDetail = { detailMetric = it },
                            onOpenDay = { daySheet = it },
                        )
                    }
                    DashboardDestination.ACTIVITY -> DashboardContent(uiState.dashboard, isRefreshing = uiState.refreshing, onRefresh = { stateStore.markStale(); reloadToken++ }) { data ->
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
                    DashboardDestination.SETTINGS -> Unit
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
                                            startedAt = System.currentTimeMillis(),
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
                                                            stageDetail = event.detail.orEmpty(),
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
    }
}

private val appTabs = listOf(
    DashboardDestination.HOME,
    DashboardDestination.SLEEP,
    DashboardDestination.ACTIVITY,
    DashboardDestination.ASSISTANT,
)

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
        // Crossfade on the KIND of state, never on the state object itself:
        // Ready carries the data, so keying it here crossfaded whole screens —
        // three of them, because the pager keeps its neighbours composed — on
        // every single refresh instead of just repainting the numbers.
        val stage = when (state) {
            DashboardState.Loading -> STAGE_LOADING
            DashboardState.Empty -> STAGE_EMPTY
            is DashboardState.Error -> STAGE_ERROR
            is DashboardState.Ready -> STAGE_READY
        }
        androidx.compose.animation.Crossfade(
            targetState = stage,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
            label = "dashboard-state-transition",
        ) { currentStage ->
            when (currentStage) {
                STAGE_LOADING -> LoadingScreen()
                STAGE_EMPTY -> EmptyScreen(onRefresh)
                STAGE_ERROR -> ErrorScreen((state as? DashboardState.Error)?.message.orEmpty(), onRefresh)
                else -> (state as? DashboardState.Ready)?.let { content(it.data) }
            }
        }
    }
}

private const val STAGE_LOADING = 0
private const val STAGE_EMPTY = 1
private const val STAGE_ERROR = 2
private const val STAGE_READY = 3

@Composable
private fun DataSourceBanner(backendConfigured: Boolean, googleConnected: Boolean) {
    val label: String
    val statusColor: androidx.compose.ui.graphics.Color
    when {
        !backendConfigured -> {
            label = "Modo demo · datos de muestra"
            statusColor = MaterialTheme.colorScheme.outline
        }
        !googleConnected -> {
            label = "Google Health · sin conexión"
            statusColor = MaterialTheme.colorScheme.error
        }
        else -> {
            label = "Google Health · conectado · consulta a demanda"
            statusColor = MaterialTheme.colorScheme.tertiary
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).background(statusColor, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LoadingScreen() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading-shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                1300,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        ),
        label = "shimmer-sweep",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)
    // A band that travels across the placeholders reads as work in progress;
    // a flat pulsing block reads as a frozen screen.
    val shimmer = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(sweep * 1500f - 750f, 0f),
        end = androidx.compose.ui.geometry.Offset(sweep * 1500f, 0f),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Consultando tus datos…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        listOf(148.dp, 92.dp, 148.dp).forEach { blockHeight ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmer),
            )
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
            // A moon said "sleep" on a screen about missing data. The mark says
            // "this is the app, and it has nothing yet".
            BrandMark(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            )
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
