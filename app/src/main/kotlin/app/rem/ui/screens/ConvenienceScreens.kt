package app.rem.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.rem.capture.Captured
import app.rem.capture.SmsBills
import app.rem.container
import app.rem.core.insights.Money
import app.rem.core.life.DetectedBill
import app.rem.core.life.Template
import app.rem.core.life.Templates
import app.rem.core.recurrence.describe
import app.rem.ui.components.Caption
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SwitchRow
import app.rem.ui.components.noirSurface
import app.rem.ui.format.formatDay
import app.rem.ui.theme.Rem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ============================================================================ Templates

/** One-tap sets of reminders (or a packing list) for common situations. */
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<String?>(null) }

    NoirScreen("Templates", "Start with\n*a set.*", onBack) {
        item {
            Text("Add a whole routine at once. You can change any reminder afterwards.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
            Spacer(Modifier.height(8.dp))
        }
        items(Templates.ALL, key = { it.id }) { t ->
            Spacer(Modifier.height(12.dp))
            TemplateCard(t, expanded = open == t.id, done = t.id in added, onToggle = { open = if (open == t.id) null else t.id }) {
                scope.launch {
                    applyTemplate(context, t)
                    added = added + t.id
                }
            }
        }
    }
}

suspend fun applyTemplate(context: android.content.Context, t: Template): Int {
    val c = context.container
    return if (t.asList) {
        val id = c.spaces.addList(t.name)
        c.spaces.addNames(id, t.items.map { it.title })
    } else {
        Templates.reminders(t, LocalDateTime.now(c.clock)).onEach { c.service.save(it) }.size
    }
}

@Composable
private fun TemplateCard(t: Template, expanded: Boolean, done: Boolean, onToggle: () -> Unit, onAdd: () -> Unit) {
    val c = Rem.colors
    Column(Modifier.fillMaxWidth().noirSurface().clickable(onClick = onToggle).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, color = c.label)
                Spacer(Modifier.height(2.dp))
                Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
            }
            Spacer(Modifier.width(12.dp))
            Text(if (t.asList) "${t.items.size} items" else "${t.items.size} reminders", style = MaterialTheme.typography.labelMedium, color = c.gold)
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            t.items.forEach { i ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(4.dp).clip(CircleShape).then(Modifier.noirSurface(CircleShape, elevated = true)))
                    Spacer(Modifier.width(10.dp))
                    Text(i.title, style = MaterialTheme.typography.bodyMedium, color = c.label, modifier = Modifier.weight(1f))
                    i.rrule?.let { r -> Text(app.rem.core.recurrence.RecurrenceRule.parseOrNull(r)?.describe().orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel) }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (t.asList) "Added to Shopping" else "Added to your reminders", style = MaterialTheme.typography.labelLarge, color = c.success)
                }
            } else PrimaryButton(if (t.asList) "Add list" else "Add ${t.items.size} reminders", onAdd, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
}

// ============================================================================ Captured notifications

private val TIME = DateTimeFormatter.ofPattern("h:mm a")

