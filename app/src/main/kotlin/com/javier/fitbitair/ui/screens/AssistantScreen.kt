package com.javier.fitbitair.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.javier.fitbitair.domain.AssistantChart
import com.javier.fitbitair.ui.components.ChatMiniChart
import kotlinx.coroutines.delay

sealed interface AssistantMessage {
    data class User(val text: String) : AssistantMessage
    data class Agent(
        val text: String,
        val usedData: String,
        val isDemo: Boolean,
        /** The agent decides when a chart helps; null means a plain answer. */
        val chart: AssistantChart? = null,
        /** Tappable follow-ups the agent proposed to keep the thread going. */
        val suggestions: List<String> = emptyList(),
        /** True while the answer is still being written. */
        val streaming: Boolean = false,
        /** Machine name of the current step, e.g. "datos" or "escribiendo". */
        val stage: String = "",
        /** Human wording of that step, shown next to the animated indicator. */
        val stageLabel: String = "",
    ) : AssistantMessage
}

@Composable
fun AssistantScreen(
    messages: List<AssistantMessage>,
    backendConfigured: Boolean,
    googleConnected: Boolean,
    assistantConfigured: Boolean,
    isBusy: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "assistant-orb")
        .animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(3_200), repeatMode = RepeatMode.Reverse),
            label = "assistant-orb-scale",
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(pulse)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tu asistente de salud", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !backendConfigured -> "Modo demo · backend pendiente"
                            !googleConnected -> "Conecta Google Health en Ajustes"
                            !assistantConfigured -> "Google Health conectado · Hermes pendiente"
                            else -> "Hermes · métricas consultadas al preguntar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (messages.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("¿Qué quieres entender?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Pregunta sobre tendencias y periodos concretos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("¿Cómo ha ido mi sueño esta semana?", "Resume mis pasos de los últimos 7 días", "¿Qué significa mi pulso en reposo?").forEach { prompt ->
                            AssistChip(onClick = { onSend(prompt) }, enabled = !isBusy, label = { Text(prompt) })
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, message ->
                when (message) {
                    is AssistantMessage.User -> MessageBubble(message.text, fromUser = true)
                    is AssistantMessage.Agent -> AgentBubble(message, onSend = onSend, isBusy = isBusy)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(500) },
                modifier = Modifier.weight(1f),
                enabled = !isBusy,
                placeholder = { Text("Escribe una pregunta…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(draft) { onSend(it); draft = "" } }),
            )
            IconButton(
                onClick = { submit(draft) { onSend(it); draft = "" } },
                enabled = draft.isNotBlank() && !isBusy,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Enviar pregunta",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            if (backendConfigured && googleConnected) {
                "Se envía a Hermes un resumen solo cuando preguntas. No sustituye el consejo de un profesional sanitario."
            } else {
                "En modo demo, las métricas son ficticias. No sustituye el consejo de un profesional sanitario."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun MessageBubble(text: String, fromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(
                topStart = if (fromUser) 20.dp else 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = if (fromUser) 6.dp else 20.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (fromUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                },
            ),
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AgentBubble(message: AssistantMessage.Agent, onSend: (String) -> Unit, isBusy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth(0.92f)) {
        if (message.streaming) {
            AgentStageRow(message.stage, message.stageLabel)
        }
        if (message.text.isNotBlank()) {
            MessageBubble(
                text = if (message.streaming) "${message.text}▍" else message.text,
                fromUser = false,
            )
        }
        AnimatedVisibility(
            visible = message.chart != null,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
        ) {
            message.chart?.let { chart ->
                ChatMiniChart(chart, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (message.suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                message.suggestions.forEachIndexed { index, suggestion ->
                    var visible by remember(message.text, suggestion) { mutableStateOf(false) }
                    // Chips land one after another, like they were being offered.
                    LaunchedEffect(message.text, suggestion) {
                        delay(index * 70L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 },
                    ) {
                        AssistChip(
                            onClick = { onSend(suggestion) },
                            enabled = !isBusy,
                            label = { Text(suggestion) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
        if (!message.streaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            color = if (message.isDemo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape,
                        ),
                )
                Text(
                    message.usedData,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isDemo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the agent is doing right now, with a pulse so a slow turn still feels alive. */
@Composable
private fun AgentStageRow(stage: String, label: String) {
    val transition = rememberInfiniteTransition(label = "agent-working")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Reverse),
        label = "agent-pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "agent-spin",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
    ) {
        Icon(
            imageVector = stageIcon(stage),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(15.dp)
                .alpha(pulse)
                .rotate(if (stage == "pensando") spin else 0f),
        )
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 }) togetherWith
                    fadeOut(tween(120))
            },
            label = "agent-stage-label",
        ) { current ->
            Text(
                current,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private fun stageIcon(stage: String) = when (stage) {
    "datos" -> Icons.Rounded.CloudDownload
    "escribiendo" -> Icons.Rounded.EditNote
    "grafico" -> Icons.Rounded.Insights
    else -> Icons.Rounded.AutoAwesome
}

private inline fun submit(draft: String, action: (String) -> Unit) {
    val text = draft.trim()
    if (text.isNotEmpty()) action(text)
}
