package com.javier.fitbitair.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUiStateStoreTest {
    @Test
    fun startsOnHomeWithWeeklyRangeLoadingAndDefaultStepGoal() {
        val store = DashboardUiStateStore()

        assertEquals(DashboardDestination.HOME, store.state.destination)
        assertEquals(TrendRange.WEEK, store.state.range)
        assertEquals(DashboardState.Loading, store.state.dashboard)
        assertEquals(8_000, store.state.stepGoal)
    }

    @Test
    fun selectingSleepChangesTheCurrentDestination() {
        val store = DashboardUiStateStore()

        store.select(DashboardDestination.SLEEP)

        assertEquals(DashboardDestination.SLEEP, store.state.destination)
    }

    @Test
    fun updatingDashboardPublishesTheLoadedState() {
        val store = DashboardUiStateStore()

        store.updateDashboard(DashboardState.Empty)

        assertEquals(DashboardState.Empty, store.state.dashboard)
    }

    @Test
    fun changingRangeUpdatesRangeAndResetsDashboardToLoading() {
        val store = DashboardUiStateStore()
        store.updateDashboard(DashboardState.Empty)

        store.changeRange(TrendRange.MONTH)

        assertEquals(TrendRange.MONTH, store.state.range)
        assertEquals(DashboardState.Loading, store.state.dashboard)
    }

    @Test
    fun settingsReturnsToThePreviouslySelectedTab() {
        val store = DashboardUiStateStore()
        store.select(DashboardDestination.SLEEP)

        store.openSettings()
        assertEquals(DashboardDestination.SETTINGS, store.state.destination)
        store.closeSettings()

        assertEquals(DashboardDestination.SLEEP, store.state.destination)
    }

    @Test
    fun nonPositiveStepGoalIsRejectedWithoutChangingPreference() {
        val store = DashboardUiStateStore()

        assertEquals(false, store.setStepGoal(0))
        assertEquals(8_000, store.state.stepGoal)
    }

    @Test
    fun positiveStepGoalUpdatesPreference() {
        val store = DashboardUiStateStore()

        assertEquals(true, store.setStepGoal(10_500))
        assertEquals(10_500, store.state.stepGoal)
    }

    @Test
    fun errorStateKeepsItsRetryMessage() {
        val store = DashboardUiStateStore()
        val error = DashboardState.Error("Sin conexión")

        store.updateDashboard(error)

        assertEquals(error, store.state.dashboard)
    }
}
