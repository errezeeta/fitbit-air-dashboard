package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    stepGoal: Int,
    onStepGoalChange: (Int) -> Unit,
    backendConfigured: Boolean,
    googleConnected: Boolean,
    googleOAuthConfigured: Boolean,
    assistantConfigured: Boolean,
    connectionMessage: String?,
    onConnectGoogleHealth: () -> Unit,
) {
    var editGoal by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Preferencias y conexiones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (backendConfigured) "Estado de las conexiones" else "Demo local · backend de Cloud Run pendiente",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ConnectionCard(
            title = "Google Health",
            subtitle = when {
                !backendConfigured -> "Cloud Run aún no está configurado."
                !googleOAuthConfigured -> "El servidor está desplegado, pero falta completar la configuración OAuth."
                googleConnected -> "Conectado · métricas consultadas a demanda."
                else -> "No conectado · autoriza tu cuenta para leer pasos, sueño y pulso."
            },
            icon = {
                Icon(
                    if (googleConnected) Icons.Rounded.Favorite else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (googleConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            actionLabel = if (backendConfigured && googleOAuthConfigured) {
                if (googleConnected) "Reautorizar" else "Conectar"
            } else null,
            onAction = onConnectGoogleHealth,
        )
        ConnectionCard(
            title = "Hermes",
            subtitle = if (assistantConfigured) {
                "Configurado · el backend verifica que no haya herramientas activas antes de enviar métricas."
            } else {
                "Pendiente · falta enlazar el API Server de forma segura por HTTPS."
            },
            icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
        connectionMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ElevatedCard(shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Meta manual de pasos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("${formatGoal(stepGoal)} pasos al día", style = MaterialTheme.typography.bodyLarge)
                Text("Esta preferencia queda guardada en el dispositivo; no se guarda un historial local de salud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { editGoal = true }) { Text("Cambiar meta") }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacidad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "El token de Google y la clave de Hermes permanecen en el servidor. El dispositivo guarda una sesión de app cifrada; las métricas se consultan a demanda y no se conserva un historial local. Si preguntas a la IA, se envía el resumen del periodo elegido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (editGoal) {
        SettingsGoalDialog(
            current = stepGoal,
            onDismiss = { editGoal = false },
            onSave = { value -> onStepGoalChange(value); editGoal = false },
        )
    }
}

@Composable
private fun ConnectionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actionLabel?.let { label ->
                TextButton(onClick = onAction) { Text(label) }
            }
        }
    }
}

@Composable
private fun SettingsGoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta diaria") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(6) },
                label = { Text("Pasos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let(onSave) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatGoal(value: Int): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()
