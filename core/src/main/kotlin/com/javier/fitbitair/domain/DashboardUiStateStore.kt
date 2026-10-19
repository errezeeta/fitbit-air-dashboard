package com.javier.fitbitair.domain

enum class DashboardDestination {
    HOME,
    SLEEP,
    ACTIVITY,
    ASSISTANT,
    SETTINGS,
}

data class DashboardUiState(
    val destination: DashboardDestination = DashboardDestination.HOME,
    val range: TrendRange = TrendRange.WEEK,
    val dashboard: DashboardState = DashboardState.Loading,
    val stepGoal: Int = 8_000,
    /** True while a reload is in flight, so the UI can spin without blanking. */
    val refreshing: Boolean = false,
)

/** Small UI state holder; the Compose layer renders this state and supplies user events. */
class DashboardUiStateStore {
    companion object {
        /** How long a load is considered fresh enough to serve again. */
        const val FRESH_WINDOW_MS = 45_000L
    }
    var state: DashboardUiState = DashboardUiState()
        private set

    /**
     * The last successful load for the current range. Keeping it lets the app
     * re-render instantly while a refresh runs in the background, instead of
     * blanking into the loading skeleton every time the screen is opened.
     */
    private val lastGoodByRange = mutableMapOf<TrendRange, DashboardState.Ready>()
    private val loadedAtByRange = mutableMapOf<TrendRange, Long>()
    private var loadedRevision: Int? = null
    private var loadedConnected: Boolean? = null
    private var settingsReturnDestination = DashboardDestination.HOME

    fun cachedDashboard(range: TrendRange): DashboardState.Ready? = lastGoodByRange[range]

    /**
     * Whether a fetch is actually needed. Data loaded moments ago is served
     * from here: sliding between sections or flipping the range back and forth
     * must not turn into network traffic.
     */
    fun needsReload(
        revision: Int,
        connected: Boolean,
        ttlMillis: Long = FRESH_WINDOW_MS,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val loadedAt = loadedAtByRange[state.range] ?: return true
        if (nowMillis - loadedAt >= ttlMillis) return true
        if (loadedRevision != revision) return true
        if (loadedConnected != connected) return true
        return false
    }

    /** Forces the next load to go out, e.g. after a pull-to-refresh. */
    fun markStale() {
        loadedAtByRange.remove(state.range)
    }

    fun select(destination: DashboardDestination) {
        if (destination != DashboardDestination.SETTINGS) {
            state = state.copy(destination = destination)
        }
    }

    fun updateDashboard(
        dashboard: DashboardState,
        revision: Int = 0,
        connected: Boolean = false,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        if (dashboard is DashboardState.Ready) {
            lastGoodByRange[state.range] = dashboard
            loadedAtByRange[state.range] = atMillis
            loadedRevision = revision
            loadedConnected = connected
        }
        state = state.copy(dashboard = dashboard, refreshing = false)
    }

    /**
     * Refreshing keeps the previous data on screen and only swaps in the loading
     * state when there is nothing to show yet.
     */
    fun beginRefresh() {
        val replacement = cachedDashboard(state.range) ?: DashboardState.Loading
        if (state.dashboard !is DashboardState.Ready) {
            state = state.copy(dashboard = replacement)
        }
        state = state.copy(refreshing = true)
    }

    fun changeRange(range: TrendRange) {
        if (state.range != range) {
            val cached = cachedDashboard(range)
            state = state.copy(
                range = range,
                dashboard = cached ?: DashboardState.Loading,
            )
        }
    }

    fun openSettings() {
        if (state.destination != DashboardDestination.SETTINGS) {
            settingsReturnDestination = state.destination
            state = state.copy(destination = DashboardDestination.SETTINGS)
        }
    }

    fun closeSettings() {
        if (state.destination == DashboardDestination.SETTINGS) {
            state = state.copy(destination = settingsReturnDestination)
        }
    }

    fun setStepGoal(goal: Int): Boolean {
        if (goal <= 0) return false
        state = state.copy(stepGoal = goal)
        return true
    }
}
