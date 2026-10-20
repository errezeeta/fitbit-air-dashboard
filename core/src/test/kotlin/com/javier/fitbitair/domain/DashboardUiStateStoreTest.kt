package com.javier.fitbitair.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateStoreTest {
    private val ready = DashboardState.Ready(
        DashboardData(
            sleep = SleepSummary(400, 480, "23:00", "07:00", emptyList()),
            stepsToday = 5000,
            restingHeartRate = 58,
            previousRestingHeartRate = null,
            stepsTrend = emptyList(),
            sleepTrend = emptyList(),
            heartRateTrend = emptyList(),
        ),
    )

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
    fun changingRangeShowsLoadingUntilSomethingIsCached() {
        val store = DashboardUiStateStore()
        store.updateDashboard(DashboardState.Empty)

        store.changeRange(TrendRange.MONTH)

        assertEquals(TrendRange.MONTH, store.state.range)
        assertEquals(DashboardState.Loading, store.state.dashboard)
    }

    @Test
    fun changingBackToARangeWithGoodDataShowsItImmediately() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready)
        store.changeRange(TrendRange.MONTH)
        store.changeRange(TrendRange.WEEK)

        assertEquals(ready, store.state.dashboard)
    }

    @Test
    fun changingRangeStopsThePreviousRangesRefreshIndicator() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready)
        store.changeRange(TrendRange.MONTH)
        store.updateDashboard(ready)
        store.changeRange(TrendRange.WEEK)
        store.beginRefresh()

        store.changeRange(TrendRange.MONTH)

        assertEquals(false, store.state.refreshing)
        assertEquals(ready, store.state.dashboard)
    }

    @Test
    fun beginRefreshKeepsTheCachedDashboardInsteadOfBlanking() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready)

        store.beginRefresh()

        assertEquals(ready, store.state.dashboard)
        // The data stays on screen while the spinner runs over it.
        assertEquals(true, store.state.refreshing)

        store.updateDashboard(ready)

        assertEquals(false, store.state.refreshing)
    }

    @Test
    fun needsReloadIsFalseRightAfterALoadAndTrueOnceTheWindowPasses() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready, revision = 0, connected = true, atMillis = 1_000L)

        // Same revision and connection, well inside the window: nothing to fetch.
        assertEquals(false, store.needsReload(revision = 0, connected = true, nowMillis = 5_000L))
        // Past the window, the data is stale enough to ask again.
        assertEquals(true, store.needsReload(revision = 0, connected = true, nowMillis = 99_000L))
    }

    @Test
    fun needsReloadIsTrueOnAFreshStoreOrWhenSomethingActuallyChanged() {
        val store = DashboardUiStateStore()
        // Nothing loaded yet.
        assertEquals(true, store.needsReload(revision = 0, connected = false, nowMillis = 1_000L))

        store.updateDashboard(ready, revision = 3, connected = false, atMillis = 1_000L)
        // A new revision (re-authorization) invalidates what is on screen.
        assertEquals(true, store.needsReload(revision = 4, connected = false, nowMillis = 2_000L))
        assertEquals(true, store.needsReload(revision = 3, connected = true, nowMillis = 2_000L))
        assertEquals(false, store.needsReload(revision = 3, connected = false, nowMillis = 2_000L))
    }

    @Test
    fun markStaleForcesTheNextLoadEvenInsideTheWindow() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready, revision = 0, connected = false, atMillis = 1_000L)
        assertEquals(false, store.needsReload(revision = 0, connected = false, nowMillis = 2_000L))

        store.markStale()

        assertEquals(true, store.needsReload(revision = 0, connected = false, nowMillis = 2_000L))
    }

    @Test
    fun eachRangeKeepsItsOwnFreshnessWindow() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready, revision = 0, connected = false, atMillis = 1_000L)
        store.changeRange(TrendRange.MONTH)

        // The month range was never loaded, so it must go out.
        assertEquals(true, store.needsReload(revision = 0, connected = false, nowMillis = 2_000L))
    }

    @Test
    fun errorsNeverReplaceTheCachedGoodState() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready)
        store.updateDashboard(DashboardState.Error("algo"))

        assertEquals(ready, store.cachedDashboard(TrendRange.WEEK))
    }

    @Test
    fun eachRangeCachesItsOwnGoodState() {
        val store = DashboardUiStateStore()
        store.updateDashboard(ready)
        store.changeRange(TrendRange.MONTH)
        val monthly = DashboardState.Ready(ready.data)
        store.updateDashboard(monthly)

        assertEquals(ready, store.cachedDashboard(TrendRange.WEEK))
        assertEquals(monthly, store.cachedDashboard(TrendRange.MONTH))
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
