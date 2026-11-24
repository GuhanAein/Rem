package app.rem.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.insights.CarLog
import app.rem.core.insights.Money
import app.rem.core.insights.Rotation
import app.rem.core.insights.Subscriptions
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.ui.components.Caption
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.noirSurface
import app.rem.ui.edit.FieldRow
import app.rem.ui.edit.MEMBERS
import app.rem.ui.edit.PHOTO
import app.rem.ui.edit.ROTATION_START
import app.rem.ui.edit.rememberUriBitmap
import app.rem.ui.format.formatDay
import app.rem.ui.theme.Rem
import app.rem.core.recurrence.describe
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One-tap presets for things people forget: sensible titles, categories and repeats. */
data class Preset(val title: String, val category: Category, val rrule: String?, val daysFromNow: Long, val group: String)

val PRESETS = listOf(
    Preset("Clean the AC filter", Category.HOME, "FREQ=MONTHLY;INTERVAL=3", 30, "Home"),
    Preset("Change water purifier filter", Category.HOME, "FREQ=MONTHLY;INTERVAL=6", 60, "Home"),
    Preset("Book gas cylinder", Category.HOME, "FREQ=DAILY;INTERVAL=45", 30, "Home"),
    Preset("Pest control", Category.HOME, "FREQ=MONTHLY;INTERVAL=3", 45, "Home"),
    Preset("Deep clean the kitchen", Category.HOME, "FREQ=MONTHLY", 14, "Home"),
    Preset("Pay school fees", Category.BILL, "FREQ=MONTHLY;INTERVAL=3", 30, "Kids"),
    Preset("Vaccination due", Category.HEALTH, null, 30, "Kids"),
    Preset("Exam preparation starts", Category.EVENT, null, 21, "Kids"),
    Preset("Parent-teacher meeting", Category.EVENT, null, 14, "Kids"),
)

