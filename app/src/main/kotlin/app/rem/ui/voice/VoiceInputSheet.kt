package app.rem.ui.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.SecondaryButton
import app.rem.ui.components.SheetHandle
import app.rem.ui.theme.Rem

/**
 * Voice input for any space. Listens, shows the words live and a [preview] of what will be
 * created, then hands the final text to [onConfirm]. Shopping, Wishlist and Work each decide
 * what the words mean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputSheet(
    caption: String,
    example: String,
    actionLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable (String) -> Unit = {},
) {
    val context = LocalContext.current
    val speech = remember { SpeechController(context) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) speech.acceptExternalResult(text)
    }
    fun listen() {
        if (speech.isAvailable) speech.start() else runCatching { systemDialog.launch(SpeechController.recognizerIntent()) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() }
    fun requestListen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { requestListen() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rem.colors.surface,
        dragHandle = { SheetHandle() },
    ) {
        VoiceInputContent(
            caption = caption, example = example, actionLabel = actionLabel,
            phase = speech.phase, transcript = speech.transcript, error = speech.error, level = speech.level,
            onMic = { if (speech.phase == VoicePhase.LISTENING) speech.stop() else requestListen() },
            onConfirm = { onConfirm(speech.transcript) },
            onRetry = { requestListen() },
            preview = preview,
        )
    }
}

@Composable
fun VoiceInputContent(
    caption: String,
    example: String,
    actionLabel: String,
    phase: VoicePhase,
    transcript: String,
    error: String?,
    level: Float,
    onMic: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    preview: @Composable (String) -> Unit,
) {
    val c = Rem.colors
    val listening = phase == VoicePhase.LISTENING
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp).padding(bottom = 18.dp).animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (listening) "LISTENING" else if (phase == VoicePhase.DONE) "HEARD" else caption.uppercase(),
            style = MaterialTheme.typography.labelSmall, color = if (listening) c.gold else c.secondaryLabel,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 88.dp), contentAlignment = Alignment.Center) {
            when {
                transcript.isNotBlank() -> Text("“$transcript”", style = MaterialTheme.typography.headlineMedium, color = c.label, textAlign = TextAlign.Center)
                error != null -> Text(error, style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, textAlign = TextAlign.Center)
                else -> Text("“$example”", style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic), color = c.tertiaryLabel, textAlign = TextAlign.Center)
            }
        }
        AnimatedVisibility(transcript.isNotBlank(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
            Box(Modifier.padding(top = 14.dp)) { preview(transcript) }
        }
        Spacer(Modifier.height(24.dp))
        Waveform(listening, level, Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(18.dp))
        MicButton(listening, onMic)
        Spacer(Modifier.height(22.dp))
        AnimatedVisibility(phase == VoicePhase.DONE && transcript.isNotBlank(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Again", onRetry, Modifier.weight(1f))
                PrimaryButton(actionLabel, onConfirm, Modifier.weight(1.5f))
            }
        }
    }
}
