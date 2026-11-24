package app.rem.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.rem.ui.onboarding.editorial
import app.rem.ui.theme.Rem
import java.io.File

/** The frame every secondary screen shares: back, a small caption, a big editorial title. */
@Composable
fun NoirScreen(
    caption: String,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val c = Rem.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.canvas),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 48.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.separator, CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.weight(1f))
                    trailing?.invoke()
                }
                Spacer(Modifier.height(26.dp))
                Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.height(8.dp))
                Text(editorial(title), style = MaterialTheme.typography.displaySmall, color = c.label)
                Spacer(Modifier.height(8.dp))
            }
        }
        content()
    }
}

/** Shares a file from cache/share or files/backups through the app's FileProvider. */
fun shareFile(context: Context, file: File, mime: String, text: String? = null, title: String = "Share") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
