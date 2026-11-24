package app.rem.ui.spaces

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.insights.Money
import app.rem.core.spaces.Shopping
import app.rem.core.spaces.ShoppingItem
import app.rem.ui.components.Caption
import app.rem.ui.components.CheckCircle
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.noirSurface
import app.rem.ui.edit.FieldRow
import app.rem.ui.motion.rememberHaptics
import app.rem.ui.onboarding.editorial
import app.rem.ui.theme.Rem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen() {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val spaces = context.container.spaces
    val dao = context.container.database.shopping()
    LaunchedEffect(Unit) { spaces.ensureDefaultLists() }
    val lists by remember { dao.observeLists() }.collectAsState(emptyList())
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val list = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()
    val itemsFlow = remember(list?.id) { list?.let { dao.observeItems(it.id) } ?: flowOf(emptyList()) }
    val items = itemsFlow.collectAsState(emptyList()).value.map { it.toModel() }
    val reminder by produceState<app.rem.core.model.Reminder?>(null, list?.id, items.size) { value = list?.let { spaces.listReminder(it.id) } }
    val places by remember { context.container.database.places().observeAll() }.collectAsState(emptyList())

    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val toBuy = items.filter { !it.checked }
    val inCart = items.filter { it.checked && it.lastBought == LocalDate.now() }
    val total = Shopping.estimatedTotal(items)

    LazyColumn(
        Modifier.fillMaxSize().background(c.canvas),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = LocalBottomInset.current + 24.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 20.dp)) {
                Text("SHOPPING", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(editorial(if (toBuy.isEmpty()) "All *stocked.*" else "${toBuy.size} to *buy.*"), style = MaterialTheme.typography.displaySmall, color = c.label, modifier = Modifier.weight(1f))
                    RoundAction(if (reminder != null) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, "List reminder", highlighted = reminder != null) { sheet = "remind" }
                    Spacer(Modifier.width(8.dp))
                    RoundAction(Icons.Rounded.IosShare, "Share list") {
                        list?.let { l ->
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Shopping.asText(l.name, items)), "Share list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
                if (total > 0) Text("About ${Money.format(total)}", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                reminder?.let { r ->
                    Text(
                        if (r.trigger != null) "Reminds you at ${places.firstOrNull { it.id.toString() == r.extras[app.rem.triggers.Triggers.PLACE_ID] }?.name ?: "the shop"}"
                        else "Reminds you ${app.rem.ui.format.describeDue(r.dueAt)}" + (r.recurrence?.let { " · weekly" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = c.gold,
                    )
                }
            }
        }
        // List switcher
        item {
            LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lists, key = { it.id }) { l ->
                    val on = l.id == list?.id
                    Text(
                        l.name, style = MaterialTheme.typography.labelLarge, color = if (on) c.onIvory else c.label,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.ivory else c.surface)
                            .border(1.dp, if (on) c.ivory else c.separator, RoundedCornerShape(50))
                            .combinedClickable(onClick = { selectedId = l.id }, onLongClick = { selectedId = l.id; sheet = "list" })
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                item {
                    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.separator, RoundedCornerShape(50)).clickable { newList = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(Icons.Rounded.Add, "New list", tint = c.gold, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // Fast add
        item {
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().noirSurface(RoundedCornerShape(26.dp)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) Text("milk, eggs, 2 bread, 1kg rice", style = MaterialTheme.typography.bodyLarge, color = c.tertiaryLabel)
                    BasicTextField(
                        input, { input = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.label), cursorBrush = SolidColor(c.gold), singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { list?.let { l -> if (input.isNotBlank()) scope.launch { spaces.addItems(l.id, input); input = "" } } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SendOrMic(input.isNotBlank(), onSend = { list?.let { l -> haptics.tick(); scope.launch { spaces.addItems(l.id, input); input = "" } } }, onMic = { voice = true })
            }
        }
        // Buy again
        val again = Shopping.buyAgain(items).filter { it.lastBought != LocalDate.now() }
        if (again.isNotEmpty()) {
            item {
                Caption("Buy again")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(again, key = { it.id }) { i ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.separator, RoundedCornerShape(50)).clickable { haptics.tick(); scope.launch { spaces.buyAgain(i) } }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = c.gold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(i.name, style = MaterialTheme.typography.labelMedium, color = c.label)
                        }
                    }
                }
            }
        }
        // To buy, by aisle
        Shopping.grouped(items).forEach { (aisle, group) ->
            item(key = "aisle-${aisle.name}") {
                Caption(aisle.label, Modifier.animateItem(), count = group.size)
                GroupedCard(Modifier.animateItem()) {
                    group.forEachIndexed { i, item ->
                        if (i > 0) RowDivider(56.dp)
                        ItemRow(item, onToggle = { haptics.confirm(); scope.launch { spaces.toggle(item) } }, onOpen = { editing = item })
                    }
                }
            }
        }
        if (toBuy.isEmpty() && lists.isNotEmpty()) {
            item { Text("Type a few things above, separated by commas. Rem sorts them by aisle.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(top = 28.dp, start = 4.dp)) }
        }
        if (inCart.isNotEmpty()) {
            item(key = "cart") {
                Caption("In the cart", Modifier.animateItem(), count = inCart.size)
                GroupedCard(Modifier.animateItem()) {
                    inCart.forEachIndexed { i, item ->
                        if (i > 0) RowDivider(56.dp)
                        ItemRow(item, onToggle = { scope.launch { spaces.toggle(item) } }, onOpen = { editing = item })
                    }
                }
            }
        }
    }

    if (voice) list?.let { l ->
        app.rem.ui.voice.VoiceInputSheet(
            caption = "Add to ${l.name}", example = "milk, eggs and two bread", actionLabel = "Add to list",
            onConfirm = { text -> scope.launch { spaces.addItems(l.id, text, spoken = true) }; voice = false },
            onDismiss = { voice = false },
            preview = { text ->
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.rem.core.spaces.ShoppingParser.splitSpoken(text).forEach { (name, qty, unit) ->
                        app.rem.ui.components.Token(listOfNotNull(name, qty?.let { q -> (if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()) + (unit?.let { " $it" } ?: "") }).joinToString(" \u00B7 "), null, tint = c.gold)
                    }
                }
            },
        )
    }

    editing?.let { item -> ItemDialog(item, onDismiss = { editing = null }, onSave = { scope.launch { spaces.updateItem(it) }; editing = null }, onDelete = { scope.launch { spaces.deleteItem(item.id) }; editing = null }) }

    when (sheet) {
        "remind" -> list?.let { l ->
            val sat = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(LocalTime.of(10, 0))
            val sun = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(10, 0))
            val options = buildList<Pair<String, String>> {
                places.forEach { add("place:${it.id}" to "When I arrive at ${it.name}") }
                add("sat" to "Every Saturday, 10 AM")
                add("sun" to "Every Sunday, 10 AM")
                add("tonight" to "Tonight, 7 PM")
                add("off" to "No reminder")
            }
            OptionSheet("Remind me about ${l.name}", options, { false }, { key ->
                scope.launch {
                    when {
                        key.startsWith("place:") -> spaces.setListReminder(l.id, key.removePrefix("place:").toLong(), null, null)
                        key == "sat" -> spaces.setListReminder(l.id, null, sat, "FREQ=WEEKLY;BYDAY=SA")
                        key == "sun" -> spaces.setListReminder(l.id, null, sun, "FREQ=WEEKLY;BYDAY=SU")
                        key == "tonight" -> spaces.setListReminder(l.id, null, LocalDate.now().atTime(19, 0), null)
                        else -> spaces.setListReminder(l.id, null, null, null)
                    }
                }
                sheet = null
            }, { sheet = null }, footer = {
                if (places.isEmpty()) Text("Save a shop in Settings › Places to get reminded when you arrive there.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(top = 10.dp, start = 6.dp))
            })
        }
        "list" -> list?.let { l ->
            OptionSheet(l.name, listOf("long" to (if (l.remindWhenLong) "Don't nudge when the list gets long" else "Nudge me when the list gets long"), "delete" to "Delete this list"), { false }, { k ->
                scope.launch {
                    if (k == "delete") { spaces.deleteList(l.id); selectedId = null } else dao.upsertList(l.copy(remindWhenLong = !l.remindWhenLong))
                }
                sheet = null
            }, { sheet = null })
        }
    }
    if (newList) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newList = false },
            title = { Text("New list") },
            text = { GroupedCard { FieldRow("Name", name, "e.g. DMart, Pharmacy") { name = it } } },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) scope.launch { selectedId = spaces.addList(name.trim()) }; newList = false }) { Text("Create", color = c.gold) } },
            dismissButton = { TextButton(onClick = { newList = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = Rem.colors
    val dim by animateFloatAsState(if (item.checked) 0.4f else 1f, tween(250), label = "dim")
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onOpen).padding(start = 6.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(c.gold, item.checked, onToggle)
        Column(Modifier.weight(1f).graphicsLayer { alpha = dim }) {
            val strike = c.secondaryLabel
            Text(item.name, style = MaterialTheme.typography.titleMedium, color = c.label, modifier = Modifier.drawWithContent {
                drawContent()
                if (item.checked) drawLine(strike, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2.dp.toPx())
            })
            val meta = listOfNotNull(
                item.everyDays?.let { "Every $it days" },
                "Running low".takeIf { item.runningLow && !item.checked },
                item.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = if (item.runningLow) c.gold else c.secondaryLabel)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.graphicsLayer { alpha = dim }) {
            item.quantityText?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.label) }
            item.price?.let { Text(Money.format(it), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel) }
        }
    }
}

