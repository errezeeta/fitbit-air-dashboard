package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.DaySnapshot
import com.javier.fitbitair.domain.chartTooltipDate
import com.javier.fitbitair.ui.components.SleepStageBar
import com.javier.fitbitair.ui.components.SleepStageLegend
import com.javier.fitbitair.ui.components.formatDistanceValue
import com.javier.fitbitair.ui.components.formatDurationValue
import com.javier.fitbitair.ui.components.formatStepsCount

/**
 * Everything recorded on one day, opened by holding a bar in any chart: the
 * day the value belongs to, without leaving the screen you were reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(snapshot: DaySnapshot, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "DÍA REGISTRADO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    chartTooltipDate(snapshot.date),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (!snapshot.hasAnything) {
                Text(
                    "Ese día no hay ningún registro de la pulsera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DayMetricTile(
                    label = "Pasos",
                    value = snapshot.steps?.let(::formatStepsCount) ?: "—",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                DayMetricTile(
                    label = "Pulso en reposo",
                    value = snapshot.restingHeartRate?.let { "$it lpm" } ?: "—",
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }

            snapshot.distanceMeters?.let { meters ->
                DayRow("Distancia", formatDistanceValue(meters))
            }

            snapshot.sleep?.let { sleep ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sueño", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatDurationValue(sleep.asleepMinutes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "${sleep.bedtime} — ${sleep.wakeTime} · ${formatDurationValue(sleep.inBedMinutes)} en cama",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sleep.stages.isNotEmpty()) {
                            SleepStageBar(sleep.stages, Modifier.fillMaxWidth())
                            SleepStageLegend(sleep.stages)
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Datos del periodo seleccionado · no constituye una valoración médica.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayMetricTile(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
