package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.DailyRestingHeartRate
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.SleepStage
import com.javier.fitbitair.domain.SleepStageKind

@Composable
fun SleepStageBar(stages: List<SleepStage>, modifier: Modifier = Modifier) {
    val total = stages.sumOf { it.minutes }.coerceAtLeast(1)
    Row(
        modifier = modifier
            .height(10.dp)
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .width(7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(stageColor(stage.kind)),
                )
                Text(stage.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun StepsBarChart(data: List<DailySteps>, modifier: Modifier = Modifier) {
    val maxSteps = data.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1
    val dense = data.size >= 20
    val gap = if (dense) 2.dp else 6.dp
    val barWidth = if (dense) 6.dp else 14.dp
    val latestBarWidth = if (dense) 8.dp else 18.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { index, point ->
            val fraction = (point.steps.toFloat() / maxSteps).coerceIn(0.04f, 1f)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(if (index == data.lastIndex) latestBarWidth else barWidth)
                        .height(88.dp * fraction)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(
                            if (index == data.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.44f),
                        ),
                )
                Spacer(Modifier.height(7.dp))
                if (point.label.isNotEmpty()) {
                    Text(point.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun HeartRateSparkline(data: List<DailyRestingHeartRate>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.fillMaxWidth().height(58.dp).padding(vertical = 4.dp)) {
        val values = data.map { it.beatsPerMinute.toFloat() }
        val minValue = (values.minOrNull() ?: 0f) - 2f
        val maxValue = (values.maxOrNull() ?: 1f) + 2f
        val span = (maxValue - minValue).coerceAtLeast(1f)
        val stepX = if (values.size <= 1) size.width else size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / span * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / span * size.height)
            drawCircle(color, radius = if (index == values.lastIndex) 4.dp.toPx() else 2.dp.toPx(), center = Offset(x, y))
        }
    }
}
