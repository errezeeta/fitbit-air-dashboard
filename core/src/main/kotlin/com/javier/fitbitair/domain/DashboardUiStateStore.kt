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
)

/** Small UI state holder; the Compose layer renders this state and supplies user events. */
class DashboardUiStateStore {
    var state: DashboardUiState = DashboardUiState()
        private set
    private var settingsReturnDestination = DashboardDestination.HOME

    fun select(destination: DashboardDestination) {
        if (destination != DashboardDestination.SETTINGS) {
            state = state.copy(destination = destination)
        }
    }

    fun updateDashboard(dashboard: DashboardState) {
        state = state.copy(dashboard = dashboard)
    }

    fun changeRange(range: TrendRange) {
        if (state.range != range) {
            state = state.copy(range = range, dashboard = DashboardState.Loading)
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
