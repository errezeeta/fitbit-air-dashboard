package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.DashboardData
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.MetricKind
import com.javier.fitbitair.domain.SleepSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.javier.fitbitair.domain.SleepStageKind
import com.javier.fitbitair.domain.TrendRange
import com.javier.fitbitair.domain.sleepEfficiencyPercent
import com.javier.fitbitair.domain.stepProgress
import com.javier.fitbitair.domain.trendDelta
import com.javier.fitbitair.ui.components.DistanceBarChart
import com.javier.fitbitair.ui.components.StaggeredAppear
import com.javier.fitbitair.ui.components.clickableScaled
import com.javier.fitbitair.ui.components.HeartRateSparkline
import com.javier.fitbitair.ui.components.SleepStageBar
import com.javier.fitbitair.ui.components.SleepStageLegend
import com.javier.fitbitair.ui.components.SleepStagesChart
import com.javier.fitbitair.ui.components.SleepTrendChart
import com.javier.fitbitair.ui.components.StepsBarChart
import com.javier.fitbitair.ui.components.formatDistanceValue
import com.javier.fitbitair.ui.components.formatStepsCount

@Composable
fun HomeScreen(
    data: DashboardData,
    stepGoal: Int,
    range: TrendRange,
    onRangeChange: (TrendRange) -> Unit,
    onOpenSleep: () -> Unit,
    onStepGoalChange: (Int) -> Unit,
    onOpenDetail: (MetricKind) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    var showGoalDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text("RESUMEN DIARIO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("Hoy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(todayLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        StaggeredAppear(index = 1) {
            TodayOverviewCard(
                data = data,
                stepGoal = stepGoal,
                onEditGoal = { showGoalDialog = true },
                onOpenSleep = onOpenSleep,
                onOpenHeart = { onOpenDetail(MetricKind.HEART) },
                onOpenDistance = { onOpenDetail(MetricKind.DISTANCE) },
            )
        }

        StaggeredAppear(index = 2) {
            TrendPanel(
                title = if (range == TrendRange.WEEK) "Pasos esta semana" else "Pasos últimos 30 días",
                range = range,
                onRangeChange = onRangeChange,
                onOpenDetail = { onOpenDetail(MetricKind.STEPS) },
            ) {
                if (data.stepsTrend.isEmpty()) {
                    TrendEmptyState("Todavía no hay datos de pasos", "Cuando Google Health tenga registros, aparecerán aquí.")
                } else {
                    StepsBarChart(data.stepsTrend, goal = stepGoal, onOpenDay = onOpenDay)
                }
            }
        }

    }

    if (showGoalDialog) {
        StepGoalDialog(
            currentGoal = stepGoal,
            onDismiss = { showGoalDialog = false },
            onSave = { newGoal ->
                onStepGoalChange(newGoal)
                showGoalDialog = false
            },
        )
    }
}

@Composable
private fun TodayOverviewCard(
    data: DashboardData,
    stepGoal: Int,
    onEditGoal: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenHeart: () -> Unit,
    onOpenDistance: () -> Unit,
) {
    val progress = stepProgress(data.stepsToday, stepGoal)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "today-step-progress",
    )
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "PASOS DE HOY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatStepsCount(data.stepsToday),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        "de ${formatStepsCount(stepGoal)} pasos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(7.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                    TextButtonLabel("Ajustar objetivo", onEditGoal)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(78.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 7.dp,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TodayMetricTile(
                    label = "SUEÑO",
                    value = data.sleep.asleepMinutes.takeIf { it > 0 }?.let(::formatDuration) ?: "—",
                    note = if (data.sleep.asleepMinutes > 0) "última noche" else "sin registro",
                    icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    onClick = onOpenSleep,
                    modifier = Modifier.weight(1f),
                )
                TodayMetricTile(
                    label = "PULSO",
                    value = data.restingHeartRate.takeIf { it > 0 }?.let { "$it lpm" } ?: "—",
                    note = "en reposo",
                    icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    onClick = onOpenHeart,
                    modifier = Modifier.weight(1f),
                )
                TodayMetricTile(
                    label = "DISTANCIA",
                    value = data.distanceTodayMeters.takeIf { it > 0 }?.let(::formatDistanceValue) ?: "—",
                    note = "hoy",
                    icon = { Icon(Icons.Rounded.Route, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    onClick = onOpenDistance,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TodayMetricTile(
    label: String,
    value: String,
    note: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f))
            .clickableScaled(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SleepScreen(
    data: DashboardData,
    range: TrendRange,
    onRangeChange: (TrendRange) -> Unit,
    onOpenDetail: (MetricKind) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val efficiency = sleepEfficiencyPercent(data.sleep.asleepMinutes, data.sleep.inBedMinutes)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading("DESCANSO", "Sueño", "Duración y fases de tus noches")
        SleepHeroCard(data.sleep, onOpen = { onOpenDetail(MetricKind.SLEEP) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Eficiencia*", efficiency?.let { "$it%" } ?: "—", "cálculo de la app", Modifier.weight(1f))
            StatCard("Sueño profundo", formatDuration(data.sleep.stages.firstOrNull { it.kind == SleepStageKind.DEEP }?.minutes ?: 0), "fase registrada", Modifier.weight(1f))
        }
        TrendPanel(
            "Duración del sueño",
            range,
            onRangeChange,
            onOpenDetail = { onOpenDetail(MetricKind.SLEEP) },
        ) {
            if (data.sleepTrend.isEmpty()) {
                TrendEmptyState("Todavía no hay noches registradas", "El gráfico se mostrará cuando haya datos de sueño.")
            } else {
                SleepTrendChart(data.sleepTrend, onOpenDay = onOpenDay)
            }
        }
        TrendPanel(
            "Fases de cada noche",
            range,
            onRangeChange = {},
            onOpenDetail = { onOpenDetail(MetricKind.SLEEP) },
        ) {
            if (data.sleepTrend.isEmpty()) {
                TrendEmptyState("Sin fases de sueño", "Las fases aparecerán cuando se registre una noche.")
            } else {
                SleepStagesChart(data.sleepTrend, onOpenDay = onOpenDay)
            }
        }
        InfoNote("*La eficiencia es un cálculo propio (tiempo dormido ÷ tiempo en cama). No es el Sleep Score de Fitbit.")
    }
}

@Composable
fun ActivityScreen(
    data: DashboardData,
    stepGoal: Int,
    range: TrendRange,
    onRangeChange: (TrendRange) -> Unit,
    onStepGoalChange: (Int) -> Unit,
    onOpenDetail: (MetricKind) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    var showGoalDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading("MOVIMIENTO", "Actividad", "Pasos, distancia y pulso en reposo")
        StepsGoalCard(data.stepsToday, stepGoal, onEdit = { showGoalDialog = true }, onClick = { onOpenDetail(MetricKind.STEPS) })
        DistanceCard(
            meters = data.distanceTodayMeters,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenDetail(MetricKind.DISTANCE) },
        )
        TrendPanel(
            "Pasos por día",
            range,
            onRangeChange,
            onOpenDetail = { onOpenDetail(MetricKind.STEPS) },
        ) {
            if (data.stepsTrend.isEmpty()) {
                TrendEmptyState("Todavía no hay datos de pasos", "Las lecturas diarias aparecerán aquí.")
            } else {
                StepsBarChart(data.stepsTrend, goal = stepGoal, onOpenDay = onOpenDay)
            }
        }
        if (data.distanceTrend.isNotEmpty()) {
            TrendPanel(
                "Distancia por día",
                range,
                onRangeChange = {},
                onOpenDetail = { onOpenDetail(MetricKind.DISTANCE) },
            ) {
                DistanceBarChart(data.distanceTrend, onOpenDay = onOpenDay)
            }
        }
        RestingHeartDetail(data, onOpenDetail = { onOpenDetail(MetricKind.HEART) }, onOpenDay = onOpenDay)
        Text(
            "Lecturas del periodo seleccionado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp),
        )
    }
    if (showGoalDialog) {
        StepGoalDialog(stepGoal, { showGoalDialog = false }, { value -> onStepGoalChange(value); showGoalDialog = false })
    }
}

@Composable
private fun SleepHeroCard(sleep: SleepSummary, onOpen: () -> Unit) {
    val efficiency = sleepEfficiencyPercent(sleep.asleepMinutes, sleep.inBedMinutes)
    Card(
        modifier = Modifier.fillMaxWidth().clickableScaled(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (sleep.asleepMinutes > 0) "SUEÑO · ANOCHE" else "SUEÑO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (sleep.asleepMinutes > 0) "Tiempo total dormido" else "Sin registro reciente",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                efficiency?.let { percent ->
                    Text(
                        "Eficiencia $percent%*",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            if (sleep.asleepMinutes <= 0) {
                Text(
                    "No hay un registro de sueño reciente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${sleep.asleepMinutes / 60}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(" h ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${sleep.asleepMinutes % 60}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(" min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("${sleep.bedtime} — ${sleep.wakeTime} · hora local", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                if (sleep.stages.isNotEmpty()) {
                    SleepStageBar(sleep.stages, Modifier.fillMaxWidth())
                    SleepStageLegend(sleep.stages)
                }
            }
        }
    }
}

@Composable
private fun DistanceCard(meters: Int, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    if (meters <= 0) return
    Card(
        modifier = modifier.clickableScaled(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Rounded.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Distancia de hoy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f))
                    Text(
                        formatDistanceValue(meters),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
            }
        }
    }
}

@Composable
private fun StepsGoalCard(steps: Int, goal: Int, onEdit: () -> Unit, onClick: () -> Unit = {}) {
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.clickableScaled(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val progress = stepProgress(steps, goal)
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(progress, label = "step-progress")
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 9.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("PASOS DE HOY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatStepsCount(steps), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("de ${formatStepsCount(goal)} pasos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButtonLabel("Editar meta", onEdit)
            }
        }
    }
}

@Composable
private fun HeartMiniStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RestingHeartDetail(
    data: DashboardData,
    onOpenDetail: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val current = data.restingHeartRate.takeIf { it > 0 }
    ElevatedCard(shape = RoundedCornerShape(26.dp), modifier = Modifier.clickableScaled(onClick = onOpenDetail)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("SALUD CARDIOVASCULAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pulso en reposo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("lpm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                current?.let { value ->
                    trendDelta(value, data.previousRestingHeartRate)?.let { delta ->
                        Text("${if (delta > 0) "+" else ""}$delta esta semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            HeartRateSparkline(data.heartRateTrend, onOpenDay = onOpenDay, modifier = Modifier.fillMaxWidth())
            if (data.heartRateTrend.isNotEmpty()) {
                val readings = data.heartRateTrend.map { it.beatsPerMinute }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HeartMiniStat("Media", "${Math.round(readings.average())} lpm")
                    HeartMiniStat("Mínimo", "${readings.min()} lpm")
                    HeartMiniStat("Máximo", "${readings.max()} lpm")
                    HeartMiniStat("Lecturas", "${readings.size}")
                }
                com.javier.fitbitair.domain.TrendStats.drift(readings)?.let { drift ->
                    val text = when (drift.direction) {
                        com.javier.fitbitair.domain.TrendStats.Direction.RISING ->
                            "Subiendo ${drift.beatsPerMinute} lpm en la segunda mitad del periodo"
                        com.javier.fitbitair.domain.TrendStats.Direction.FALLING ->
                            "Bajando ${drift.beatsPerMinute} lpm en la segunda mitad del periodo"
                        com.javier.fitbitair.domain.TrendStats.Direction.STABLE ->
                            "Estable entre la primera y la segunda mitad del periodo"
                    }
                    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Lectura informativa · no es una evaluación médica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendPanel(
    title: String,
    range: TrendRange,
    onRangeChange: (TrendRange) -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("TENDENCIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrendRange.entries.forEach { option ->
                        FilterChip(
                            selected = range == option,
                            onClick = { onRangeChange(option) },
                            label = { Text(if (option == TrendRange.WEEK) "7d" else "30d") },
                        )
                    }
                }
            }
            chart()
            onOpenDetail?.let { open ->
                TextButtonLabel("Ver detalle del periodo →", open)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, note: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScreenHeading(kicker: String, title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepGoalDialog(currentGoal: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember(currentGoal) { mutableStateOf(currentGoal.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta de pasos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solo se guarda esta preferencia, no un historial de métricas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit).take(6) },
                    label = { Text("Pasos diarios") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let(onSave) }) { Text("Guardar") }
        },
        dismissButton = { TextButtonLabel("Cancelar", onDismiss) },
    )
}

@Composable
private fun TextButtonLabel(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TrendEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

private fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "—"
    return "${minutes / 60} h ${minutes % 60} min"
}

private fun todayLabel(): String = LocalDate.now()
    .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES")))
    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-ES")) }
