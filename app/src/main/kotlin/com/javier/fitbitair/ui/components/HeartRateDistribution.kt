package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HeartRateBucket(val label: String, val count: Int, val from: Int, val to: Int)

/**
 * How the resting-heart-rate readings are spread. The average hides whether a
 * period was steady or jumped between two very different levels.
 */
@Composable
fun HeartRateDistribution(
    readings: List<Int>,
    modifier: Modifier = Modifier,
) {
    val buckets = bucketize(readings)
    if (buckets.all { it.count == 0 }) return

    val accent = MaterialTheme.colorScheme.tertiary
    val track = accent.copy(alpha = 0.10f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val countStyle = MaterialTheme.typography.labelSmall.copy(
        color = accent,
        fontWeight = FontWeight.SemiBold,
    )
    val measurer = rememberTextMeasurer()
    val maximum = buckets.maxOf { it.count }.coerceAtLeast(1)
    // Resolved before the Canvas: MaterialTheme cannot be read in a draw lambda.
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    val spoken = remember(readings) {
        val total = buckets.sumOf { it.count }
        val busiest = buckets.maxByOrNull { it.count }
        "Reparto de $total lecturas de pulso. La franja más frecuente es ${busiest?.label ?: "—"} " +
            "con ${busiest?.count ?: 0}"
    }
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Reparto de lecturas",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
        ) {
            val baselineY = size.height - 14.dp.toPx()
            val topInset = 14.dp.toPx()
            val usable = baselineY - topInset
            val slot = size.width / buckets.size
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())

            drawLine(
                axisColor,
                Offset(0f, baselineY),
                Offset(size.width, baselineY),
                1.dp.toPx(),
            )

            buckets.forEachIndexed { index, bucket ->
                val centerX = slot * (index + 0.5f)
                val left = centerX - barWidth / 2f

                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, topInset),
                    size = Size(barWidth, usable),
                    cornerRadius = CornerRadius(barWidth / 3f),
                )
                if (bucket.count > 0) {
                    val height = usable * (bucket.count.toFloat() / maximum)
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, baselineY - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 3f),
                    )
                    val measured = measurer.measure(bucket.count.toString(), countStyle)
                    drawText(
                        measured,
                        topLeft = Offset(
                            (centerX - measured.size.width / 2f)
                                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)),
                            (baselineY - height - measured.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                        ),
                    )
                }
                val label = measurer.measure(bucket.label, labelStyle)
                drawText(
                    label,
                    topLeft = Offset(
                        (centerX - label.size.width / 2f)
                            .coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                        baselineY + 3.dp.toPx(),
                    ),
                )
            }
        }
        Text(
            "Cada barra es un tramo de lpm y cuántos días cayeron en él.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Four fixed bands around the readings. Fixed bands beat quantiles here: they
 * are comparable between weeks, which is the whole point of looking twice.
 */
fun bucketize(readings: List<Int>): List<HeartRateBucket> {
    val bands = listOf(
        "<56" to (0..55),
        "56-59" to (56..59),
        "60-63" to (60..63),
        "64+" to (64..Int.MAX_VALUE),
    )
    return bands.map { (label, range) ->
        HeartRateBucket(
            label = label,
            count = readings.count { it in range },
            from = range.first,
            to = range.last,
        )
    }
}
