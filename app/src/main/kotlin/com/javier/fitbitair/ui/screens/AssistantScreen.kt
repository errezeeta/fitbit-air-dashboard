package com.javier.fitbitair.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Psychology
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
        /** Concrete backing for that step, e.g. "7 días · pasos, sueño, pulso". */
        val stageDetail: String = "",
        /** When the question was sent, so the progress line can count seconds. */
        val startedAt: Long = 0L,
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
    val isWorking = isBusy || messages.any { it is AssistantMessage.Agent && it.streaming }

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
                HeaderOrb(active = isWorking)
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

        val listState = rememberLazyListState()
        // Follow the answer while it is written, but at a walking pace (one
        // scroll per ~48 characters, not per tick) and only when the reader is
        // already at the bottom, so scrolling up is never yanked back.
        val lastLength = (messages.lastOrNull() as? AssistantMessage.Agent)?.text?.length ?: 0
        LaunchedEffect(messages.size, lastLength / 48) {
            if (messages.isEmpty()) return@LaunchedEffect
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible < messages.lastIndex - 1) return@LaunchedEffect
            listState.animateScrollToItem(messages.lastIndex)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("¿Cómo ha ido mi sueño esta semana?", "Resume mis pasos de los últimos 7 días", "¿Qué significa mi pulso en reposo?").forEach { prompt ->
                            AssistChip(onClick = { onSend(prompt) }, enabled = !isBusy, label = { Text(prompt) })
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, message ->
                MessageEntrance(
                    fromUser = message is AssistantMessage.User,
                    modifier = Modifier.animateItem(),
                ) {
                    when (message) {
                        is AssistantMessage.User -> MessageBubble(message.text, fromUser = true)
                        is AssistantMessage.Agent -> AgentBubble(message, onSend = onSend, isBusy = isBusy)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(500) },
                modifier = Modifier.weight(1f),
                enabled = !isBusy,
                placeholder = { Text(if (isBusy) "Escribiendo la respuesta…" else "Escribe una pregunta…") },
                shape = RoundedCornerShape(26.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(draft) { onSend(it); draft = "" } }),
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = if (draft.isNotBlank() || isBusy) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    )
                    .clickable(enabled = draft.isNotBlank() && !isBusy) {
                        submit(draft) { onSend(it); draft = "" }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    SendSpinner()
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

/**
 * The answer is revealed as a steady stream of characters, not in the chunks
 * the network delivers: text that lands in bursts still reads like it is being
 * typed. Far behind? It catches up a little faster, so it never lags the model.
 */
@Composable
private fun StreamingBubble(text: String, streaming: Boolean) {
    var revealed by remember { mutableStateOf(0) }
    LaunchedEffect(text, streaming) {
        if (!streaming) {
            revealed = text.length
            return@LaunchedEffect
        }
        if (revealed > text.length) revealed = text.length
        while (revealed < text.length) {
            val behind = text.length - revealed
            revealed += when {
                behind > 160 -> 4
                behind > 60 -> 2
                else -> 1
            }
            delay(14)
        }
    }
    val visible = if (streaming) text.take(revealed.coerceIn(0, text.length)) else text
    MessageBubble(text = if (streaming) "$visible▍" else visible, fromUser = false)
}

/**
 * The header orb only breathes while the assistant is actually working. An
 * always-running animation in a page that may be sitting offscreen costs
 * frames for nothing.
 */
@Composable
private fun HeaderOrb(active: Boolean) {
    if (!active) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        return
    }
    val pulse by rememberInfiniteTransition(label = "assistant-orb").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2_400), repeatMode = RepeatMode.Reverse),
        label = "assistant-orb-scale",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(pulse)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun MessageBubble(text: String, fromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (fromUser) 0.84f else 0.9f),
            shape = RoundedCornerShape(
                topStart = if (fromUser) 22.dp else 8.dp,
                topEnd = 22.dp,
                bottomStart = 22.dp,
                bottomEnd = if (fromUser) 8.dp else 22.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (fromUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                },
            ),
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
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
            ReasoningBlock(
                stage = message.stage,
                label = message.stageLabel,
                detail = message.stageDetail,
                startedAt = message.startedAt,
            )
        }
        if (message.text.isNotBlank()) {
            StreamingBubble(text = message.text, streaming = message.streaming)
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

/**
 * What the agent is doing, with the numbers behind it: an orb that keeps
 * turning, the step it is on, what that step is working with, and how long it
 * has been going. Steps change with a slide-and-settle animation rather than a
 * silent swap.
 */
@Composable
private fun ReasoningBlock(stage: String, label: String, detail: String, startedAt: Long) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp),
    ) {
        ReasoningOrb(stage)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 } + scaleIn(tween(260), initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 1.04f))
                    },
                    label = "reasoning-label",
                ) { current ->
                    Text(
                        current,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                ElapsedBadge(startedAt)
            }
            AnimatedContent(
                targetState = detail,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "reasoning-detail",
            ) { current ->
                Text(
                    current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A ring that keeps turning around the step's icon, so waiting feels alive. */
@Composable
private fun ReasoningOrb(stage: String) {
    val transition = rememberInfiniteTransition(label = "reasoning-orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "orb-rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb-sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb-pulse",
    )
    val accent = MaterialTheme.colorScheme.tertiary

    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            val stroke = 2.2.dp.toPx()
            drawArc(
                color = accent.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .alpha(pulse)
                .background(accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = stageIcon(stage),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/** How long this turn has been running: small, live, and honest. */
@Composable
private fun ElapsedBadge(startedAt: Long) {
    if (startedAt <= 0L) return
    var now by remember(startedAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text(
        "${seconds}s",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** The send button, busy: a small turning ring instead of a generic spinner. */
@Composable
private fun SendSpinner() {
    val transition = rememberInfiniteTransition(label = "send-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "send-rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "send-sweep",
    )
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 2.4.dp.toPx()
        drawArc(
            color = onPrimary.copy(alpha = 0.28f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = onPrimary,
            startAngle = -90f,
            sweepAngle = 360f * sweep,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** New messages arrive from their own side, so the thread reads in order. */
@Composable
private fun MessageEntrance(
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    val shift = (1f - progress.value) * (if (fromUser) 36f else -36f)
    Box(
        modifier = modifier.graphicsLayer {
            translationX = shift
            alpha = progress.value
            val scale = 0.96f + 0.04f * progress.value
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

private fun stageIcon(stage: String) = when (stage) {
    "datos" -> Icons.Rounded.CloudDownload
    "pensando" -> Icons.Rounded.Psychology
    "escribiendo" -> Icons.Rounded.EditNote
    "grafico" -> Icons.Rounded.Insights
    "arranque" -> Icons.Rounded.Bolt
    else -> Icons.Rounded.AutoAwesome
}

private inline fun submit(draft: String, action: (String) -> Unit) {
    val text = draft.trim()
    if (text.isNotEmpty()) action(text)
}
