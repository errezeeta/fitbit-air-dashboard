package com.javier.fitbitair.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.ui.components.PageHeader

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
        PageHeader("PERSONALIZACIÓN", "Ajustes", "Conexiones, preferencias y privacidad", Icons.Rounded.Settings)
        SectionLabel("CUENTAS Y SERVICIOS")
        ConnectionCard(
            title = "Google Health",
            subtitle = when {
                !backendConfigured -> "Cloud Run aún no está configurado."
                !googleOAuthConfigured -> "El servidor está listo; falta autorizar la cuenta de Google."
                googleConnected -> "Pasos, sueño y pulso se consultan cuando abres la app."
                else -> "Conecta tu cuenta para consultar pasos, sueño y pulso."
            },
            icon = {
                Icon(
                    if (googleConnected) Icons.Rounded.Favorite else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (googleConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            statusLabel = if (googleConnected) "Conectado" else if (backendConfigured && googleOAuthConfigured) "Pendiente" else "No disponible",
            isHealthy = googleConnected,
            actionLabel = if (backendConfigured && googleOAuthConfigured) {
                if (googleConnected) "Reautorizar" else "Conectar"
            } else null,
            onAction = onConnectGoogleHealth,
        )
        ConnectionCard(
            title = "Hermes",
            subtitle = if (assistantConfigured) {
                "Configurado · las métricas solo se envían cuando haces una pregunta."
            } else {
                "Pendiente · falta enlazar el API Server de forma segura por HTTPS."
            },
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            statusLabel = if (assistantConfigured) "Listo" else "Pendiente",
            isHealthy = assistantConfigured,
        )
        connectionMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel("TU OBJETIVO")
        ElevatedCard(shape = RoundedCornerShape(26.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f))) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Meta diaria de pasos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("${formatGoal(stepGoal)} pasos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Se guarda en este dispositivo; no se conserva aquí un historial de salud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { editGoal = true }) { Text("Cambiar meta") }
            }
        }

        SectionLabel("ESTE DISPOSITIVO")
        SoftwareUpdateCard(updater, appVersionName)

        var privacyExpanded by remember { mutableStateOf(false) }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Privacidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Qué datos salen del teléfono y cuándo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (privacyExpanded) {
                    Text(
                        "El token de Google y la clave de Hermes permanecen en el servidor. El dispositivo guarda una sesión cifrada; las métricas se consultan a demanda y no se conserva un historial local. Si preguntas a Hermes, se envía el resumen del periodo elegido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { privacyExpanded = !privacyExpanded }) {
                    Text(if (privacyExpanded) "Ocultar detalles" else "Ver detalles")
                }
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
    statusLabel: String,
    isHealthy: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(if (isHealthy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline, CircleShape))
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = if (isHealthy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                actionLabel?.let { label -> TextButton(onClick = onAction) { Text(label) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
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
