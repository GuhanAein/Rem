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
import androidx.compose.material.icons.rounded.Stop
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
import app.rem.ui.components.CategoryGlyph
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
        containerColor = Rem.colors.card,
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
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp).animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = when {
                listening -> "Listening…"
                phase == VoicePhase.DONE -> "Here’s what I heard"
                phase == VoicePhase.ERROR -> "Let’s try that again"
                else -> "Tap to speak"
            },
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "status",
        ) { Text(it, style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel) }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
            when {
                transcript.isNotBlank() -> Text(
                    if (phase == VoicePhase.DONE) parsed.title.replaceFirstChar { it.uppercase() } else transcript,
                    style = MaterialTheme.typography.headlineSmall, color = c.label, textAlign = TextAlign.Center,
                )
                error != null -> Text(error, style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, textAlign = TextAlign.Center)
                else -> Text(
                    "Say something like\n“Call mom tomorrow at 6 pm”",
                    style = MaterialTheme.typography.bodyLarge, color = c.tertiaryLabel, textAlign = TextAlign.Center,
                )
            }
        }

        // What Rem understood, live.
        AnimatedVisibility(transcript.isNotBlank(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryGlyph(parsed.category, size = 26.dp)
                }
                Token(
                    if (parsed.dueAt != null) describeDue(parsed.dueAt, now.toLocalDate()) else "No time yet",
                    Icons.Rounded.CalendarMonth,
                    tint = if (parsed.dueAt != null) c.accent else c.secondaryLabel,
                )
                parsed.recurrence?.let { Token(it.describe(), Icons.Rounded.Repeat) }
                if (parsed.priority == Priority.HIGH) Token(parsed.priority.label, Icons.Rounded.Flag, tint = c.warning)
                Token(parsed.category.label, null)
            }
        }

        Spacer(Modifier.height(28.dp))
        MicButton(listening, level, onMic)
        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(phase == VoicePhase.DONE && transcript.isNotBlank(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Edit", onEdit, Modifier.weight(1f))
                PrimaryButton("Add Reminder", onAdd, Modifier.weight(1.4f))
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val c = Rem.colors
    val ring by animateFloatAsState(if (listening) 1f + level * 0.45f else 1f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "level")
    val halo by animateFloatAsState(if (listening) 1f else 0f, tween(200), label = "halo")
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        // A single soft halo that breathes with your voice.
        Box(
            Modifier.size(76.dp).graphicsLayer { scaleX = ring * 1.3f; scaleY = ring * 1.3f; alpha = halo }
                .clip(CircleShape).background(c.accent.copy(alpha = 0.14f)),
        )
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(if (listening) c.danger else c.accent)
                .clickable(role = Role.Button, onClickLabel = if (listening) "Stop listening" else "Start listening", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
    }
}
