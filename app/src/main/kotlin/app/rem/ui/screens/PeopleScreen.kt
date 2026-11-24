package app.rem.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.insights.Anniversaries
import app.rem.core.insights.Nudges
import app.rem.core.insights.Person
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.data.PersonEntity
import app.rem.ui.components.Caption
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SheetHandle
import app.rem.ui.components.Token
import app.rem.ui.edit.FieldRow
import app.rem.ui.format.formatDay
import app.rem.ui.theme.Rem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val CADENCES = listOf<Pair<Int?, String>>(null to "Off", 7 to "Every week", 14 to "Every 2 weeks", 30 to "Every month", 90 to "Every 3 months")

@Composable
fun PeopleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val people by remember { context.container.database.people().observeAll() }.collectAsState(emptyList())
    val today = LocalDate.now()
    var editing by remember { mutableStateOf<Person?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scope.launch { imported = importBirthdays(context) }
    }
    val models = people.map { it.toModel() }

    NoirScreen("People", "Stay *close.*", onBack, trailing = {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.ivory).clickable { editing = Person(name = "") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Add person", tint = c.onIvory) }
    }) {
        val due = models.filter { Nudges.isDue(it, today) }
        if (due.isNotEmpty()) {
            item { Caption("Reach out", count = due.size) }
            item {
                GroupedCard {
                    due.forEachIndexed { i, p ->
                        if (i > 0) RowDivider(66.dp)
                        PersonRow(p, Nudges.message(p, today), highlight = true, onClick = { editing = p },
                            onCall = p.phone?.let { { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                            onDone = { scope.launch { context.container.database.people().upsert(PersonEntity.from(p.copy(lastContact = today))) } })
                    }
                }
            }
        }
        val gifts = models.filter { Nudges.giftsDue(it, today) }
        if (gifts.isNotEmpty()) {
            item { Caption("Gift ideas, coming up") }
            item {
                GroupedCard {
                    gifts.forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        Column(Modifier.padding(18.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.label)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.giftIdeas.take(4).forEach { Token(it, Icons.Rounded.CardGiftcard, tint = c.gold) } }
                        }
                    }
                }
            }
        }
        item { Caption("Everyone", count = models.size) }
        item {
            GroupedCard {
                if (models.isEmpty()) {
                    Text("Add the people you care about. Rem will nudge you to stay in touch and remember every birthday.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(18.dp))
                }
                models.forEachIndexed { i, p ->
                    if (i > 0) RowDivider(66.dp)
                    PersonRow(p, subtitle(p, today), highlight = false, onClick = { editing = p }, onCall = null, onDone = null)
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            GroupedCard {
                SettingRow("Import birthdays from contacts", icon = Icons.Rounded.ContactPage, iconColor = c.secondaryLabel, onClick = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                    value = imported?.let { "$it added" })
            }
        }
    }

    editing?.let { p -> PersonSheet(p, onDismiss = { editing = null }, onSave = { saved -> scope.launch { savePerson(context, saved) }; editing = null },
        onDelete = { scope.launch { context.container.database.people().delete(p.id) }; editing = null }) }
}

private fun subtitle(p: Person, today: LocalDate): String = listOfNotNull(
    p.birthday?.let { b ->
        var next = b.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
        val days = ChronoUnit.DAYS.between(today, next)
        val what = if (b.year > 1900) Anniversaries.describe(b, next, true)?.let { " ($it)" }.orEmpty() else ""
        if (days == 0L) "Birthday today$what" else "Birthday in $days days$what"
    },
    p.anniversary?.let { a ->
        val next = a.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
        Anniversaries.describe(a, next, false)?.let { "$it ${formatDay(next, today)}" }
    },
    p.lastContact?.let { "Last talked ${ChronoUnit.DAYS.between(it, today)} days ago" },
).joinToString("  ·  ").ifEmpty { "No dates yet" }

@Composable
private fun PersonRow(p: Person, subtitle: String, highlight: Boolean, onClick: () -> Unit, onCall: (() -> Unit)?, onDone: (() -> Unit)?) {
    val c = Rem.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.elevated).border(1.dp, if (highlight) c.gold.copy(alpha = 0.5f) else c.separator, CircleShape), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = c.label)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.label)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (highlight) c.gold else c.secondaryLabel, maxLines = 2)
        }
        onCall?.let { Box(Modifier.size(38.dp).clip(CircleShape).border(1.dp, c.separator, CircleShape).clickable(onClick = it), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Call, "Call", tint = c.label, modifier = Modifier.size(18.dp)) } }
        onDone?.let {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.ivory).clickable(onClick = it), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, "Talked today", tint = c.onIvory, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonSheet(initial: Person, onDismiss: () -> Unit, onSave: (Person) -> Unit, onDelete: () -> Unit) {
    val c = Rem.colors
    var p by remember { mutableStateOf(initial) }
    var gifts by remember { mutableStateOf(initial.giftIdeas.joinToString("\n")) }
    var sheet by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.canvas, dragHandle = { SheetHandle() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            GroupedCard {
                FieldRow("Name", p.name, "Grandma") { p = p.copy(name = it) }
                RowDivider()
                FieldRow("Phone", p.phone.orEmpty(), "Optional", KeyboardType.Phone) { p = p.copy(phone = it.ifBlank { null }) }
            }
            Spacer(Modifier.height(14.dp))
            GroupedCard {
                SettingRow("Stay in touch", icon = Icons.Rounded.Schedule, iconColor = c.secondaryLabel, value = CADENCES.first { it.first == p.cadenceDays }.second, onClick = { sheet = "cadence" })
                RowDivider(54.dp)
                SettingRow("Birthday", icon = Icons.Rounded.Cake, iconColor = c.secondaryLabel, value = p.birthday?.let { formatDay(it) + if (it.year > 1900) " ${it.year}" else "" } ?: "Add", onClick = { sheet = "birthday" })
                RowDivider(54.dp)
                SettingRow("Anniversary", icon = Icons.Rounded.Favorite, iconColor = c.secondaryLabel, value = p.anniversary?.let { formatDay(it) + " ${it.year}" } ?: "Add", onClick = { sheet = "anniversary" })
            }
            Spacer(Modifier.height(14.dp))
            GroupedCard {
                Column(Modifier.padding(18.dp)) {
                    Text("GIFT IDEAS", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        if (gifts.isEmpty()) Text("One idea per line. Rem shows them two weeks before their day.", style = MaterialTheme.typography.bodyMedium, color = c.tertiaryLabel)
                        BasicTextField(gifts, { gifts = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.label), cursorBrush = SolidColor(c.gold), modifier = Modifier.fillMaxWidth().height(96.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save", onClick = { onSave(p.copy(giftIdeas = gifts.lines().map { it.trim() }.filter { it.isNotEmpty() })) }, enabled = p.name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            if (initial.id != 0L) TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Remove", color = c.danger) }
        }
    }
    when (sheet) {
        "cadence" -> OptionSheet("Stay in touch", CADENCES, { it == p.cadenceDays }, { p = p.copy(cadenceDays = it, lastContact = p.lastContact ?: LocalDate.now()); sheet = null }, { sheet = null })
        "birthday", "anniversary" -> {
            val initialDate = (if (sheet == "birthday") p.birthday else p.anniversary) ?: LocalDate.now().minusYears(25)
            val ps = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { sheet = null },
                confirmButton = {
                    TextButton(onClick = {
                        ps.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            p = if (sheet == "birthday") p.copy(birthday = d) else p.copy(anniversary = d)
                        }
                        sheet = null
                    }) { Text("Done") }
                },
                dismissButton = { TextButton(onClick = { p = if (sheet == "birthday") p.copy(birthday = null) else p.copy(anniversary = null); sheet = null }) { Text("Clear") } },
            ) { DatePicker(ps) }
        }
    }
}