/** "Turn any notification into a reminder": recent notifications from other apps, one tap each. */
@Composable
fun CapturedScreen(onBack: () -> Unit, onUse: (String) -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    var access by remember { mutableStateOf(Captured.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = Captured.hasAccess(context)
        Captured.load(context)
        onPauseOrDispose { }
    }
    val items by Captured.items.collectAsState()

    NoirScreen("From notifications", "Catch it\n*before it's gone.*", onBack) {
        if (!access) {
            item {
                Column(Modifier.fillMaxWidth().noirSurface().padding(20.dp)) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = c.gold)
                    Spacer(Modifier.height(10.dp))
                    Text("Allow notification access", style = MaterialTheme.typography.titleMedium, color = c.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Rem keeps your last 40 notifications on this phone so a WhatsApp message, an email or a bank alert can become a reminder in one tap. Nothing is uploaded, and one-time passwords are never saved.",
                        style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Open settings", { context.startActivity(Captured.accessSettings()) }, Modifier.fillMaxWidth())
                }
            }
        } else if (items.isEmpty()) {
            item {
                Text("New notifications will show up here. Tap one to make it a reminder.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            item { Caption("Recent", count = items.size) }
            item {
                GroupedCard {
                    items.forEachIndexed { i, n ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().clickable { onUse(n.asReminderText); Captured.remove(context, n.key) }.padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${n.app.uppercase()}  ·  ${java.time.Instant.ofEpochMilli(n.at).atZone(java.time.ZoneId.systemDefault()).format(TIME)}", style = MaterialTheme.typography.labelSmall, color = c.tertiaryLabel)
                                Spacer(Modifier.height(3.dp))
                                if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1)
                                if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 2)
                            }
                            androidx.compose.material3.IconButton(onClick = { Captured.remove(context, n.key) }) { Icon(Icons.Rounded.Close, "Remove", tint = c.tertiaryLabel, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Clear all", style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel, modifier = Modifier.clickable { Captured.clear(context) }.padding(8.dp))
            }
        }
    }
}

// ============================================================================ SMS bills

/** Bills found in your text messages, each one tap away from a reminder. */
@Composable
fun BillsScanScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val settings by context.container.settings.state.collectAsState()
    var canRead by remember { mutableStateOf(SmsBills.canRead(context)) }
    var found by remember { mutableStateOf<List<DetectedBill>?>(null) }
    var added by remember { mutableStateOf(mapOf<String, Long?>()) }

    suspend fun scan() { found = withContext(Dispatchers.IO) { SmsBills.scanInbox(context) } }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        canRead = res[Manifest.permission.READ_SMS] == true
        if (res[Manifest.permission.RECEIVE_SMS] == true) context.container.settings.update { it.copy(smsBills = true) }
        if (canRead) scope.launch { scan() }
    }
    LaunchedEffect(canRead) { if (canRead) scan() }

    NoirScreen("Bills from messages", "Bills that\n*find you.*", onBack) {
        item {
            Text(
                "Rem reads bill texts from your bank, phone, power and gas providers on this phone, using simple rules, and offers a reminder before each due date. Messages never leave your phone.",
                style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (!canRead) {
            item {
                PrimaryButton("Allow SMS access", { ask.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Sms)
            }
        } else {
            item {
                GroupedCard {
                    SwitchRow("Watch new messages for bills", settings.smsBills, { on ->
                        if (on && !SmsBills.canReceive(context)) ask.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                        else context.container.settings.update { it.copy(smsBills = on) }
                    }, icon = Icons.Rounded.Sms, iconColor = c.secondaryLabel, subtitle = "A notification with “Remind me” when a bill arrives")
                }
            }
            val list = found
            item { Caption("Found in the last 45 days", count = list?.size) }
            if (list == null) item { Text("Looking…", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) }
            else if (list.isEmpty()) item { Text("No upcoming bills in your messages.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) }
            else item {
                GroupedCard {
                    list.forEachIndexed { i, b ->
                        if (i > 0) RowDivider()
                        val state = added[b.key]
                        SettingRow(
                            b.biller,
                            subtitle = listOfNotNull(b.amount?.let { Money.format(it) }, b.dueOn?.let { "due ${formatDay(it)}" }).joinToString(" · "),
                            value = when {
                                b.key !in added -> "Remind me"
                                state == null -> "Already added"
                                else -> "Added"
                            },
                            valueColor = if (b.key in added) c.secondaryLabel else c.gold,
                            showChevron = false,
                            onClick = {
                                val existing = added[b.key]
                                if (existing != null) onOpen(existing)
                                else if (b.key !in added) scope.launch { added = added + (b.key to SmsBills.add(context, b)) }
                            },
                        )
                    }
                }
            }
        }
    }
}
