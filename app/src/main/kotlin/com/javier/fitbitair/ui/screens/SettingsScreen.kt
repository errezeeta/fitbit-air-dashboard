package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.javier.fitbitair.data.AppUpdater
import com.javier.fitbitair.ui.components.formatStepsCount
import kotlinx.coroutines.launch
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
    updater: com.javier.fitbitair.data.AppUpdater? = null,
    appVersionName: String = com.javier.fitbitair.BuildConfig.APP_VERSION_NAME,
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
                !googleOAuthConfigured -> "El servidor está desplegado, pero falta completar la autorización de Google."
                googleConnected -> "Conectado · pasos, sueño y pulso se consultan cuando los necesitas."
                else -> "Desconectado · conecta tu cuenta para consultar pasos, sueño y pulso."
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
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

        SoftwareUpdateCard(updater, appVersionName)

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

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object Downloading : UpdateUiState
    data class Available(val update: AppUpdater.AvailableUpdate) : UpdateUiState
    data class Failed(
        val message: String,
        val actionLabel: String,
        val onRetry: () -> Unit,
    ) : UpdateUiState
    data class ReadyToInstall(val prep: AppUpdater.InstallPreparation) : UpdateUiState
}

/**
 * Looks for a newer GitHub release and lets the user download and install it
 * from inside the app. Android's package installer shows the one-time
 * "install unknown apps" prompt itself; no extra permission is requested here.
 */
@Composable
private fun SoftwareUpdateCard(updater: AppUpdater?, appVersionName: String) {
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    fun checkForUpdate() {
        val client = updater
        if (client == null) {
            state = UpdateUiState.Failed(
                message = "El actualizador no está disponible en esta instalación.",
                actionLabel = "Reintentar",
                onRetry = {},
            )
            return
        }
        state = UpdateUiState.Checking
        coroutineScope.launch {
            state = try {
                client.checkForUpdate()?.let(UpdateUiState::Available) ?: UpdateUiState.UpToDate
            } catch (_: Exception) {
                UpdateUiState.Failed(
                    message = "No se pudo comprobar la versión. Revisa tu conexión.",
                    actionLabel = "Reintentar",
                    onRetry = ::checkForUpdate,
                )
            }
        }
    }

    fun downloadUpdate(update: AppUpdater.AvailableUpdate) {
        val client = updater ?: return
        state = UpdateUiState.Downloading
        coroutineScope.launch {
            state = try {
                UpdateUiState.ReadyToInstall(client.download(update))
            } catch (_: Exception) {
                UpdateUiState.Failed(
                    message = "No se pudo descargar la actualización. Revisa tu conexión.",
                    actionLabel = "Reintentar descarga",
                    onRetry = { downloadUpdate(update) },
                )
            }
        }
    }

    LaunchedEffect(updater) { checkForUpdate() }

    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text("Actualización de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Versión $appVersionName · instalada en el dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val snapshot = state) {
                UpdateUiState.Idle, UpdateUiState.Checking -> Text(
                    "Buscando actualizaciones…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UpdateUiState.UpToDate -> Text(
                    "Estás en la última versión publicada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is UpdateUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        snapshot.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = snapshot.onRetry) { Text(snapshot.actionLabel) }
                }
                UpdateUiState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Descargando actualización…", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateUiState.Available -> TextButton(onClick = { downloadUpdate(snapshot.update) }) {
                    Text("Instalar ${snapshot.update.versionName}")
                }
                is UpdateUiState.ReadyToInstall -> InstallAction(updater, snapshot.prep)
            }
        }
    }
}


private fun formatGoal(value: Int): String = formatStepsCount(value)

/**
 * Two-step install: Android only lets an app launch the package installer once
 * the user has allowed "install unknown apps" for it, so when that is missing
 * we send them to that exact settings page and install on the way back.
 */
@Composable
private fun InstallAction(updater: AppUpdater?, prep: AppUpdater.InstallPreparation) {
    val context = LocalContext.current
    var failure by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(updater?.canInstallPackages() == true) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val resolved = updater?.canInstallPackages() == true
        allowed = resolved
        if (resolved && updater.startInstall(prep)) failure = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allowed) {
            Button(onClick = {
                val started = updater?.startInstall(prep) == true
                failure = if (started) null else "Android no encontró un instalador para esta APK."
            }) {
                Text("Abrir instalador")
            }
            Text(
                "Si el instalador se cierra solo, activa «Instalar apps desconocidas» para esta app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(onClick = {
                val intent = updater?.unknownSourcesSettingsIntent()
                if (intent != null) {
                    runCatching { settingsLauncher.launch(intent) }
                        .onFailure { context.startActivity(intent) }
                } else {
                    failure = "No se pudo abrir los ajustes de instalación."
                }
            }) {
                Text("Permitir instalación")
            }
            Text(
                "Android exige autorizar a esta app para instalar la actualización. Al volver, se abrirá sola.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        failure?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
