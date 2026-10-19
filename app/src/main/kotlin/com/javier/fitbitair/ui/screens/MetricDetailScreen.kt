package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.DashboardData
import com.javier.fitbitair.domain.MetricKind
import com.javier.fitbitair.domain.SeriesPoint
import com.javier.fitbitair.domain.TrendRange
import com.javier.fitbitair.domain.TrendStats
import com.javier.fitbitair.domain.chartTooltipDate
import com.javier.fitbitair.domain.distancePoints
import com.javier.fitbitair.domain.heartPoints
import com.javier.fitbitair.domain.sleepEfficiencyPercent
import com.javier.fitbitair.domain.sleepPoints
import com.javier.fitbitair.domain.stepPoints
import com.javier.fitbitair.ui.components.DistanceBarChart
import com.javier.fitbitair.ui.components.HeartRateSparkline
import com.javier.fitbitair.ui.components.SleepTrendChart
import com.javier.fitbitair.ui.components.SleepStagesChart
import com.javier.fitbitair.ui.components.StepsBarChart
import com.javier.fitbitair.ui.components.formatDistanceValue
import com.javier.fitbitair.ui.components.formatDurationValue
import com.javier.fitbitair.ui.components.formatStepsCount
import java.time.LocalDate

/**
 * The full picture for one metric: today's number against the period, the
 * chart with its range switch, the period arithmetic, and the day-by-day list
 * where every row opens that day.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailScreen(
    kind: MetricKind,
    data: DashboardData,
    range: TrendRange,
    stepGoal: Int,
    onRangeChange: (TrendRange) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val points = when (kind) {
        MetricKind.STEPS -> data.stepPoints()
        MetricKind.DISTANCE -> data.distancePoints()
        MetricKind.SLEEP -> data.sleepPoints()
        MetricKind.HEART -> data.heartPoints()
    }
    val stats = TrendStats.of(points)
    val todayValue = when (kind) {
        MetricKind.STEPS -> data.stepsToday
        MetricKind.DISTANCE -> data.distanceTodayMeters
        MetricKind.SLEEP -> data.sleep.asleepMinutes
        MetricKind.HEART -> data.restingHeartRate
    }.takeIf { it > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                kind.kicker,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(kind.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(range.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HeroCard(kind, todayValue, stats.average, data.previousRestingHeartRate, stepGoal)

        RangeChips(range, onRangeChange)

        ElevatedCard(shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (range == TrendRange.WEEK) "Últimos 7 días" else "Últimos 30 días",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                when (kind) {
                    MetricKind.STEPS -> StepsBarChart(
                        data.stepsTrend,
                        goal = stepGoal,
                        onOpenDay = onOpenDay,
                    )
                    MetricKind.DISTANCE -> DistanceBarChart(data.distanceTrend, onOpenDay = onOpenDay)
                    MetricKind.SLEEP -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SleepTrendChart(data.sleepTrend, onOpenDay = onOpenDay)
                        SleepStagesChart(data.sleepTrend, onOpenDay = onOpenDay)
                    }
                    MetricKind.HEART -> HeartRateSparkline(data.heartRateTrend, onOpenDay = onOpenDay)
                }
                Hint("Toca una barra para ver ese día · mantén pulsado para abrir su ficha")
            }
        }

        StatGrid(kind, stats, data, stepGoal)

        RecentDays(kind, points, onOpenDay)

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HeroCard(
    kind: MetricKind,
    todayValue: Int?,
    average: Double,
    previousHeartRate: Int?,
    stepGoal: Int,
) {
    val accent = kind.accent()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(kind.icon(), contentDescription = null, tint = accent)
                Text(
                    kind.heroLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    todayValue?.let { kind.formatValue(it) } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    kind.unitLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                kind.comparisonLine(todayValue, average, previousHeartRate, stepGoal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RangeChips(range: TrendRange, onRangeChange: (TrendRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendRange.entries.forEach { option ->
            FilterChip(
                selected = range == option,
                onClick = { onRangeChange(option) },
                label = { Text(if (option == TrendRange.WEEK) "7 días" else "30 días") },
            )
        }
    }
}

@Composable
private fun StatGrid(
    kind: MetricKind,
    stats: com.javier.fitbitair.domain.SeriesStats,
    data: DashboardData,
    stepGoal: Int,
) {
    val steps = data.stepsTrend.map { it.steps }
    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Resumen del periodo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            StatLine("Días con registro", "${stats.days}")
            StatLine("Media diaria", kind.formatValue(Math.round(stats.average).toInt()))
            if (kind == MetricKind.STEPS || kind == MetricKind.DISTANCE) {
                StatLine("Total del periodo", kind.formatValue(stats.total.toInt()))
            }
            stats.best?.let { best ->
                StatLine(
                    "Mejor día",
                    kind.formatValue(best.value),
                    best.date?.let(::chartTooltipDate),
                )
            }
            stats.worst?.let { worst ->
                StatLine(
                    "Día más bajo",
                    kind.formatValue(worst.value),
                    worst.date?.let(::chartTooltipDate),
                )
            }

            when (kind) {
                MetricKind.STEPS -> {
                    StatLine("Días cumpliendo la meta", "${TrendStats.daysOverGoal(steps, stepGoal)} de ${stats.days}")
                    StatLine(
                        "Mejor racha sobre la meta",
                        "${TrendStats.longestStreakOverGoal(steps, stepGoal)} días",
                    )
                    StatLine("Días con actividad", "${TrendStats.activeDayPercent(steps)} %")
                }
                MetricKind.SLEEP -> {
                    val variability = TrendStats.variabilityPercent(data.sleepTrend.map { it.summary.asleepMinutes })
                    variability?.let { StatLine("Regularidad del descanso", "±$it %", "variación respecto a tu media") }
                    val efficiency = sleepEfficiencyPercent(data.sleep.asleepMinutes, data.sleep.inBedMinutes)
                    efficiency?.let { StatLine("Eficiencia de anoche", "$it %", "tiempo dormido ÷ tiempo en cama") }
                }
                MetricKind.HEART -> {
                    StatLine("Rango del periodo", "${stats.worst?.value ?: 0} – ${stats.best?.value ?: 0} lpm")
                }
                MetricKind.DISTANCE -> {
                    StatLine("Media por día activo", kind.formatValue(activeAverage(data)))
                }
            }
        }
    }
}

private fun activeAverage(data: DashboardData): Int {
    val recorded = data.distanceTrend.map { it.meters }.filter { it > 0 }
    if (recorded.isEmpty()) return 0
    return recorded.sum() / recorded.size
}

@Composable
private fun RecentDays(kind: MetricKind, points: List<SeriesPoint>, onOpenDay: (LocalDate) -> Unit) {
    val recent = points.asReversed().take(10)
    if (recent.isEmpty()) return
    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Día a día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Toca un día para ver todo lo registrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            recent.forEach { point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = point.date != null) { point.date?.let(onOpenDay) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        point.date?.let(::chartTooltipDate) ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        kind.formatValue(point.value),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, note: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetricKind.accent(): Color = when (this) {
    MetricKind.STEPS -> MaterialTheme.colorScheme.primary
    MetricKind.DISTANCE -> MaterialTheme.colorScheme.secondary
    MetricKind.SLEEP -> MaterialTheme.colorScheme.tertiary
    MetricKind.HEART -> MaterialTheme.colorScheme.tertiary
}

private fun MetricKind.icon(): ImageVector = when (this) {
    MetricKind.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
    MetricKind.DISTANCE -> Icons.Rounded.Route
    MetricKind.SLEEP -> Icons.Rounded.Bedtime
    MetricKind.HEART -> Icons.Rounded.Favorite
}

private fun MetricKind.heroLabel(): String = when (this) {
    MetricKind.STEPS -> "PASOS DE HOY"
    MetricKind.DISTANCE -> "DISTANCIA DE HOY"
    MetricKind.SLEEP -> "ÚLTIMA NOCHE"
    MetricKind.HEART -> "ÚLTIMA LECTURA"
}

private fun MetricKind.unitLabel(): String = when (this) {
    MetricKind.STEPS -> "pasos"
    MetricKind.DISTANCE -> "recorridos"
    MetricKind.SLEEP -> "dormidas"
    MetricKind.HEART -> "lpm"
}

private fun MetricKind.formatValue(value: Int): String = when (this) {
    MetricKind.STEPS -> formatStepsCount(value)
    MetricKind.DISTANCE -> formatDistanceValue(value)
    MetricKind.SLEEP -> formatDurationValue(value)
    MetricKind.HEART -> "$value"
}

private fun MetricKind.comparisonLine(
    todayValue: Int?,
    average: Double,
    previousHeartRate: Int?,
    stepGoal: Int,
): String {
    if (todayValue == null) return "Sin lecturas en el periodo"
    val rounded = Math.round(average).toInt()
    return when (this) {
        MetricKind.HEART -> {
            val delta = (todayValue - (previousHeartRate ?: todayValue))
            when {
                previousHeartRate == null -> "Media del periodo: $rounded lpm"
                delta > 0 -> "Sube $delta lpm respecto a la semana anterior · media $rounded"
                delta < 0 -> "Baja ${-delta} lpm respecto a la semana anterior · media $rounded"
                else -> "Igual que la semana anterior · media $rounded"
            }
        }
        MetricKind.STEPS -> {
            val goalDelta = todayValue - stepGoal
            val goalText = if (goalDelta >= 0) "meta superada por ${formatStepsCount(goalDelta)}" else "faltan ${formatStepsCount(-goalDelta)} para la meta"
            "$goalText · media del periodo ${formatStepsCount(rounded)} pasos"
        }
        MetricKind.DISTANCE -> "Media del periodo: ${formatDistanceValue(rounded)}"
        MetricKind.SLEEP -> "Media del periodo: ${formatDurationValue(rounded)}"
    }
}
