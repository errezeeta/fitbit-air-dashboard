package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
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
    if (values.size < 2) return
    val accent = MaterialTheme.colorScheme.tertiary
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
    )
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    // Resolved before the Canvas: MaterialTheme cannot be read inside a draw lambda.
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val showEveryLabel = chart.points.size <= 7

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chart.title.ifBlank { "Datos del periodo" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (chart.unit.isNotBlank()) {
                    Text(
                        chart.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                val baselineY = size.height - 14.dp.toPx()
                val topInset = if (showEveryLabel) 14.dp.toPx() else 6.dp.toPx()
                val usable = baselineY - topInset
                val maximum = (values.maxOrNull() ?: 1f)
                    .coerceAtLeast(chart.goal?.toFloat() ?: 0f)
                    .coerceAtLeast(1f)
                val slot = size.width / values.size
                val barWidth = (slot * 0.62f).coerceAtMost(18.dp.toPx())

                drawLine(
                    baselineColor,
                    Offset(0f, baselineY),
                    Offset(size.width, baselineY),
                    1.dp.toPx(),
                )

                if (chart.isBars) {
                    values.forEachIndexed { index, value ->
                        val centerX = slot * (index + 0.5f)
                        val height = (value / maximum) * usable
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(centerX - barWidth / 2f, baselineY - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius((barWidth / 3f), (barWidth / 3f)),
                        )
                        if (showEveryLabel) {
                            val measured = measurer.measure(formatPoint(value), valueStyle.copy(color = accent))
                            drawText(
                                measured,
                                topLeft = Offset(
                                    (centerX - measured.size.width / 2f)
                                        .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)),
                                    (baselineY - height - measured.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                                ),
                            )
                        }
                    }
                } else {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = slot * (index + 0.5f)
                        val y = baselineY - (value / maximum) * usable
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, accent, style = Stroke(width = 2.dp.toPx()))
                    values.forEachIndexed { index, value ->
                        val x = slot * (index + 0.5f)
                        val y = baselineY - (value / maximum) * usable
                        drawCircle(accent, radius = 2.5.dp.toPx(), center = Offset(x, y))
                    }
                }

                chart.goal?.let { goal ->
                    val goalY = baselineY - (goal.toFloat() / maximum) * usable
                    drawLine(
                        accent.copy(alpha = 0.6f),
                        Offset(0f, goalY),
                        Offset(size.width, goalY),
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)),
                    )
                }

                if (showEveryLabel) {
                    chart.points.forEachIndexed { index, point ->
                        if (point.label.isBlank()) return@forEachIndexed
                        val measured = measurer.measure(point.label, axisStyle)
                        drawText(
                            measured,
                            topLeft = Offset(
                                (slot * (index + 0.5f) - measured.size.width / 2f)
                                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)),
                                baselineY + 3.dp.toPx(),
                            ),
                        )
                    }
                }
            }

            Text(
                rangeSummary(chart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatPoint(value: Float): String = compactNumber(value.toInt())

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
