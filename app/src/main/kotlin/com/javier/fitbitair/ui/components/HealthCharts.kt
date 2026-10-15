package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.domain.DailyDistance
import com.javier.fitbitair.domain.DailyRestingHeartRate
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.SleepStage
import com.javier.fitbitair.domain.SleepStageKind
import com.javier.fitbitair.domain.chartTooltipDate
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val esNumber: NumberFormat by lazy {
    NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES"))
}

fun formatStepsCount(value: Int): String = esNumber.format(value)

fun formatDistanceValue(meters: Int): String {
    if (meters < 1_000) return "$meters m"
    val kilometres = meters / 1_000.0
    return String.format(Locale.forLanguageTag("es-ES"), "%.1f km", kilometres)
}

fun formatDurationValue(minutes: Int): String {
    if (minutes <= 0) return "—"
    return "${minutes / 60} h ${minutes % 60} min"
}

private data class TrendEntry(
    val value: Int,
    val axisLabel: String,
    val date: LocalDate?,
)

/**
 * Bar charts render in one Canvas and are tappable: pointing at a bar lifts it
 * and names the day that value belongs to. A dashed average line runs across
 * the plot so the trend reads at a glance.
 */
@Composable
fun StepsBarChart(data: List<DailySteps>, modifier: Modifier = Modifier) {
    TrendBarChart(
        entries = data.map { TrendEntry(it.steps, it.label, it.date) },
        tooltipDetail = { steps -> "${formatStepsCount(steps)} pasos" },
        highlightColor = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
        averageLabel = averageTooltip(data.map { it.steps }) { "${formatStepsCount(it)} pasos" },
        modifier = modifier,
    )
}

@Composable
fun DistanceBarChart(data: List<DailyDistance>, modifier: Modifier = Modifier) {
    TrendBarChart(
        entries = data.map { TrendEntry(it.meters, it.label, it.date) },
        tooltipDetail = ::formatDistanceValue,
        highlightColor = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
        averageLabel = averageTooltip(data.map { it.meters }) { formatDistanceValue(it) },
        modifier = modifier,
    )
}

/** "media 12 400 pasos"-style chip fed to the chart header. */
private inline fun averageTooltip(values: List<Int>, format: (Int) -> String): String {
    if (values.isEmpty()) return ""
    val average = values.sum().toDouble() / values.size
    return "media ${format(Math.round(average).toInt())}"
}

@Composable
/** Sleep trend shares the same interaction with minutes as the value. */
fun SleepTrendChart(values: List<Int>, axisLabels: List<String>, modifier: Modifier = Modifier) {
    TrendBarChart(
        entries = values.mapIndexed { index, asleep ->
            TrendEntry(asleep, axisLabels.getOrElse(index) { "" }, null)
        },
        tooltipDetail = ::formatDurationValue,
        highlightColor = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f),
        averageLabel = "",
        modifier = modifier,
    )
}

