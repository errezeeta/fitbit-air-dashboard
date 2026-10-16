package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.domain.DailyDistance
import com.javier.fitbitair.domain.DailyRestingHeartRate
import com.javier.fitbitair.domain.DailySleep
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.SleepStage
import com.javier.fitbitair.domain.SleepStageKind
import com.javier.fitbitair.domain.chartTooltipDate
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val esLocale = Locale.forLanguageTag("es-ES")

private val esNumber: NumberFormat by lazy { NumberFormat.getIntegerInstance(esLocale) }

fun formatStepsCount(value: Int): String = esNumber.format(value)

fun formatDistanceValue(meters: Int): String {
    if (meters < 1_000) return "$meters m"
    return String.format(esLocale, "%.1f km", meters / 1_000.0)
}

fun formatDurationValue(minutes: Int): String {
    if (minutes <= 0) return "—"
    return "${minutes / 60} h ${minutes % 60} min"
}

/** Short form for the value printed on top of a bar: 12,4k / 45m / 7h30. */
private fun compactSteps(value: Int): String =
    if (value < 1_000) "$value" else String.format(esLocale, "%.1fk", value / 1_000.0)

private fun compactDistance(meters: Int): String =
    if (meters < 1_000) "${meters}m" else String.format(esLocale, "%.1fk", meters / 1_000.0)