@Composable
private fun ItemDialog(item: ShoppingItem, onDismiss: () -> Unit, onSave: (ShoppingItem) -> Unit, onDelete: () -> Unit) {
    val c = Rem.colors
    var qty by remember { mutableStateOf(item.quantity?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty()) }
    var unit by remember { mutableStateOf(item.unit.orEmpty()) }
    var price by remember { mutableStateOf(item.price?.toString().orEmpty()) }
    var every by remember { mutableStateOf(item.everyDays?.toString().orEmpty()) }
    var note by remember { mutableStateOf(item.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(Modifier.animateContentSize()) {
                GroupedCard {
                    FieldRow("Quantity", qty, "e.g. 2", KeyboardType.Decimal) { qty = it }
                    RowDivider()
                    FieldRow("Unit", unit, "kg, L, pack") { unit = it }
                    RowDivider()
                    FieldRow("Price each", price, "₹", KeyboardType.Decimal) { price = it }
                    RowDivider()
                    FieldRow("Staple: every (days)", every, "e.g. 3", KeyboardType.Number) { every = it }
                    RowDivider()
                    FieldRow("Note", note, "brand, size…") { note = it }
                }
                Spacer(Modifier.height(8.dp))
                GroupedCard { SettingRow("Running low, add to list", icon = Icons.Rounded.Refresh, iconColor = c.secondaryLabel, onClick = { onSave(item.copy(checked = false, runningLow = true)) }) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(item.copy(quantity = qty.toDoubleOrNull(), unit = unit.ifBlank { null }, price = price.toDoubleOrNull(), everyDays = every.toIntOrNull(), note = note.trim()))
            }) { Text("Save", color = c.gold) }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text("Remove", color = c.danger) } },
    )
}

/** Mic when the field is empty, send arrow once you type: like a chat app. */
@Composable
fun SendOrMic(hasText: Boolean, onSend: () -> Unit, onMic: () -> Unit) {
    val c = Rem.colors
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (hasText) c.ivory else c.gold.copy(alpha = 0.16f))
            .clickable(onClickLabel = if (hasText) "Add" else "Speak", onClick = if (hasText) onSend else onMic),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (hasText) Icons.Rounded.ArrowUpward else Icons.Rounded.Mic, if (hasText) "Add" else "Speak", tint = if (hasText) c.onIvory else c.gold, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    val c = Rem.colors
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (highlighted) c.gold.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (highlighted) c.gold.copy(alpha = 0.5f) else c.separator, CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (highlighted) c.gold else c.label, modifier = Modifier.size(19.dp)) }
}

