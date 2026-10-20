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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
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
import com.javier.fitbitair.ui.components.PageHeader
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
        PageHeader("RESUMEN DIARIO", "Hoy", todayLabel(), Icons.Rounded.Home)

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
    var showStages by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader("RECUPERACIÓN", "Sueño", "Duración, horario y fases de cada noche", Icons.Rounded.Bedtime, accent = MaterialTheme.colorScheme.tertiary)
        SleepHeroCard(data.sleep, onOpen = { onOpenDetail(MetricKind.SLEEP) })
        SleepSummaryStrip(
            efficiency = efficiency,
            deepMinutes = data.sleep.stages.firstOrNull { it.kind == SleepStageKind.DEEP }?.minutes ?: 0,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showStages, onClick = { showStages = false }, label = { Text("Duración") })
            FilterChip(selected = showStages, onClick = { showStages = true }, label = { Text("Fases") })
        }
        TrendPanel(
            if (showStages) "Fases por noche" else "Duración por noche",
            range,
            onRangeChange,
            onOpenDetail = { onOpenDetail(MetricKind.SLEEP) },
        ) {
            if (data.sleepTrend.isEmpty()) {
                TrendEmptyState("Todavía no hay noches registradas", "El gráfico aparecerá cuando Google Health tenga datos de sueño.")
            } else if (showStages) {
                SleepStagesChart(data.sleepTrend, onOpenDay = onOpenDay)
            } else {
                SleepTrendChart(data.sleepTrend, onOpenDay = onOpenDay)
            }
        }
        InfoNote("La eficiencia es un cálculo propio (tiempo dormido ÷ tiempo en cama), no el Sleep Score de Fitbit.")
    }
}

@Composable
private fun SleepSummaryStrip(efficiency: Int?, deepMinutes: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("EFICIENCIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(efficiency?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("tiempo dormido / en cama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SUEÑO PROFUNDO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(deepMinutes.takeIf { it > 0 }?.let(::formatDuration) ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (deepMinutes > 0) "fase registrada" else "sin registro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    var showDistance by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader("MOVIMIENTO", "Actividad", "Pasos, recorrido y pulso en reposo", Icons.AutoMirrored.Rounded.DirectionsWalk)
        StepsGoalCard(
            steps = data.stepsToday,
            goal = stepGoal,
            distanceMeters = data.distanceTodayMeters,
            onEdit = { showGoalDialog = true },
            onClick = { onOpenDetail(MetricKind.STEPS) },
            onOpenDistance = { onOpenDetail(MetricKind.DISTANCE) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showDistance, onClick = { showDistance = false }, label = { Text("Pasos") })
            FilterChip(selected = showDistance, onClick = { showDistance = true }, label = { Text("Distancia") })
        }
        TrendPanel(
            if (showDistance) "Distancia por día" else "Pasos por día",
            range,
            onRangeChange,
            onOpenDetail = { onOpenDetail(if (showDistance) MetricKind.DISTANCE else MetricKind.STEPS) },
        ) {
            if (!showDistance) {
                if (data.stepsTrend.isEmpty()) {
                    TrendEmptyState("Todavía no hay datos de pasos", "Las lecturas diarias aparecerán aquí.")
                } else {
                    StepsBarChart(data.stepsTrend, goal = stepGoal, onOpenDay = onOpenDay)
                }
            } else if (data.distanceTrend.isEmpty()) {
                TrendEmptyState("Todavía no hay distancias", "El gráfico aparecerá cuando se registren recorridos.")
            } else {
                DistanceBarChart(data.distanceTrend, onOpenDay = onOpenDay)
            }
        }
        RestingHeartDetail(data, onOpenDetail = { onOpenDetail(MetricKind.HEART) }, onOpenDay = onOpenDay)
        Spacer(Modifier.height(8.dp))
    }
    if (showGoalDialog) {
        StepGoalDialog(stepGoal, { showGoalDialog = false }, { value -> onStepGoalChange(value); showGoalDialog = false })
    }
}

@Composable
private fun SleepHeroCard(sleep: SleepSummary, onOpen: () -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        modifier = Modifier.fillMaxWidth().clickableScaled(onClick = onOpen),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(foreground.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = foreground)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("ÚLTIMA NOCHE", style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.72f), fontWeight = FontWeight.Bold)
                    Text(if (sleep.asleepMinutes > 0) "Tiempo dormido" else "Sin registro reciente", style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.SemiBold)
                }
            }
            if (sleep.asleepMinutes <= 0) {
                Text("No hay un registro de sueño reciente.", style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.82f))
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("${sleep.asleepMinutes / 60}", style = MaterialTheme.typography.displayMedium, color = foreground, fontWeight = FontWeight.SemiBold)
                    Text(" h ", style = MaterialTheme.typography.titleLarge, color = foreground)
                    Text("${sleep.asleepMinutes % 60}", style = MaterialTheme.typography.displayMedium, color = foreground, fontWeight = FontWeight.SemiBold)
                    Text(" min", style = MaterialTheme.typography.titleLarge, color = foreground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SleepTimePoint("ACOSTARSE", sleep.bedtime, Modifier.weight(1f))
                    SleepTimePoint("DESPERTAR", sleep.wakeTime, Modifier.weight(1f))
                }
                if (sleep.stages.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SleepStageBar(sleep.stages, Modifier.fillMaxWidth())
                        SleepStageLegend(sleep.stages)
                    }
                }
            }
            Text("Toca para ver el detalle de esta noche →", style = MaterialTheme.typography.labelSmall, color = foreground.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SleepTimePoint(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f), fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepsGoalCard(
    steps: Int,
    goal: Int,
    distanceMeters: Int,
    onEdit: () -> Unit,
    onClick: () -> Unit = {},
    onOpenDistance: () -> Unit,
) {
    val progress = stepProgress(steps, goal)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        progress,
        animationSpec = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "activity-goal-progress",
    )
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().clickableScaled(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("PASOS DE HOY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(formatStepsCount(steps), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("de ${formatStepsCount(goal)} pasos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButtonLabel("Cambiar meta", onEdit)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)))
            Row(
                modifier = Modifier.fillMaxWidth().clickableScaled(onClick = onOpenDistance),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Route, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f)) {
                    Text("DISTANCIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text("Recorrido de hoy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    distanceMeters.takeIf { it > 0 }?.let(::formatDistanceValue) ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
