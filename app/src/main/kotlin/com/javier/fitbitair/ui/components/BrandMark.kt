package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * The launcher mark, drawn in-app: a mint ring and one heartbeat.
 *
 * The top bar used a stock heart icon, which had nothing to do with the icon on
 * the home screen. Drawing the same geometry here keeps the brand in one piece.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        val unit = min(size.width, size.height) / 24f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color,
            radius = 9.2f * unit,
            center = centre,
            style = Stroke(width = 1.7f * unit),
        )
        val pulse = Path().apply {
            moveTo(centre.x - 8.2f * unit, centre.y)
            lineTo(centre.x - 3.2f * unit, centre.y)
            lineTo(centre.x - 1.2f * unit, centre.y - 4.2f * unit)
            lineTo(centre.x + 2.4f * unit, centre.y + 4.4f * unit)
            lineTo(centre.x + 4.4f * unit, centre.y)
            lineTo(centre.x + 8.4f * unit, centre.y)
        }
        drawPath(
            path = pulse,
            color = color,
            style = Stroke(
                width = 2.3f * unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