private fun compactMinutes(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h%02d".format(minutes % 60)

private data class TrendEntry(
    val value: Int,
    val axisLabel: String,
    val date: LocalDate?,
)

/**
 * Bar charts draw in one Canvas and react to touch: pointing at a bar lifts it
 * and names the day it belongs to. Bars have square feet and a rounded crown,
 * each sits on its own track, and a dashed average line crosses the plot.
 */
@Composable
fun StepsBarChart(
    data: List<DailySteps>,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TrendBarChart(
        entries = data.map { TrendEntry(it.steps, it.label, it.date) },
        tooltipDetail = { steps -> "${formatStepsCount(steps)} pasos" },
        barValue = ::compactSteps,
        highlightColor = MaterialTheme.colorScheme.primary,
        averageLabel = averageTooltip(data.map { it.steps }) { "${formatStepsCount(it)} pasos" },
        onOpenDay = onOpenDay,
        modifier = modifier,
    )
}

@Composable
fun DistanceBarChart(
    data: List<DailyDistance>,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TrendBarChart(
        entries = data.map { TrendEntry(it.meters, it.label, it.date) },
        tooltipDetail = ::formatDistanceValue,
        barValue = ::compactDistance,
        highlightColor = MaterialTheme.colorScheme.secondary,
        averageLabel = averageTooltip(data.map { it.meters }) { formatDistanceValue(it) },
        onOpenDay = onOpenDay,
        modifier = modifier,
    )
}

/** Sleep trend shares the same interaction, with minutes as the value. */
@Composable
fun SleepTrendChart(
    data: List<DailySleep>,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TrendBarChart(
        entries = data.map { TrendEntry(it.summary.asleepMinutes, it.label, it.date) },
        tooltipDetail = ::formatDurationValue,
        barValue = ::compactMinutes,
        highlightColor = MaterialTheme.colorScheme.tertiary,
        averageLabel = averageTooltip(data.map { it.summary.asleepMinutes }) { formatDurationValue(it) },
        onOpenDay = onOpenDay,
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
private fun TrendBarChart(
    entries: List<TrendEntry>,
    tooltipDetail: (Int) -> String,
    barValue: (Int) -> String,
    highlightColor: Color,
    averageLabel: String,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxWidth().height(140.dp)) {}
        return
    }
    var selected by remember(entries) { mutableStateOf(entries.lastIndex) }
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val trackColor = highlightColor.copy(alpha = 0.07f)
    val dormantBarColor = highlightColor.copy(alpha = 0.30f)
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )
    val mutedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueLabelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    val plotHeight = 112.dp
    val showValues = entries.size <= 8

    Column(modifier = modifier.fillMaxWidth()) {
        SelectedTooltip(entries, selected, tooltipDetail, highlightColor, averageLabel)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight + 26.dp)
                .pointerInput(entries, onOpenDay) {
                    detectTapGestures(
                        onTap = { offset ->
                            selected = (offset.x / (size.width / entries.size))
                                .toInt()
                                .coerceIn(0, entries.lastIndex)
                        },
                        // Holding a bar opens that day: which day, what was recorded.
                        onLongPress = { offset ->
                            val index = (offset.x / (size.width / entries.size))
                                .toInt()
                                .coerceIn(0, entries.lastIndex)
                            selected = index
                            entries.getOrNull(index)?.date?.let { day -> onOpenDay?.invoke(day) }
                        },
                    )
                },
        ) {
            val plotWidth = size.width
            val baselineY = plotHeight.toPx()
            val topInset = if (showValues) 16.dp.toPx() else 4.dp.toPx()
            val plotTop = topInset
            val usableHeight = baselineY - plotTop
            val slot = plotWidth / entries.size
            val barWidth = (slot * if (entries.size > 16) 0.58f else 0.68f).coerceAtMost(20.dp.toPx())
            val maximum = entries.maxOf { it.value }.coerceAtLeast(1)
            val average = entries.sumOf { it.value }.toFloat() / entries.size
            val crown = (barWidth / 2f).coerceAtMost(6.dp.toPx())

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = baselineY - fraction * usableHeight
                drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), 1.dp.toPx())
            }

            // dashed average line, captioned by the chip in the header
            val averageY = baselineY - (average / maximum) * usableHeight
            drawLine(
                highlightColor.copy(alpha = 0.75f),
                Offset(0f, averageY),
                Offset(plotWidth, averageY),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
            )

            entries.forEachIndexed { index, entry ->
                val centerX = slot * (index + 0.5f)
                val left = centerX - barWidth / 2f
                val right = centerX + barWidth / 2f
                val isSelected = index == selected

                // full-height track: the empty space reads as a column
                drawPath(
                    roundedCrownPath(left, plotTop, right, baselineY, crown),
                    trackColor,
                )

                val fraction = (entry.value.toFloat() / maximum).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val barTop = baselineY - fraction * usableHeight
                    if (isSelected) {
                        drawPath(
                            roundedCrownPath(
                                left - 2.dp.toPx(),
                                plotTop,
                                right + 2.dp.toPx(),
                                baselineY,
                                crown + 2.dp.toPx(),
                            ),
                            highlightColor.copy(alpha = 0.10f),
                        )
                    }
                    drawPath(
                        roundedCrownPath(left, barTop, right, baselineY, crown),
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isSelected) highlightColor else dormantBarColor,
                                (if (isSelected) highlightColor else dormantBarColor).copy(alpha = 0.62f),
                            ),
                            startY = barTop,
                            endY = baselineY,
                        ),
                    )
                    if (showValues) {
                        val measured = measurer.measure(
                            barValue(entry.value),
                            valueLabelStyle.copy(
                                color = if (isSelected) highlightColor else mutedLabelColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                        drawText(
                            measured,
                            topLeft = Offset(
                                (centerX - measured.size.width / 2f)
                                    .coerceIn(0f, (plotWidth - measured.size.width).coerceAtLeast(0f)),
                                (barTop - measured.size.height - 3.dp.toPx()).coerceAtLeast(0f),
                            ),
                        )
                    }
                }

                val label = entry.axisLabel
                if (label.isNotEmpty()) {
                    val measured = measurer.measure(
                        label,
                        axisLabelStyle.copy(
                            color = if (isSelected) highlightColor else axisLabelStyle.color,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    drawText(
                        measured,
                        topLeft = Offset(
                            (centerX - measured.size.width / 2f)
                                .coerceIn(0f, (plotWidth - measured.size.width).coerceAtLeast(0f)),
                            baselineY + 9.dp.toPx(),
                        ),
                    )
                }
            }

            drawLine(
                axisColor,
                Offset(0f, baselineY),
                Offset(plotWidth, baselineY),
                1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A bar with square feet and a rounded crown. drawRoundRect cannot do this —
 * it rounds all four corners — so the crown is an arc over a straight body.
 */
private fun roundedCrownPath(left: Float, top: Float, right: Float, bottom: Float, crown: Float): Path {
    val radius = crown
        .coerceAtMost((right - left) / 2f)
        .coerceAtMost(((bottom - top) / 2f).coerceAtLeast(0.5f))
    return Path().apply {
        moveTo(left, bottom)
        lineTo(left, top + radius)
        // 180° is the left edge of the oval and a 180° sweep crosses its top.
        arcTo(Rect(left, top, right, top + 2 * radius), 180f, 180f, false)
        lineTo(right, bottom)
        close()
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
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
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
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
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

/**
 * Resting heart rate over the period: a smoothed curve with a soft fill, the
 * period average as a dashed reference and the latest reading ringed.
 */
@Composable
fun HeartRateSparkline(
    data: List<DailyRestingHeartRate>,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val values = data.map { it.beatsPerMinute.toFloat() }
    val average = values.average()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(data, onOpenDay) {
                detectTapGestures(
                    onLongPress = { offset ->
                        if (onOpenDay == null || data.isEmpty()) return@detectTapGestures
                        val slotWidth = size.width / data.size
                        val index = (offset.x / slotWidth).toInt().coerceIn(0, data.lastIndex)
                        data[index].date?.let(onOpenDay)
                    },
                )
            },
    ) {
        val minimum = (values.minOrNull() ?: 0f) - 4f
        val maximum = (values.maxOrNull() ?: 1f) + 4f
        val span = (maximum - minimum).coerceAtLeast(1f)
        val inset = 6.dp.toPx()
        val stepX = if (values.size <= 1) size.width / 2 else size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(index * stepX, inset + (size.height - 2 * inset) * (1f - (value - minimum) / span))
        }

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = inset + (size.height - 2 * inset) * fraction
            drawLine(axisColor.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }

        val averageY = inset + (size.height - 2 * inset) * (1f - (average.toFloat() - minimum) / span)
        drawLine(
            lineColor.copy(alpha = 0.6f),
            Offset(0f, averageY),
            Offset(size.width, averageY),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.zipWithNext().forEach { (from, to) ->
                val middle = (from.x + to.x) / 2f
                cubicTo(middle, from.y, middle, to.y, to.x, to.y)
            }
        }
        drawPath(
            path = Path().apply {
                addPath(curve)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.26f), Color.Transparent),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(curve, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        val last = points.last()
        drawCircle(lineColor.copy(alpha = 0.18f), radius = 8.dp.toPx(), center = last)
        drawCircle(lineColor, radius = 4.dp.toPx(), center = last)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "mín ${data.minOf { it.beatsPerMinute }} · media ${Math.round(average)} lpm",
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "último ${data.last().beatsPerMinute} lpm",
            style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
            color = lineColor,
        )
    }
}
