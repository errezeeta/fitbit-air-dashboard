package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.domain.DailySleep
import com.javier.fitbitair.domain.SleepStageKind
import com.javier.fitbitair.domain.SleepSummary
import com.javier.fitbitair.domain.chartTooltipDate
import java.time.LocalDate

private data class StageBand(val kind: SleepStageKind, val label: String, val minutes: Int)

/**
 * How each night was composed, not just how long it lasted: deep, REM, light and
 * awake minutes stacked per night. The total alone hides the nights that only
 * look good because they are long.
 */
@Composable
fun SleepStagesChart(
    nights: List<DailySleep>,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (nights.isEmpty()) return
    var selected by remember(nights) { mutableStateOf(nights.lastIndex) }
    val measurer = rememberTextMeasurer()
    val deepColor = MaterialTheme.colorScheme.tertiary
    val remColor = MaterialTheme.colorScheme.primary
    val lightColor = MaterialTheme.colorScheme.secondary
    val awakeColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )
    val plotHeight = 118.dp

    val colors = mapOf(
        SleepStageKind.DEEP to deepColor,
        SleepStageKind.REM to remColor,
        SleepStageKind.LIGHT to lightColor,
        SleepStageKind.AWAKE to awakeColor,
        SleepStageKind.RESTLESS to awakeColor,
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val current = nights.getOrNull(selected)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current?.date?.let(::chartTooltipDate) ?: "—",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            current?.let { night ->
                Text(
                    bandsOf(night.summary).joinToString(" · ") { "${it.label} ${formatDurationValue(it.minutes)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight + 22.dp)
                .pointerInput(nights, onOpenDay) {
                    detectTapGestures(
                        onTap = { offset ->
                            selected = (offset.x / (size.width / nights.size))
                                .toInt()
                                .coerceIn(0, nights.lastIndex)
                        },
                        onLongPress = { offset ->
                            val index = (offset.x / (size.width / nights.size))
                                .toInt()
                                .coerceIn(0, nights.lastIndex)
                            selected = index
                            nights.getOrNull(index)?.date?.let { day -> onOpenDay?.invoke(day) }
                        },
                    )
                },
        ) {
            val baselineY = plotHeight.toPx()
            val slot = size.width / nights.size
            val barWidth = (slot * if (nights.size > 16) 0.6f else 0.7f).coerceAtMost(22.dp.toPx())
            val totals = nights.map { it.summary.asleepMinutes }
            val maximum = totals.maxOrNull()?.coerceAtLeast(1) ?: 1
            val crown = (barWidth / 2f).coerceAtMost(6.dp.toPx())

            drawLine(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                Offset(0f, baselineY),
                Offset(size.width, baselineY),
                1.dp.toPx(),
            )

            nights.forEachIndexed { index, night ->
                val centerX = slot * (index + 0.5f)
                val left = centerX - barWidth / 2f
                val isSelected = index == selected
                var cursorY = baselineY
                val scale = (baselineY - 6.dp.toPx()) / maximum

                bandsOf(night.summary).forEach { band ->
                    val height = band.minutes * scale
                    if (height <= 0f) return@forEach
                    val top = cursorY - height
                    val color = colors[band.kind] ?: lightColor
                    drawRect(
                        color = if (isSelected) color else color.copy(alpha = 0.55f),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, height),
                    )
                    cursorY = top
                }

                if (isSelected) {
                    drawRoundRect(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        topLeft = Offset(left - 2.dp.toPx(), cursorY - 2.dp.toPx()),
                        size = Size(barWidth + 4.dp.toPx(), (baselineY - cursorY) + 4.dp.toPx()),
                        cornerRadius = CornerRadius(crown),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            // Tick labels only where they fit, to keep the axis readable.
            val labelStep = if (nights.size <= 7) 1 else 5
            nights.forEachIndexed { index, night ->
                if (index % labelStep != 0 && index != nights.lastIndex) return@forEachIndexed
                val measured = measurer.measure(night.label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        (slot * (index + 0.5f) - measured.size.width / 2f)
                            .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)),
                        baselineY + 4.dp.toPx(),
                    ),
                )
            }
        }

        StageColorLegend(
            listOf(
                SleepStageKind.DEEP to deepColor,
                SleepStageKind.REM to remColor,
                SleepStageKind.LIGHT to lightColor,
                SleepStageKind.AWAKE to awakeColor,
            )
        )
    }
}

@Composable
private fun StageColorLegend(entries: List<Pair<SleepStageKind, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 2.dp)) {
        entries.forEach { (kind, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(
                    kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Deep, REM, light and awake minutes in the order they are stacked. */
private fun bandsOf(summary: SleepSummary): List<StageBand> {
    fun minutesOf(vararg kinds: SleepStageKind): Int =
        summary.stages.filter { it.kind in kinds }.sumOf { it.minutes }

    return listOf(
        StageBand(SleepStageKind.DEEP, "Profundo", minutesOf(SleepStageKind.DEEP)),
        StageBand(SleepStageKind.REM, "REM", minutesOf(SleepStageKind.REM)),
        StageBand(SleepStageKind.LIGHT, "Ligero", minutesOf(SleepStageKind.LIGHT)),
        StageBand(
            SleepStageKind.AWAKE,
            "Despierto",
            minutesOf(SleepStageKind.AWAKE, SleepStageKind.RESTLESS),
        ),
    )
}
