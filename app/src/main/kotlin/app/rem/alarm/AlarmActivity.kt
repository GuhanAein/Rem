package app.rem.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.rem.container
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.format.formatLongDate
import app.rem.ui.format.formatTime
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.motion.pressScale
import app.rem.ui.theme.RemTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Full-screen "you can't miss this" alarm, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reminder by mutableStateOf<Reminder?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        lifecycleScope.launch {
            reminder = container.repository.get(id)
            if (reminder == null) finish() else startRinging()
        }

        setContent {
            RemTheme(darkTheme = true) {
                androidx.compose.runtime.CompositionLocalProvider(app.rem.ui.motion.LocalAmbientMotion provides app.rem.ui.motion.systemAnimationsEnabled(this@AlarmActivity)) {
                reminder?.let { r ->
                    AlarmContent(
                        reminder = r,
                        now = LocalDateTime.now(),
                        onDone = { act { container.service.complete(r.id) } },
                        onSnooze = { preset -> act { container.service.snooze(r.id, preset) } },
                        onDismiss = { act { container.service.dismiss(r.id) } },
                    )
                }
                }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        stopRinging()
        lifecycleScope.launch {
            block()
            finish()
        }
    }

    private fun startRinging() {
        // Your chosen sound for this category, or the phone's alarm sound.
        val custom = reminder?.category?.let { container.settings.value.sounds[it] }?.let(android.net.Uri::parse)
        val uri = custom ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        // Stop the sound after a minute; the nag alarm will bring it back.
        handler.postDelayed({ stopRinging() }, 60_000)
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context, reminderId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
fun AlarmContent(
    reminder: Reminder,
    now: LocalDateTime,
    onDone: () -> Unit,
    onSnooze: (SnoozePreset) -> Unit,
    onDismiss: () -> Unit,
    animate: Boolean = true,
) {
    val c = app.rem.ui.theme.Rem.colors
    val tint = reminder.category.color
    val breathe = if (animate && LocalAmbientMotion.current) {
        val v by rememberInfiniteTransition(label = "breathe").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "b",
        )
        v
    } else 0.5f
    val clock = formatTime(now.toLocalTime())

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        // A slow, warm breathing glow behind the clock: the only motion on screen.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(c.gold.copy(alpha = 0.06f + 0.06f * breathe), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.28f),
                    radius = size.width * (0.8f + 0.1f * breathe),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(formatLongDate(now.toLocalDate()).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(clock.substringBefore(' '), color = c.label, fontSize = 112.sp, fontFamily = app.rem.ui.theme.Serif, letterSpacing = (-4).sp, lineHeight = 120.sp)
                Text(clock.substringAfter(' ', ""), color = c.secondaryLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 22.dp, start = 6.dp))
            }

            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(8.dp))
                Text(reminder.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
            }
            Spacer(Modifier.height(12.dp))
            Text(reminder.title, color = c.label, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            reminder.amount?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = c.gold, style = MaterialTheme.typography.titleMedium)
            }
            if (reminder.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(reminder.notes, color = c.secondaryLabel, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))

            app.rem.ui.components.SlideToConfirm("Slide to complete", onConfirm = onDone)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(SnoozePreset.MIN_5, SnoozePreset.MIN_15, SnoozePreset.HOUR_1).forEach { p ->
                    Text(
                        p.label,
                        color = c.label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, c.separator, RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button) { onSnooze(p) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Dismiss", color = c.secondaryLabel) }
        }
    }
}
