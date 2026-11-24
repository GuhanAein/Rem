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
import androidx.compose.ui.draw.shadow
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
import app.rem.ui.format.describeDue
import app.rem.ui.format.formatLongDate
import app.rem.ui.format.formatTime
import app.rem.ui.theme.Rem
import app.rem.ui.motion.AuroraBackground
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.motion.floating
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.ringing
import app.rem.ui.motion.shimmer
import app.rem.ui.motion.staggeredEntrance
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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
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
    val accent = reminder.category.color
    val t = if (animate && LocalAmbientMotion.current) {
        val wave by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "wave")
        wave
    } else 0.45f

    val seen = androidx.compose.runtime.remember { mutableSetOf<Any>() }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(
            base = Rem.extras.alarmGradient,
            blobs = listOf(accent.copy(alpha = 0.8f), Color(0xFFA855F7), Color(0xFF38BDF8).copy(alpha = 0.6f)),
            speedMs = 9000,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.staggeredEntrance("clock", 0, seen), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(now.toLocalTime()), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Text(formatLongDate(now.toLocalDate()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.size(260.dp).staggeredEntrance("art", 1, seen), contentAlignment = Alignment.Center) {
                listOf(0f, 0.33f, 0.66f).forEach { phase ->
                    val p = (t + phase) % 1f
                    Box(
                        Modifier.size(260.dp).scale(0.45f + p * 0.55f).graphicsLayer { alpha = (1f - p) * 0.35f }
                            .clip(CircleShape).background(accent),
                    )
                }
                Box(
                    Modifier.size(120.dp).shadow(30.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(reminder.category.icon, null, tint = accent, modifier = Modifier.size(56.dp).ringing(1400, 18f)) }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.staggeredEntrance("title", 2, seen), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(reminder.category.label.uppercase(), color = Color.White.copy(alpha = 0.7f), style = app.rem.ui.theme.SectionLabel)
                Spacer(Modifier.height(8.dp))
                Text(reminder.title, color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(describeDue(reminder.dueAt, now.toLocalDate()), reminder.amount).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.weight(1f))

            val doneInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Row(
                Modifier
                    .staggeredEntrance("done", 3, seen)
                    .pressScale(doneInteraction, 0.95f)
                    .floating(2f, 1800)
                    .fillMaxWidth()
                    .height(68.dp)
                    .shadow(24.dp, RoundedCornerShape(34.dp), ambientColor = accent, spotColor = accent)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color.White)
                    .shimmer(2200)
                    .clickable(doneInteraction, androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = "Mark done", onClick = onDone),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Done", color = Color(0xFF14141F), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().staggeredEntrance("snooze", 4, seen)) {
                listOf(SnoozePreset.MIN_5, SnoozePreset.MIN_15, SnoozePreset.HOUR_1).forEach { p ->
                    Text(
                        p.label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(role = Role.Button) { onSnooze(p) }
                            .padding(vertical = 16.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss) { Text("Dismiss for now", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

