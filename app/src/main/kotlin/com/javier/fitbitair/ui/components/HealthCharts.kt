package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.domain.DailyDistance
import com.javier.fitbitair.domain.DailyRestingHeartRate
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.SleepStage
import com.javier.fitbitair.domain.SleepStageKind

/**
 * Bar charts render in one Canvas: every value maps against the plot area
 * instead of a per-bar Column, so tick labels stay centred under their bar and
 * a highlighted "today" bar cannot overpaint its neighbours.
 */
@Composable
fun StepsBarChart(data: List<DailySteps>, modifier: Modifier = Modifier) {
    TrendBarChart(
        values = data.map { it.steps },
        labels = data.map { it.label },
        highlightColor = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
        axisColor = MaterialTheme.colorScheme.outline,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun DistanceBarChart(data: List<DailyDistance>, modifier: Modifier = Modifier) {
    TrendBarChart(
        values = data.map { it.meters },
        labels = data.map { it.label },
        highlightColor = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f),
        axisColor = MaterialTheme.colorScheme.outline,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TrendBarChart(
    values: List<Int>,
    labels: List<String>,
    highlightColor: Color,
    trackColor: Color,
    axisColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        Box(modifier.fillMaxWidth().height(124.dp))
        return
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontSize = 10.sp)
    val plotHeight = 104.dp
    val tickArea = 26.dp
    Canvas(modifier = modifier.fillMaxWidth().height(plotHeight + tickArea)) {
        val maximum = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val plotWidth = size.width
        val baselineY = plotHeight.toPx()
        val slot = plotWidth / values.size
        val barWidth = (slot * if (values.size > 16) 0.60f else 0.70f).coerceAtMost(22.dp.toPx())

        // three faint guides instead of a dense grid
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = baselineY - fraction * plotHeight.toPx()
            drawLine(axisColor.copy(alpha = 0.15f), Offset(0f, y), Offset(plotWidth, y), 1.dp.toPx())
        }

        values.forEachIndexed { index, value ->
            val fraction = (value.toFloat() / maximum).coerceIn(0f, 1f)
            if (fraction > 0f) {
                val barHeight = fraction * plotHeight.toPx()
                val left = slot * (index + 0.5f) - barWidth / 2
                drawRoundRect(
                    color = if (index == values.lastIndex) highlightColor else trackColor,
                    topLeft = Offset(left, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
            val label = labels.getOrNull(index) ?: ""
            if (label.isNotEmpty()) {
                val measured = measurer.measure(label, labelStyle)
                val x = (slot * (index + 0.5f) - measured.size.width / 2f)
                    .coerceIn(0f, (plotWidth - measured.size.width).coerceAtLeast(0f))
                drawText(measured, topLeft = Offset(x, baselineY + 7.dp.toPx()))
            }
        }

        drawLine(axisColor, Offset(0f, baselineY), Offset(plotWidth, baselineY), 1.5.dp.toPx(), StrokeCap.Round)
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