@Composable
fun LifeScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: (Kind) -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val active by remember { context.container.repository.observeActive() }.collectAsState(emptyList())
    val today = LocalDate.now()
    var odometerFor by remember { mutableStateOf<Reminder?>(null) }

    fun of(k: Kind) = active.filter { it.kind == k }

    NoirScreen("Life admin", "Everything,\n*handled.*", onBack) {
        // ---- Subscriptions
        val subs = of(Kind.SUBSCRIPTION)
        item {
            Caption("Subscriptions", count = subs.size)
            Column(Modifier.fillMaxWidth().noirSurface().padding(18.dp)) {
                Text("PER MONTH", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
                Text(Money.format(Subscriptions.monthlyTotal(subs), Money.symbol(subs.firstOrNull()?.amount)), style = MaterialTheme.typography.displaySmall, color = c.label)
                Text("${Money.format(Subscriptions.monthlyTotal(subs) * 12, Money.symbol(subs.firstOrNull()?.amount))} a year", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                subs.forEach { s ->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium, color = c.label)
                            val renews = s.dueAt?.let { "Renews ${formatDay(it.toLocalDate(), today)}" } ?: "No renewal date"
                            val trial = if (Subscriptions.trialEndingSoon(s, today)) "  ·  Trial ends soon" else ""
                            Text(renews + trial, style = MaterialTheme.typography.bodySmall, color = if (trial.isNotEmpty()) c.danger else c.secondaryLabel)
                        }
                        Text("${s.amount.orEmpty()}/${Subscriptions.cycleOf(s.recurrence).label}", style = MaterialTheme.typography.labelMedium, color = c.gold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                AddLink("Add subscription") { onNew(Kind.SUBSCRIPTION) }
            }
        }

        // ---- Warranty vault
        val warranties = of(Kind.WARRANTY)
        item {
            Caption("Warranty vault", count = warranties.size)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(warranties, key = { it.id }) { w -> PhotoTile(w.extras[PHOTO], w.extras["product"] ?: w.title, w.dueAt?.let { "Expires ${formatDay(it.toLocalDate(), today)}" } ?: "", Icons.Rounded.ReceiptLong) { onOpen(w.id) } }
                item { NewTile("Add receipt") { onNew(Kind.WARRANTY) } }
            }
        }

        // ---- Car
        val cars = of(Kind.CAR_SERVICE)
        item {
            Caption("Car", count = cars.size)
            GroupedCard {
                cars.forEachIndexed { i, car ->
                    if (i > 0) RowDivider()
                    val left = CarLog.kmLeft(car)
                    val est = CarLog.estimateDate(car)?.let { " · ~${formatDay(it, today)}" }.orEmpty()
                    SettingRow(car.title, value = left?.let { if (it > 0) "%,d km left".format(it) + est else "Due now" } ?: "Log odometer", onClick = { odometerFor = car })
                }
                if (cars.isNotEmpty()) RowDivider()
                SettingRow("Add a service reminder", valueColor = c.gold, onClick = { onNew(Kind.CAR_SERVICE) }, showChevron = false, trailing = { Icon(Icons.Rounded.Add, null, tint = c.gold) })
            }
        }

        // ---- Plants
        val plants = of(Kind.PLANT)
        item {
            Caption("Plants", count = plants.size)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(plants, key = { it.id }) { p ->
                    val days = p.dueAt?.let { ChronoUnit.DAYS.between(today, it.toLocalDate()) }
                    PhotoTile(p.extras[PHOTO], p.title, when { days == null -> ""; days <= 0 -> "Water today"; days == 1L -> "Water tomorrow"; else -> "Water in $days days" }, Icons.Rounded.LocalFlorist) { onOpen(p.id) }
                }
                item { NewTile("Add plant") { onNew(Kind.PLANT) } }
            }
        }

        // ---- Lend & borrow, returns
        val lent = of(Kind.LEND) + of(Kind.BORROW)
        val returns = of(Kind.RETURN)
        item {
            Caption("Lent, borrowed & returns", count = lent.size + returns.size)
            GroupedCard {
                lent.forEach { l ->
                    val who = l.extras["person"].orEmpty()
                    val item = l.extras["item"] ?: l.title
                    SettingRow(if (l.kind == Kind.LEND) "$item → $who" else "$item ← $who", value = l.dueAt?.let { formatDay(it.toLocalDate(), today) }, onClick = { onOpen(l.id) })
                    RowDivider()
                }
                returns.forEach { r ->
                    val days = r.dueAt?.let { ChronoUnit.DAYS.between(today, it.toLocalDate()) }
                    SettingRow(r.title, value = days?.let { if (it >= 0) "$it days left" else "Window closed" }, valueColor = if ((days ?: 99) <= 2) c.danger else c.secondaryLabel, onClick = { onOpen(r.id) })
                    RowDivider()
                }
                Row {
                    Box(Modifier.weight(1f)) { SettingRow("Lend", valueColor = c.gold, onClick = { onNew(Kind.LEND) }, trailing = { Icon(Icons.Rounded.Add, null, tint = c.gold) }) }
                    Box(Modifier.weight(1f)) { SettingRow("Borrow", onClick = { onNew(Kind.BORROW) }, trailing = { Icon(Icons.Rounded.Add, null, tint = c.gold) }) }
                    Box(Modifier.weight(1f)) { SettingRow("Return", onClick = { onNew(Kind.RETURN) }, trailing = { Icon(Icons.Rounded.Add, null, tint = c.gold) }) }
                }
            }
        }

        // ---- Chores
        val chores = of(Kind.CHORE)
        item {
            Caption("Family chores", count = chores.size)
            GroupedCard {
                chores.forEach { ch ->
                    val members = ch.extras[MEMBERS].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val start = ch.extras[ROTATION_START]?.let(LocalDate::parse) ?: today
                    SettingRow(ch.title, value = Rotation.assignee(members, start, ch.dueAt?.toLocalDate() ?: today)?.let { "$it's turn" }, valueColor = c.gold, onClick = { onOpen(ch.id) })
                    RowDivider()
                }
                SettingRow("Add a rotating chore", onClick = { onNew(Kind.CHORE) }, trailing = { Icon(Icons.Rounded.Add, null, tint = c.gold) })
            }
        }

        // ---- Presets
        PRESETS.groupBy { it.group }.forEach { (group, list) ->
            item {
                Caption("$group essentials")
                GroupedCard {
                    list.forEachIndexed { i, p ->
                        if (i > 0) RowDivider(66.dp)
                        val exists = active.any { it.title.equals(p.title, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !exists) {
                                scope.launch {
                                    val id = context.container.service.save(
                                        Reminder(
                                            title = p.title, category = p.category, recurrence = p.rrule,
                                            dueAt = LocalDateTime.now().toLocalDate().plusDays(p.daysFromNow).atTime(10, 0),
                                            alertOffsets = listOf(1440, 0), alertStyle = AlertStyle.NOTIFICATION, createdAt = Instant.now(),
                                        ),
                                    )
                                    onOpen(id)
                                }
                            }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryGlyph(p.category, size = 32.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge, color = c.label)
                                Text(p.rrule?.let { app.rem.core.recurrence.RecurrenceRule.parseOrNull(it)?.describe() } ?: "One-time", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                            }
                            if (exists) Text("Added", style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                            else Icon(Icons.Rounded.Add, "Add", tint = c.gold)
                        }
                    }
                }
            }
        }
    }

    odometerFor?.let { car ->
        var km by remember { mutableStateOf(car.extras[CarLog.ODOMETER].orEmpty()) }
        AlertDialog(
            onDismissRequest = { odometerFor = null },
            title = { Text("Odometer reading") },
            text = { GroupedCard { FieldRow("Kilometres", km, "21500", KeyboardType.Number) { km = it } } },
            confirmButton = {
                TextButton(onClick = {
                    km.toIntOrNull()?.let { v -> scope.launch { context.container.service.save(CarLog.withOdometer(car, v, today)) } }
                    odometerFor = null
                }) { Text("Save", color = c.gold) }
            },
            dismissButton = { TextButton(onClick = { odometerFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddLink(text: String, onClick: () -> Unit) {
    val c = Rem.colors
    Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, null, tint = c.gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.gold)
    }
}

@Composable
private fun PhotoTile(uri: String?, title: String, subtitle: String, fallback: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Rem.colors
    Column(Modifier.width(140.dp).noirSurface(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(10.dp)) {
        val bmp = rememberUriBitmap(uri)
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(c.elevated), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(96.dp))
            else Icon(fallback, null, tint = c.tertiaryLabel, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun NewTile(text: String, onClick: () -> Unit) {
    val c = Rem.colors
    Column(
        Modifier.width(120.dp).height(152.dp).noirSurface(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.gold)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
    }
}
