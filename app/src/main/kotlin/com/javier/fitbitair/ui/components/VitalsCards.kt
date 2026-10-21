package com.javier.fitbitair.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.ExerciseSession
import com.javier.fitbitair.domain.VitalKind
import com.javier.fitbitair.domain.VitalSeries
import com.javier.fitbitair.domain.chartTooltipDate
import java.util.Locale
import kotlin.math.roundToInt

private val esLocale = Locale.forLanguageTag("es-ES")

/** Latest reading plus how far it sits from the period average. */
private fun readout(kind: VitalKind, series: VitalSeries): String {
    val latest = series.latest?.value ?: return "—"
    return if (kind.decimals == 0) {
        latest.roundToInt().toString()
    } else {
        String.format(esLocale, "%.${kind.decimals}f", latest)
    }
}

private fun deltaNote(series: VitalSeries): String {
    val latest = series.latest?.value ?: return ""
    val average = series.average ?: return ""
    if (average == 0.0) return "media —"
    val delta = latest - average
    if (delta == 0.0) return "en la media"
    val sign = if (delta > 0) "+" else "−"
    val size = if (kotlin.math.abs(delta) < 1.0) {
        String.format(esLocale, "%.1f", kotlin.math.abs(delta))
    } else {
        kotlin.math.abs(delta).roundToInt().toString()
    }
    return "$sign$size vs media"
}

/**
 * Every vital in one grid. The backend hands them over in the same shape, so
 * a data type the tracker does not record simply has no points and never
 * produces a card.
 */
@Composable
fun VitalsSection(
    vitals: List<Pair<VitalKind, VitalSeries>>,
    modifier: Modifier = Modifier,
) {
    if (vitals.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "CONSTANTES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        vitals.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (kind, series) ->
                    VitalCard(kind, series, modifier = Modifier.weight(1f))
                }
                // Keep a lone card the same width as its neighbours.
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VitalCard(kind: VitalKind, series: VitalSeries, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                kind.kicker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    readout(kind, series),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (kind.unitSuffix.isNotEmpty()) {
                    Text(
                        " ${kind.unitSuffix}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Text(
                kind.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                deltaNote(series),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            MiniTrend(
                values = series.points.map { it.value.toFloat() },
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(28.dp),
            )
        }
    }
}

/** A bare line: no axes, no tooltips. The card already states the number. */
@Composable
private fun MiniTrend(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / span) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawCircle(
            color = color,
            radius = 3.5f,
            center = Offset(size.width, size.height - ((values.last() - min) / span) * size.height),
        )
    }
}

/**
 * Workouts, newest first. Google Health already summarises each session, so
 * this is plain formatting; the app never recomputes a metric the API owns.
 */
@Composable
fun ExerciseSection(sessions: List<ExerciseSession>, modifier: Modifier = Modifier) {
    if (sessions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "ENTRENAMIENTOS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.forEach { session -> ExerciseRow(session) }
        }
    }
}

@Composable
private fun ExerciseRow(session: ExerciseSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                session.name.ifEmpty { exerciseLabel(session.type) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                session.date?.let(::chartTooltipDate) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "${session.activeMinutes} min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                exerciseDetail(session),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun exerciseDetail(session: ExerciseSession): String {
    val parts = buildList {
        if (session.distanceMeters > 0) add(formatDistanceValue(session.distanceMeters))
        if (session.caloriesKcal > 0) add("${session.caloriesKcal} kcal")
        if (session.averageHeartRate > 0) add("${session.averageHeartRate} lpm")
    }
    return parts.joinToString(" · ")
}

/** Google sends SCREAMING_SNAKE enum names; the UI speaks Spanish. */
private fun exerciseLabel(type: String): String = when (type.uppercase(Locale.ROOT)) {
    "RUNNING", "RUN", "TRAIL_RUNNING" -> "Carrera"
    "WALKING", "WALK", "HIKE", "HIKING" -> "Caminata"
    "BIKING", "CYCLING", "BIKE" -> "Bici"
    "SWIMMING", "SWIM" -> "Natación"
    "WEIGHTS", "STRENGTH_TRAINING" -> "Fuerza"
    "ELLIPTICAL" -> "Elíptica"
    "YOGA" -> "Yoga"
    "WORKOUT", "OTHER" -> "Entreno"
    else -> type.lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase(esLocale) }
}
