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
import com.javier.fitbitair.ui.components.HeartRateSparkline
import com.javier.fitbitair.ui.components.SleepStageBar
import com.javier.fitbitair.ui.components.SleepStageLegend
import com.javier.fitbitair.ui.components.SleepTrendChart
import com.javier.fitbitair.ui.components.StepsBarChart
import com.javier.fitbitair.ui.components.formatDistanceValue

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
            Text("TU RESUMEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("Hoy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(todayLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SleepHeroCard(data.sleep, onOpen = onOpenSleep)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StepsMetricCard(
                steps = data.stepsToday,
                goal = stepGoal,
                modifier = Modifier.weight(1f),
                onEditGoal = { showGoalDialog = true },
            )
            RestingHeartCard(
                current = data.restingHeartRate,
                previous = data.previousRestingHeartRate,
                modifier = Modifier.weight(1f),
                onClick = {},
            )
        }

        DistanceCard(
            meters = data.distanceTodayMeters,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenDetail(MetricKind.DISTANCE) },
        )

        TrendPanel(
            title = if (range == TrendRange.WEEK) "Pasos esta semana" else "Pasos últimos 30 días",
            range = range,
            onRangeChange = onRangeChange,
            onOpenDetail = { onOpenDetail(MetricKind.STEPS) },
        ) {
            StepsBarChart(data.stepsTrend, onOpenDay = onOpenDay)
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
        ScreenHeading("DESCANSO", "Sueño", "Duración y fases · anoche")
        SleepHeroCard(data.sleep, onOpen = {})
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
            SleepTrendChart(data.sleepTrend, onOpenDay = onOpenDay)
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
            StepsBarChart(data.stepsTrend, onOpenDay = onOpenDay)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("SUEÑO · ANOCHE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f), fontWeight = FontWeight.SemiBold)
                    Text("Un descanso continuo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    efficiency?.let { "Eficiencia $it%*" } ?: "Eficiencia —",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${sleep.asleepMinutes / 60}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                Text(" h ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${sleep.asleepMinutes % 60}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                Text(" min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("${sleep.bedtime} — ${sleep.wakeTime} · hora local", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            SleepStageBar(sleep.stages, Modifier.fillMaxWidth())
            SleepStageLegend(sleep.stages)
        }
    }
}

@Composable
private fun DistanceCard(meters: Int, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    if (meters <= 0) return
    Card(
        modifier = modifier.clickable(onClick = onClick),
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
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        formatDistanceValue(meters),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "recorridos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsMetricCard(steps: Int, goal: Int, modifier: Modifier, onEditGoal: () -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Pasos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(steps.toString().reversed().chunked(3).joinToString(" ").reversed(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
            Text("de ${goal.toString()} · meta diaria", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { stepProgress(steps, goal) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            TextButtonLabel("Editar meta", onEditGoal)
        }
    }
}

@Composable
private fun StepsGoalCard(steps: Int, goal: Int, onEdit: () -> Unit, onClick: () -> Unit = {}) {
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.clickable(onClick = onClick)) {
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
                Text(steps.toString().reversed().chunked(3).joinToString(" ").reversed(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("de ${goal} pasos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButtonLabel("Editar meta manual", onEdit)
            }
        }
    }
}

@Composable
private fun RestingHeartCard(current: Int, previous: Int?, modifier: Modifier, onClick: () -> Unit) {
    val validCurrent = current.takeIf { it > 0 }
    val delta = validCurrent?.let { trendDelta(it, previous) }
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text("Pulso en reposo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(validCurrent?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("lpm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                delta?.let { (if (it > 0) "+" else "") + "$it vs. semana anterior" } ?: if (validCurrent == null) "Sin lectura reciente" else "Tendencia disponible",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta != null && delta <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun RestingHeartDetail(
    data: DashboardData,
    onOpenDetail: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val current = data.restingHeartRate.takeIf { it > 0 }
    ElevatedCard(shape = RoundedCornerShape(26.dp), modifier = Modifier.clickable(onClick = onOpenDetail)) {
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
