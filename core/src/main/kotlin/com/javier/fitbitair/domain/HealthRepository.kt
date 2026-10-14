package com.javier.fitbitair.domain

/** Read-only data boundary. The later Google Health adapter will replace the demo source. */
fun interface HealthRepository {
    suspend fun loadDashboard(range: TrendRange): DashboardState
}
