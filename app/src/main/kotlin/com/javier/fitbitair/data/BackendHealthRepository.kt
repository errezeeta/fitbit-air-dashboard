package com.javier.fitbitair.data

import com.javier.fitbitair.domain.BackendApiClient
import com.javier.fitbitair.domain.DashboardState
import com.javier.fitbitair.domain.HealthRepository
import com.javier.fitbitair.domain.TrendRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

class BackendHealthRepository(
    private val apiClient: BackendApiClient,
    private val timezoneProvider: () -> String = { ZoneId.systemDefault().id },
) : HealthRepository {
    override suspend fun loadDashboard(range: TrendRange): DashboardState =
        withContext(Dispatchers.IO) {
            apiClient.loadDashboard(range, timezoneProvider())
        }
}