/** Saves a person and keeps their yearly birthday/anniversary reminders in sync. */
suspend fun savePerson(context: Context, p: Person) {
    val c = context.container
    val id = c.database.people().upsert(PersonEntity.from(p)).let { if (p.id != 0L) p.id else it }
    val existing = c.repository.allActive().filter { it.personId == id }
    suspend fun ensure(kindLabel: String, date: LocalDate?, birthday: Boolean) {
        val title = if (birthday) "${p.name}'s birthday" else "${p.name}'s anniversary"
        val current = existing.firstOrNull { it.extras["occasion"] == kindLabel }
        if (date == null) { current?.let { c.service.delete(it.id) }; return }
        val today = LocalDate.now()
        val next = date.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
        c.service.save(
            (current ?: Reminder(title = title, createdAt = Instant.now())).copy(
                title = title, category = Category.BIRTHDAY, recurrence = "FREQ=YEARLY", dueAt = next.atTime(9, 0),
                alertOffsets = listOf(7 * 1440, 1440, 0), personId = id,
                extras = mapOf("occasion" to kindLabel) + if (date.year > 1900) mapOf(Anniversaries.ORIGIN to date.toString()) else emptyMap(),
            ),
        )
    }
    ensure("birthday", p.birthday, true)
    ensure("anniversary", p.anniversary, false)
}

/** Reads birthdays saved in the phone's contacts and adds people + yearly reminders. */
suspend fun importBirthdays(context: Context): Int = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val projection = arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE)
    val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
    val args = arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString())
    val known = context.container.database.people().all().map { it.name.lowercase() }.toSet()
    var added = 0
    resolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cur ->
        while (cur.moveToNext()) {
            val name = cur.getString(0) ?: continue
            if (name.lowercase() in known) continue
            val raw = cur.getString(1) ?: continue
            // Contacts store "1990-03-12" or "--03-12" (no year).
            val date = runCatching { if (raw.startsWith("--")) LocalDate.parse("1900" + raw.drop(1)) else LocalDate.parse(raw.take(10)) }.getOrNull() ?: continue
            savePerson(context, Person(name = name, birthday = date))
            added++
        }
    }
    added
}
