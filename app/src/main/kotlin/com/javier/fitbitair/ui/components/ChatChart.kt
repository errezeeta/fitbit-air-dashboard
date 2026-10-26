package com.javier.fitbitair.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.domain.AssistantChart

/**
 * The chart the agent attached to a chat answer. It is deliberately small: it
 * restates numbers already in the conversation, so it only has to be readable
 * inside a bubble, not to replace the dashboard.
 */
@Composable
fun ChatMiniChart(chart: AssistantChart, modifier: Modifier = Modifier) {
    val values = chart.points.map { it.value.toFloat() }
    if (values.isEmpty()) return
    var selected by remember(chart) { mutableStateOf(chart.points.lastIndex) }
    val accent = MaterialTheme.colorScheme.tertiary
    val goalColor = MaterialTheme.colorScheme.secondary
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val selectedPoint = chart.points.getOrNull(selected) ?: chart.points.last()
    val showAxisLabels = chart.points.size <= 7

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(chart.title.ifBlank { "Datos del periodo" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (chart.unit.isNotBlank()) {
                    Text(chart.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedContent(
                targetState = selectedPoint,
                transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(150)) + slideInVertically(androidx.compose.animation.core.tween(180)) { it / 5 }) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(100))
                },
                label = "assistant-chart-selection",
            ) { point ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(point.label.ifBlank { "Punto seleccionado" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${compactNumber(point.value.toInt())}${chart.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    chart.goal?.let { goal ->
                        Text(
                            "Meta ${compactNumber(goal.toInt())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = goalColor,
                            modifier = Modifier.background(goalColor.copy(alpha = 0.10f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .pointerInput(chart) {
                        detectTapGestures(onTap = { offset ->
                            val slot = size.width / chart.points.size
                            selected = (offset.x / slot).toInt().coerceIn(0, chart.points.lastIndex)
                        })
                    },
            ) {
                val baselineY = size.height - if (showAxisLabels) 16.dp.toPx() else 5.dp.toPx()
                val topInset = 7.dp.toPx()
                val usable = baselineY - topInset
                val maximum = (values.maxOrNull() ?: 1f).coerceAtLeast(chart.goal?.toFloat() ?: 0f).coerceAtLeast(1f)
                val slot = size.width / values.size
                val barWidth = (slot * 0.62f).coerceAtMost(18.dp.toPx())

                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = baselineY - fraction * usable
                    drawLine(baselineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }

                if (chart.isBars) {
                    values.forEachIndexed { index, value ->
                        val centerX = slot * (index + 0.5f)
                        val height = (value / maximum) * usable
                        drawRoundRect(
                            color = accent.copy(alpha = if (index == selected) 0.10f else 0.045f),
                            topLeft = Offset(centerX - barWidth / 2f, topInset),
                            size = Size(barWidth, baselineY - topInset),
                            cornerRadius = CornerRadius(barWidth / 3f),
                        )
                        drawRoundRect(
                            color = if (index == selected) accent else accent.copy(alpha = 0.42f),
                            topLeft = Offset(centerX - barWidth / 2f, baselineY - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 3f),
                        )
                    }
                } else {
                    val points = values.mapIndexed { index, value ->
                        Offset(slot * (index + 0.5f), baselineY - (value / maximum) * usable)
                    }
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, accent, style = Stroke(width = 2.2.dp.toPx()))
                    points.forEachIndexed { index, point ->
                        drawCircle(if (index == selected) accent else accent.copy(alpha = 0.55f), radius = if (index == selected) 4.dp.toPx() else 2.5.dp.toPx(), center = point)
                    }
                    val point = points[selected]
                    drawLine(accent.copy(alpha = 0.20f), Offset(point.x, topInset), Offset(point.x, baselineY), 1.dp.toPx())
                    drawCircle(accent.copy(alpha = 0.16f), radius = 8.dp.toPx(), center = point)
                    drawCircle(accent, radius = 3.5.dp.toPx(), center = point)
                }

                chart.goal?.let { goal ->
                    val goalY = baselineY - (goal.toFloat() / maximum) * usable
                    drawLine(goalColor.copy(alpha = 0.8f), Offset(0f, goalY), Offset(size.width, goalY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)))
                }

                if (showAxisLabels) {
                    chart.points.forEachIndexed { index, point ->
                        if (point.label.isBlank()) return@forEachIndexed
                        val measured = measurer.measure(point.label, axisStyle.copy(fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal))
                        drawText(
                            measured,
                            topLeft = Offset(
                                (slot * (index + 0.5f) - measured.size.width / 2f).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)),
                                baselineY + 3.dp.toPx(),
                            ),
                        )
                    }
                }
            }

            Text(rangeSummary(chart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Toca el gráfico para consultar otro dato", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun rangeSummary(chart: AssistantChart): String {
    val values = chart.points.map { it.value }
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val unit = chart.unit.ifBlank { "unidades" }
    return "Mínimo ${compactNumber(minimum.toInt())} · máximo ${compactNumber(maximum.toInt())} $unit"
}

private fun compactNumber(value: Int): String = when {
    value >= 1_000 -> String.format("%.1f", value / 1_000.0).replace('.', ',') + " k"
    else -> value.toString()
}
