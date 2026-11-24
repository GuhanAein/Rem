package app.rem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.rem.backup.Backup
import app.rem.jobs.DailyJobs
import app.rem.ui.components.PrimaryButton
import app.rem.ui.theme.Rem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The daily Morning Card: a beautiful summary you can also share as an image. */
@Composable
fun MorningScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val morning by produceState<DailyJobs.Morning?>(null) { value = DailyJobs.morning(context) }
    NoirScreen("Morning card", "Today, *at a glance.*", onBack) {
        item {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) { morning?.let { MorningCard(it, LocalDate.now()) } }
        }
        item {
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Share card", onClick = {
                scope.launch {
                    val bmp = layer.toImageBitmap().asAndroidBitmap()
                    val file = withContext(Dispatchers.IO) {
                        File(Backup.shareDir(context), "rem-morning.png").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                    }
                    shareFile(context, file, "image/png", title = "Share your day")
                }
            }, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.IosShare)
        }
    }
}

@Composable
fun MorningCard(m: DailyJobs.Morning, date: LocalDate) {
    val c = Rem.colors
    val gold = c.gold
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF111114))
            .drawWithContent {
                drawRect(Brush.radialGradient(listOf(gold.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width))
                drawContent()
            }
            .padding(26.dp),
    ) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.ENGLISH)).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
        Spacer(Modifier.height(10.dp))
        Text("Good morning.", style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic), color = c.gold)
        Text(
            when (m.today.size) { 0 -> "A clear day."; 1 -> "One thing today."; else -> "${m.today.size} things today." },
            style = MaterialTheme.typography.displaySmall, color = c.label,
        )
        Spacer(Modifier.height(18.dp))
        m.today.take(5).forEach { Line(it) }
        Section("Birthdays this week", m.birthdays)
        Section("Bills due", m.bills)
        Section("Trials ending", m.trials)
        Section("Reach out", m.nudges)
        Spacer(Modifier.height(20.dp))
        Row {
            Text("REM", style = MaterialTheme.typography.labelSmall, color = c.gold)
            Spacer(Modifier.weight(1f))
            Text("remembered for you", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), color = c.tertiaryLabel)
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    val c = Rem.colors
    Spacer(Modifier.height(16.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
    Spacer(Modifier.height(6.dp))
    lines.take(4).forEach { Line(it) }
}

@Composable
private fun Line(text: String) {
    val c = Rem.colors
    Row(Modifier.padding(vertical = 5.dp)) {
        Canvas(Modifier.padding(top = 8.dp).size(5.dp)) { drawCircle(c.gold) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.label)
    }
}

