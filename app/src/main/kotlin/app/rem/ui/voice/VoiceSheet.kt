package app.rem.ui.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.motion.pressScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.rem.core.model.AlertStyle
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.parser.ParsedReminder
import app.rem.core.parser.QuickAddParser
import app.rem.core.recurrence.describe
import app.rem.ui.add.buildQuickReminder
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.SecondaryButton
import app.rem.ui.components.Token
import app.rem.ui.components.label
import app.rem.ui.format.describeDue
import app.rem.ui.theme.Rem
import java.time.Clock
import java.time.LocalDateTime

/**
 * "Just say it": listens, shows the words live, and shows what Rem understood (date, time, repeat)
 * before you add it. Uses the phone's built-in speech recogniser; the understanding itself is Rem's
 * rule-based parser, on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onEdit: (String) -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val context = LocalContext.current
    val speech = remember { SpeechController(context) }
    val parser = remember(clock) { QuickAddParser(clock) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    // Fallback: the system speech dialog, for phones whose recogniser can't be embedded.
    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) speech.acceptExternalResult(text)
    }
    fun listen() {
        if (speech.isAvailable) speech.start()
        else runCatching { systemDialog.launch(SpeechController.recognizerIntent()) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen() }
    fun requestListen() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { requestListen() }

    val parsed = remember(speech.transcript) { parser.parse(speech.transcript) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rem.colors.surface,
        dragHandle = { app.rem.ui.components.SheetHandle() },
    ) {
        VoiceContent(
            phase = speech.phase,
            transcript = speech.transcript,
            error = speech.error,
            level = speech.level,
            parsed = parsed,
            now = LocalDateTime.now(clock),
            onMic = { if (speech.phase == VoicePhase.LISTENING) speech.stop() else requestListen() },
            onAdd = {
                val cat = parsed.category
                onSave(buildQuickReminder(parsed, cat, parsed.dueAt, cat.defaultStyle == AlertStyle.ALARM, clock.instant()))
            },
            onEdit = { onEdit(speech.transcript) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceContent(
    phase: VoicePhase,
    transcript: String,
    error: String?,
    level: Float,
    parsed: ParsedReminder,
    now: LocalDateTime,
    onMic: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Rem.colors
    val listening = phase == VoicePhase.LISTENING
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp).padding(bottom = 18.dp).animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = when {
                listening -> "LISTENING"
                phase == VoicePhase.DONE -> "UNDERSTOOD"
                phase == VoicePhase.ERROR -> "TRY AGAIN"
                else -> "TAP TO SPEAK"
            },
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
            label = "status",
        ) { Text(it, style = MaterialTheme.typography.labelSmall, color = if (listening) c.gold else c.secondaryLabel) }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
            when {
                transcript.isNotBlank() -> Text(
                    if (phase == VoicePhase.DONE) parsed.title.replaceFirstChar { it.uppercase() } else "\u201C$transcript\u201D",
                    style = MaterialTheme.typography.headlineLarge, color = c.label, textAlign = TextAlign.Center,
                )
                error != null -> Text(error, style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, textAlign = TextAlign.Center)
                else -> Text(
                    "\u201CCall mom tomorrow\nat six in the evening\u201D",
                    style = MaterialTheme.typography.headlineMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = c.tertiaryLabel, textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(transcript.isNotBlank(), enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Token(parsed.category.label, parsed.category.icon, tint = parsed.category.color)
                Token(
                    if (parsed.dueAt != null) describeDue(parsed.dueAt, now.toLocalDate()) else "No time yet",
                    Icons.Rounded.CalendarMonth,
                    tint = if (parsed.dueAt != null) c.gold else c.secondaryLabel,
                )
                parsed.recurrence?.let { Token(it.describe(), Icons.Rounded.Repeat) }
                if (parsed.priority == Priority.HIGH) Token("High priority", Icons.Rounded.Flag, tint = c.danger)
            }
        }

        Spacer(Modifier.height(26.dp))
        Waveform(listening, level, Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(18.dp))
        MicButton(listening, onMic)
        Spacer(Modifier.height(22.dp))

        AnimatedVisibility(phase == VoicePhase.DONE && transcript.isNotBlank(), enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Edit", onEdit, Modifier.weight(1f))
                PrimaryButton("Add reminder", onAdd, Modifier.weight(1.5f))
            }
        }
    }
}

/** Thin gold bars that rise with your voice and settle to a calm line in silence. */
@Composable
private fun Waveform(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val ambient = app.rem.ui.motion.LocalAmbientMotion.current
    val t = if (ambient && active) {
        val v by androidx.compose.animation.core.rememberInfiniteTransition(label = "wave").animateFloat(
            0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
        )
        v
    } else 0.3f
    val energy by animateFloatAsState(if (active) 0.25f + level * 0.75f else 0.06f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "energy")
    androidx.compose.foundation.Canvas(modifier) {
        val bars = 36
        val gap = size.width / bars
        val barW = 2.2.dp.toPx()
        for (i in 0 until bars) {
            val x = gap * i + gap / 2
            // A soft bell envelope so the centre moves most.
            val envelope = kotlin.math.sin(Math.PI * (i + 0.5) / bars).toFloat()
            val wave = 0.55f + 0.45f * kotlin.math.sin((t * 2 * Math.PI + i * 0.55).toFloat())
            val h = (size.height * energy * envelope * wave).coerceAtLeast(barW)
            drawLine(
                if (active) c.gold else c.tertiaryLabel,
                androidx.compose.ui.geometry.Offset(x, size.height / 2 - h / 2),
                androidx.compose.ui.geometry.Offset(x, size.height / 2 + h / 2),
                barW, androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val c = Rem.colors
    val haptics = app.rem.ui.motion.rememberHaptics()
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(76.dp).pressScale(interaction, 0.92f)
            .clip(CircleShape).background(if (listening) Color.Transparent else c.ivory)
            .then(if (listening) Modifier.border(1.5.dp, c.gold, CircleShape) else Modifier)
            .clickable(interaction, androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = if (listening) "Stop listening" else "Start listening") { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(c.gold))
        else Icon(Icons.Rounded.Mic, null, tint = c.onIvory, modifier = Modifier.size(30.dp))
    }
}