@Composable
private fun TrendBarChart(
    entries: List<TrendEntry>,
    tooltipDetail: (Int) -> String,
    highlightColor: Color,
    trackColor: Color,
    averageLabel: String,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxWidth().height(128.dp)) {}
        return
    }
    var selected by remember(entries) { mutableStateOf(entries.lastIndex) }
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val axisColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )
    val plotHeight = 108.dp

    Column(modifier = modifier.fillMaxWidth()) {
        SelectedTooltip(entries, selected, tooltipDetail, highlightColor, averageLabel)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight + 24.dp)
                .pointerInput(entries) {
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width / entries.size))
                            .toInt()
                            .coerceIn(0, entries.lastIndex)
                    }
                },
        ) {
            val plotWidth = size.width
            val baselineY = plotHeight.toPx()
            val slot = plotWidth / entries.size
            val barWidth = (slot * if (entries.size > 16) 0.62f else 0.72f).coerceAtMost(22.dp.toPx())
            val maximum = entries.maxOf { it.value }.coerceAtLeast(1)
            val average = entries.sumOf { it.value }.toFloat() / entries.size

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = baselineY - fraction * plotHeight.toPx()
                drawLine(
                    gridColor,
                    Offset(0f, y),
                    Offset(plotWidth, y),
                    1.dp.toPx(),
                )
            }
            drawLine(
                highlightColor,
                Offset(0f, baselineY - (average / maximum) * plotHeight.toPx()),
                Offset(plotWidth, baselineY - (average / maximum) * plotHeight.toPx()),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )

            entries.forEachIndexed { index, entry ->
                val fraction = (entry.value.toFloat() / maximum).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val barHeight = fraction * plotHeight.toPx()
                    val top = baselineY - barHeight
                    val barColor = if (index == selected) highlightColor else trackColor
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barColor, barColor.copy(alpha = 0.55f)),
                            startY = top,
                            endY = baselineY,
                        ),
                        topLeft = Offset(slot * (index + 0.5f) - barWidth / 2, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
                val label = entry.axisLabel
                if (label.isNotEmpty()) {
                    val emphasized = index == selected
                    val measured = measurer.measure(
                        label,
                        labelStyle.copy(
                            color = if (emphasized) highlightColor else labelStyle.color,
                            fontWeight = if (emphasized) FontWeight.SemiBold else labelStyle.fontWeight,
                        ),
                    )
                    val x = (slot * (index + 0.5f) - measured.size.width / 2f)
                        .coerceIn(0f, (plotWidth - measured.size.width).coerceAtLeast(0f))
                    drawText(measured, topLeft = Offset(x, baselineY + 8.dp.toPx()))
                }
            }

            drawLine(
                axisColor,
                Offset(0f, baselineY),
                Offset(plotWidth, baselineY),
                1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SelectedTooltip(
    entries: List<TrendEntry>,
    selected: Int,
    tooltipDetail: (Int) -> String,
    highlightColor: Color,
    averageLabel: String,
) {
    val entry = entries.getOrNull(selected) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.date?.let(::chartTooltipDate) ?: entry.axisLabel.ifEmpty { "Hoy" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tooltipDetail(entry.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = highlightColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (averageLabel.isNotEmpty()) {
            Text(
                averageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun SleepStageBar(stages: List<SleepStage>, modifier: Modifier = Modifier) {
    val total = stages.sumOf { it.minutes }.coerceAtLeast(1)
    Row(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        stages.forEach { stage ->
            if (stage.minutes > 0) {
                Box(
                    Modifier
                        .weight(stage.minutes.toFloat() / total)
                        .fillMaxHeight()
                        .background(stageColor(stage.kind)),
                )
            }
        }
    }
}

@Composable
fun SleepStageLegend(stages: List<SleepStage>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEach { stage ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(stageColor(stage.kind)),
                )
                Text(
                    stage.kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun stageColor(kind: SleepStageKind) = when (kind) {
    SleepStageKind.LIGHT -> MaterialTheme.colorScheme.primary
    SleepStageKind.DEEP -> MaterialTheme.colorScheme.tertiary
    SleepStageKind.REM -> MaterialTheme.colorScheme.secondary
    SleepStageKind.RESTLESS -> MaterialTheme.colorScheme.outline
    SleepStageKind.AWAKE -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
fun HeartRateSparkline(data: List<DailyRestingHeartRate>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.tertiary
    val fillColor = lineColor.copy(alpha = 0.22f)
    val axisColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val values = data.map { it.beatsPerMinute.toFloat() }
        val minimum = (values.minOrNull() ?: 0f) - 3f
        val maximum = (values.maxOrNull() ?: 1f) + 3f
        val span = (maximum - minimum).coerceAtLeast(1f)
        val stepX = if (values.size <= 1) size.width / 2 else size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(index * stepX, size.height - ((value - minimum) / span * size.height))
        }

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            drawLine(
                axisColor.copy(alpha = 0.14f),
                Offset(0f, fraction * size.height),
                Offset(size.width, fraction * size.height),
                1.dp.toPx(),
            )
        }
        drawPath(
            Path().apply {
                moveTo(points.first().x, size.height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height)
                close()
            },
            Brush.verticalGradient(listOf(fillColor, Color.Transparent), 0f, size.height),
        )
        drawPath(
            Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            },
            lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        points.forEachIndexed { index, point ->
            drawCircle(
                color = lineColor,
                radius = if (index == points.lastIndex) 5.dp.toPx() else 2.4.dp.toPx(),
                center = point,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "mín ${data.minOf { it.beatsPerMinute }} lpm",
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "máx ${data.maxOf { it.beatsPerMinute }} lpm",
            style = labelStyle,
            color = lineColor,
        )
    }
}
