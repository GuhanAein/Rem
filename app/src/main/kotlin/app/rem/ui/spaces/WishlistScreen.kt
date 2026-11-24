package app.rem.ui.spaces

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.insights.Money
import app.rem.core.spaces.Wish
import app.rem.core.spaces.WishStatus
import app.rem.core.spaces.Wishes
import app.rem.ui.components.Caption
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SecondaryButton
import app.rem.ui.components.SegmentedControl
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SheetHandle
import app.rem.ui.components.noirSurface
import app.rem.ui.edit.FieldRow
import app.rem.ui.edit.PhotoRow
import app.rem.ui.edit.rememberUriBitmap
import app.rem.ui.format.formatDay
import app.rem.ui.onboarding.editorial
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen() {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val spaces = context.container.spaces
    val settings by context.container.settings.state.collectAsState()
    val wishes = remember { context.container.database.wishes().observeAll() }.collectAsState(emptyList()).value.map { it.toModel() }
    val people by remember { context.container.database.people().observeAll() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Wish?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    val today = LocalDate.now()
    val wanted = wishes.filter { it.status == WishStatus.WANTED }
    val ask = wanted.filter { Wishes.coolOffDue(it, today) }

    LazyColumn(Modifier.fillMaxSize().background(c.canvas), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = LocalBottomInset.current + 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("WISHLIST", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 2.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(editorial("Want *less,*\nlove *more.*"), style = MaterialTheme.typography.displaySmall, color = c.label)
                    }
                    RoundAction(Icons.Rounded.Add, "Add wish") { editing = Wish(title = "") }
                }
                val value = wanted.sumOf { it.price ?: 0.0 }
                if (value > 0) Text("${wanted.size} wanted · ${Money.format(value)}", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
            }
        }
        // Cool-off check-ins
        if (ask.isNotEmpty()) {
            item { Caption("Still want these?", count = ask.size, color = c.gold) }
            ask.forEach { w ->
                item(key = "ask-${w.id}") {
                    Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().noirSurface().padding(18.dp).animateItem()) {
                        Text(w.title, style = MaterialTheme.typography.headlineSmall, color = c.label)
                        Text("Added ${java.time.temporal.ChronoUnit.DAYS.between(w.addedOn, today)} days ago${w.price?.let { " · ${Money.format(it)}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Let go", { scope.launch { spaces.setWishStatus(w, WishStatus.LET_GO) } }, Modifier.weight(1f))
                            SecondaryButton("Keep", { scope.launch { spaces.keepWish(w) } }, Modifier.weight(1f))
                            PrimaryButton("Bought", { scope.launch { spaces.setWishStatus(w, WishStatus.BOUGHT) } }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { SegmentedControl(listOf("Wanted", "Gifts", "Bought"), tab, { tab = it }, Modifier.padding(top = 18.dp, bottom = 6.dp), counts = listOf(wanted.count { it.forPersonId == null }, wanted.count { it.forPersonId != null }, wishes.count { it.status == WishStatus.BOUGHT || it.status == WishStatus.RECEIVED })) }
        val shown = when (tab) {
            0 -> wanted.filter { it.forPersonId == null }
            1 -> wanted.filter { it.forPersonId != null }
            else -> wishes.filter { it.status == WishStatus.BOUGHT || it.status == WishStatus.RECEIVED }
        }
        shown.forEach { w ->
            item(key = "w-${w.id}") { WishCard(w, people.firstOrNull { it.id == w.forPersonId }?.name, Modifier.padding(top = 10.dp).animateItem()) { editing = w } }
        }
        if (shown.isEmpty()) item {
            Text(
                if (tab == 1) "Save gift ideas for people you love; Rem reminds you two weeks before their day."
                else "Share any product from Amazon, Flipkart or Myntra to Rem, or tap +. Rem asks again in 30 days, so you only buy what you still want.",
                style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(top = 20.dp, start = 4.dp),
            )
        }
        item {
            Caption("Money")
            GroupedCard {
                SettingRow("Payday", icon = Icons.Rounded.Payments, iconColor = c.secondaryLabel, value = settings.payday?.let { "${app.rem.core.insights.Anniversaries.ordinal(it)} of the month" } ?: "Not set", onClick = { sheet = "payday" })
                RowDivider(54.dp)
                SettingRow("Sale days", icon = Icons.Rounded.Event, iconColor = c.secondaryLabel,
                    value = settings.saleDates.filter { !it.isBefore(today) }.sorted().take(2).joinToString(", ") { formatDay(it) }.ifEmpty { "Add" }, onClick = { sheet = "sale" })
            }
        }
    }

    editing?.let { w ->
        WishSheet(w, people.map { it.id to it.name }, onDismiss = { editing = null },
            onSave = { scope.launch { spaces.saveWish(if (it.id == 0L) it.copy(addedOn = today, askOn = today.plusDays(Wishes.COOL_OFF_DAYS)) else it) }; editing = null },
            onDelete = { scope.launch { spaces.deleteWish(w.id) }; editing = null })
    }
    when (sheet) {
        "payday" -> OptionSheet("Payday", (listOf<Int?>(null) + listOf(1, 5, 7, 10, 15, 25, 28, 30, 31)).map { it to (it?.let { d -> "${app.rem.core.insights.Anniversaries.ordinal(d)} of the month" } ?: "Not set") },
            { it == settings.payday }, { d -> context.container.settings.update { it.copy(payday = d) }; sheet = null }, { sheet = null })
        "sale" -> {
            val ps = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().plusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            SaleDateDialog(ps, onPick = { d -> context.container.settings.update { it.copy(saleDates = (it.saleDates + d).distinct().sorted()) }; sheet = null }, onDismiss = { sheet = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleDateDialog(state: androidx.compose.material3.DatePickerState, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }) { Text("Add sale day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@Composable
private fun WishCard(w: Wish, forName: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = Rem.colors
    Row(modifier.fillMaxWidth().noirSurface().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val bmp = rememberUriBitmap(w.photo)
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(c.elevated), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, w.title, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
            else Icon(if (forName != null) Icons.Rounded.CardGiftcard else Icons.Rounded.Link, null, tint = c.tertiaryLabel)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(w.title, style = MaterialTheme.typography.titleMedium, color = c.label, maxLines = 2)
            val meta = listOfNotNull(
                forName?.let { "For $it" },
                w.occasionDate?.let { d -> var n = d.withYear(LocalDate.now().year); if (n.isBefore(LocalDate.now())) n = n.plusYears(1); formatDay(n) },
                w.targetPrice?.let { "Target ${Money.format(it)}" },
                "At your price".takeIf { Wishes.atTarget(w) },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = if (Wishes.atTarget(w)) c.gold else c.secondaryLabel)
            if (w.saved > 0 && w.price != null) {
                Spacer(Modifier.height(8.dp))
                val p = Wishes.progress(w)
                val gold = c.gold
                val track = c.fill
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(gold, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
                Text("${Money.format(w.saved)} saved", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
            }
        }
        w.price?.let { Text(Money.format(it), style = MaterialTheme.typography.headlineSmall, color = c.label, modifier = Modifier.padding(start = 10.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishSheet(initial: Wish, people: List<Pair<Long, String>>, onDismiss: () -> Unit, onSave: (Wish) -> Unit, onDelete: () -> Unit) {
    val c = Rem.colors
    val context = LocalContext.current
    var w by remember { mutableStateOf(initial) }
    var price by remember { mutableStateOf(initial.price?.let { Money.format(it, "") }.orEmpty()) }
    var target by remember { mutableStateOf(initial.targetPrice?.let { Money.format(it, "") }.orEmpty()) }
    var saved by remember { mutableStateOf(if (initial.saved > 0) Money.format(initial.saved, "") else "") }
    var sheet by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.canvas, dragHandle = { SheetHandle() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            GroupedCard {
                FieldRow("Item", w.title, "e.g. Sony headphones") { w = w.copy(title = it) }
                RowDivider()
                FieldRow("Link", w.url.orEmpty(), "Paste a product link", KeyboardType.Uri, icon = null) { w = w.copy(url = it.ifBlank { null }) }
                RowDivider()
                PhotoRow("Photo", w.photo) { w = w.copy(photo = it) }
            }
            Spacer(Modifier.height(12.dp))
            GroupedCard {
                FieldRow("Price", price, "₹", KeyboardType.Decimal) { price = it }
                RowDivider()
                FieldRow("Tell me at", target, "target price", KeyboardType.Decimal) { target = it }
                RowDivider()
                FieldRow("Saved so far", saved, "₹0", KeyboardType.Decimal) { saved = it }
            }
            Spacer(Modifier.height(12.dp))
            GroupedCard {
                SettingRow("A gift for", icon = Icons.Rounded.Person, iconColor = c.secondaryLabel, value = people.firstOrNull { it.first == w.forPersonId }?.second ?: "Me", onClick = { sheet = "person" })
                RowDivider(54.dp)
                SettingRow("Occasion", icon = Icons.Rounded.Event, iconColor = c.secondaryLabel, value = w.occasionDate?.let { formatDay(it) } ?: "None", onClick = { sheet = "date" })
            }
            w.url?.let { url ->
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Open product page", { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Link)
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save", onClick = {
                onSave(w.copy(price = app.rem.core.insights.Money.parse(price), targetPrice = app.rem.core.insights.Money.parse(target), saved = app.rem.core.insights.Money.parse(saved) ?: 0.0))
            }, enabled = w.title.isNotBlank(), modifier = Modifier.fillMaxWidth())
            if (initial.id != 0L) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    if (w.status == WishStatus.WANTED) TextButton(onClick = { onSave(w.copy(status = WishStatus.BOUGHT, askOn = null)) }) { Text("Mark bought", color = c.gold) }
                    if (w.status == WishStatus.BOUGHT) TextButton(onClick = { onSave(w.copy(status = WishStatus.RECEIVED)) }) { Text("Mark received", color = c.gold) }
                    TextButton(onClick = onDelete) { Text("Remove", color = c.danger) }
                }
            }
        }
    }
    when (sheet) {
        "person" -> OptionSheet("A gift for", listOf<Pair<Long?, String>>(null to "Me") + people.map { it.first to it.second }, { it == w.forPersonId }, { w = w.copy(forPersonId = it); sheet = null }, { sheet = null })
        "date" -> {
            val ps = rememberDatePickerState(initialSelectedDateMillis = (w.occasionDate ?: LocalDate.now().plusMonths(1)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { sheet = null },
                confirmButton = { TextButton(onClick = { ps.selectedDateMillis?.let { w = w.copy(occasionDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; sheet = null }) { Text("Done") } },
                dismissButton = { TextButton(onClick = { w = w.copy(occasionDate = null); sheet = null }) { Text("Clear") } },
            ) { DatePicker(ps) }
        }
    }
}
