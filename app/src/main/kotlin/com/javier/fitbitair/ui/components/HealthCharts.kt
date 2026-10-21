package com.javier.fitbitair.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
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

private data class TrendEntry(
    val value: Int,
    val axisLabel: String,
    val date: LocalDate?,
)

/**
 * Bar charts use one Canvas, a quiet three-guide grid, and a single goal line
 * only where it applies. Exact values live in the animated selection row above,
 * so dense ranges stay readable without labels stacked over every bar.
 */
@Composable
fun StepsBarChart(
    data: List<DailySteps>,
    goal: Int? = null,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Remembered per dataset: every recomposition of the parent (a range chip,
    // the refreshing flag) used to allocate a fresh entry list, which also
    // invalidated pointerInput and the selection inside the chart.
    val entries = remember(data) { data.map { TrendEntry(it.steps, it.label, it.date) } }
    val averageLabel = remember(data) {
        averageTooltip(data.map { it.steps }) { "${formatStepsCount(it)} pasos" }
    }
    TrendBarChart(
        entries = entries,
        tooltipDetail = { steps -> "${formatStepsCount(steps)} pasos" },
        highlightColor = MaterialTheme.colorScheme.primary,
        averageLabel = averageLabel,
        goal = goal,
        goalLabel = goal?.let { "meta ${formatStepsCount(it)}" } ?: "",
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
    val entries = remember(data) { data.map { TrendEntry(it.meters, it.label, it.date) } }
    val averageLabel = remember(data) {
        averageTooltip(data.map { it.meters }) { formatDistanceValue(it) }
    }
    TrendBarChart(
        entries = entries,
        tooltipDetail = ::formatDistanceValue,
        highlightColor = MaterialTheme.colorScheme.secondary,
        averageLabel = averageLabel,
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
    val entries = remember(data) { data.map { TrendEntry(it.summary.asleepMinutes, it.label, it.date) } }
    val averageLabel = remember(data) {
        averageTooltip(data.map { it.summary.asleepMinutes }) { formatDurationValue(it) }
    }
    TrendBarChart(
        entries = entries,
        tooltipDetail = ::formatDurationValue,
        highlightColor = MaterialTheme.colorScheme.tertiary,
        averageLabel = averageLabel,
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
    highlightColor: Color,
    averageLabel: String,
    goal: Int? = null,
    goalLabel: String = "",
    onOpenDay: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxWidth().height(140.dp)) {}
        return
    }
    var selected by remember(entries) { mutableStateOf(entries.lastIndex) }
    // Bars grow into place on first appearance; a later range or a refresh
    // keeps the plot drawn and only fades it back in, so switching ranges never
    // empties the chart.
    val grow = remember { Animatable(0f) }
    val plotAlpha = remember { Animatable(1f) }
    var drawnOnce by remember { mutableStateOf(false) }
    LaunchedEffect(entries) {
        if (!drawnOnce) {
            drawnOnce = true
            grow.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        } else {
            plotAlpha.snapTo(0.45f)
            plotAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
    }
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val trackColor = highlightColor.copy(alpha = 0.07f)
    val dormantBarColor = highlightColor.copy(alpha = 0.30f)
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
    )
    // Measured once per dataset. Measuring inside the draw lambda ran a text
    // layout per bar per frame while the bars were growing — the single most
    // expensive thing on this screen.
    val axisLabels = remember(entries, axisLabelStyle) {
        entries.map { measurer.measure(it.axisLabel, axisLabelStyle) }
    }
    val selectedLabelStyle = remember(axisLabelStyle, highlightColor) {
        axisLabelStyle.copy(color = highlightColor, fontWeight = FontWeight.SemiBold)
    }
    val selectedLabel = remember(entries, selected, selectedLabelStyle) {
        entries.getOrNull(selected)?.let { measurer.measure(it.axisLabel, selectedLabelStyle) }
    }
    val goalColor = MaterialTheme.colorScheme.tertiary
    // Also measured outside the draw lambda: the goal tag is redrawn on every
    // animation frame while the bars grow.
    val goalText = remember(goalLabel, goalColor, axisLabelStyle) {
        if (goalLabel.isEmpty()) null
        else measurer.measure(goalLabel, axisLabelStyle.copy(color = goalColor, fontSize = 9.sp))
    }
    val plotHeight = 112.dp

    Column(modifier = modifier.fillMaxWidth()) {
        SelectedTooltip(entries, selected, tooltipDetail, highlightColor, averageLabel)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight + 26.dp)
                .graphicsLayer { alpha = plotAlpha.value }
                .pointerInput(entries, onOpenDay) {
                    detectTapGestures(
                        onTap = { offset ->
                            selected = barIndexAt(offset.x, size.width.toFloat(), entries.lastIndex)
                        },
                        // Holding a bar opens that day: which day, what was recorded.
                        onLongPress = { offset ->
                            val index = barIndexAt(offset.x, size.width.toFloat(), entries.lastIndex)
                            selected = index
                            entries.getOrNull(index)?.date?.let { day -> onOpenDay?.invoke(day) }
                        },
                    )
                }
                .pointerInput(entries) {
                    // Scrubbing: dragging a thumb across a month should read day
                    // by day, not need thirty separate taps.
                    detectDragGestures(
                        onDragStart = { offset ->
                            selected = barIndexAt(offset.x, size.width.toFloat(), entries.lastIndex)
                        },
                        onDrag = { change, _ ->
                            selected = barIndexAt(change.position.x, size.width.toFloat(), entries.lastIndex)
                        },
                    )
                },
        ) {
            val plotWidth = size.width
            val baselineY = plotHeight.toPx()
            val topInset = 4.dp.toPx()
            val plotTop = topInset
            val usableHeight = baselineY - plotTop
            val slot = plotWidth / entries.size
            val barWidth = (slot * if (entries.size > 16) 0.58f else 0.68f).coerceAtMost(20.dp.toPx())
            val maximum = entries.maxOf { it.value }
                .coerceAtLeast(goal ?: 1)
                .coerceAtLeast(1)
            val crown = (barWidth / 2f).coerceAtMost(6.dp.toPx())

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = baselineY - fraction * usableHeight
                drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), 1.dp.toPx())
            }

            // The goal is what makes the bars readable at a glance: days that
            // reach it stay in full colour, the rest recede.
            goal?.let { target ->
                val goalY = baselineY - (target.toFloat() / maximum) * usableHeight
                drawLine(
                    goalColor.copy(alpha = 0.8f),
                    Offset(0f, goalY),
                    Offset(plotWidth, goalY),
                    1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)),
                )
                if (goalText != null) {
                    drawText(
                        goalText,
                        topLeft = Offset(
                            3.dp.toPx(),
                            (goalY - goalText.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                        ),
                    )
                }
            }

            val labelStep = if (entries.size <= 10) 1 else (entries.size / 6).coerceAtLeast(1)
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

                val fraction = ((entry.value.toFloat() / maximum) * grow.value).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val barTop = baselineY - fraction * usableHeight
                    val reachedGoal = goal != null && entry.value >= goal
                    val barColor = when {
                        isSelected -> highlightColor
                        reachedGoal -> highlightColor.copy(alpha = 0.58f)
                        else -> dormantBarColor
                    }
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
                                barColor,
                                barColor.copy(alpha = 0.62f),
                            ),
                            startY = barTop,
                            endY = baselineY,
                        ),
                    )
                }

                val label = entry.axisLabel
                if (label.isNotEmpty()) {
                    if (index % labelStep != 0 && index != entries.lastIndex) return@forEachIndexed
                    val measured = if (isSelected) selectedLabel else axisLabels.getOrNull(index)
                    if (measured != null) {
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
 * Pointer x to entry index. Mirrors the drawing math (slot = width / n, bar
 * centre = slot * (i + 0.5)) so a tap and a scrub land on the bar under the
 * finger instead of its neighbour.
 */
internal fun barIndexAt(x: Float, width: Float, lastIndex: Int): Int {
    if (lastIndex <= 0 || width <= 0f) return 0
    val slot = width / (lastIndex + 1)
    return (x / slot).toInt().coerceIn(0, lastIndex)
}

/** Line charts spread their points edges-to-edges, so the mapping differs. */
private fun pointIndexAt(x: Float, width: Float, lastIndex: Int): Int {
    if (lastIndex <= 0 || width <= 0f) return 0
    return ((x / width) * lastIndex).toInt().coerceIn(0, lastIndex)
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
    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            (fadeIn(androidx.compose.animation.core.tween(170)) +
                slideInVertically(androidx.compose.animation.core.tween(190)) { it / 5 }) togetherWith
                (fadeOut(androidx.compose.animation.core.tween(110)))
        },
        label = "chart-selection",
    ) { current ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    current.date?.let(::chartTooltipDate) ?: current.axisLabel.ifEmpty { "Hoy" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tooltipDetail(current.value),
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
    var selected by remember(data) { mutableStateOf(data.lastIndex) }
    val selectedReading = data.getOrNull(selected) ?: data.last()
    val lineColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val values = remember(data) { data.map { it.beatsPerMinute.toFloat() } }
    val average = remember(values) { values.average() }
    val selectionSurface = MaterialTheme.colorScheme.surface

    AnimatedContent(
        targetState = selectedReading,
        transitionSpec = {
            (fadeIn(androidx.compose.animation.core.tween(150)) + slideInVertically(androidx.compose.animation.core.tween(180)) { it / 5 }) togetherWith
                fadeOut(androidx.compose.animation.core.tween(100))
        },
        label = "heart-chart-selection",
    ) { reading ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(reading.date?.let(::chartTooltipDate) ?: "Lectura seleccionada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${reading.beatsPerMinute} lpm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = lineColor)
            }
            Text(
                "media ${Math.round(average)} lpm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(data, onOpenDay) {
                detectTapGestures(
                    onTap = { offset -> selected = pointIndexAt(offset.x, size.width.toFloat(), data.lastIndex) },
                    onLongPress = { offset ->
                        val index = pointIndexAt(offset.x, size.width.toFloat(), data.lastIndex)
                        selected = index
                        data[index].date?.let { onOpenDay?.invoke(it) }
                    },
                )
            }
            .pointerInput(data) {
                // Same gesture as the bar charts: drag to move the marker along
                // the line instead of tapping point by point.
                detectDragGestures(
                    onDragStart = { offset ->
                        selected = pointIndexAt(offset.x, size.width.toFloat(), data.lastIndex)
                    },
                    onDrag = { change, _ ->
                        selected = pointIndexAt(change.position.x, size.width.toFloat(), data.lastIndex)
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

        // The period average the header chip names, drawn as a faint dashed
        // reference: "media 58 lpm" with nothing to compare it against is a
        // number looking for a place to point.
        val averageY = inset + (size.height - 2 * inset) * (1f - ((average - minimum) / span).toFloat())
        drawLine(
            lineColor.copy(alpha = 0.30f),
            Offset(0f, averageY),
            Offset(size.width, averageY),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
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

        val selectedPoint = points.getOrNull(selected) ?: points.last()
        drawLine(
            lineColor.copy(alpha = 0.20f),
            Offset(selectedPoint.x, inset),
            Offset(selectedPoint.x, size.height - inset),
            1.dp.toPx(),
        )
        drawCircle(lineColor.copy(alpha = 0.18f), radius = 9.dp.toPx(), center = selectedPoint)
        drawCircle(lineColor, radius = 4.dp.toPx(), center = selectedPoint)
        val last = points.last()
        if (selected != data.lastIndex) {
            drawCircle(selectionSurface, radius = 5.5.dp.toPx(), center = last)
            drawCircle(lineColor, radius = 3.dp.toPx(), center = last)
        }
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
            "máx ${data.maxOf { it.beatsPerMinute }} · último ${data.last().beatsPerMinute}",
            style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
            color = lineColor,
        )
    }
    Text(
        "Toca o arrastra para recorrer las lecturas · mantén pulsado para abrir el día",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
