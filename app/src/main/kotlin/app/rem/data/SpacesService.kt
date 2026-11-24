package app.rem.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.rem.R
import app.rem.core.insights.Money
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.model.Space
import app.rem.core.model.Status
import app.rem.core.model.Trigger
import app.rem.core.parser.QuickAddParser
import app.rem.core.spaces.Aisles
import app.rem.core.spaces.Shopping
import app.rem.core.spaces.ShoppingItem
import app.rem.core.spaces.ShoppingParser
import app.rem.core.spaces.Wish
import app.rem.core.spaces.WishStatus
import app.rem.core.spaces.Wishes
import app.rem.core.spaces.Work
import app.rem.core.spaces.WorkStatus
import app.rem.ui.MainActivity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Shopping, Wishlist and Work: the operations behind each space and their reminders. */
class SpacesService(
    private val context: Context,
    private val db: RemDatabase,
    private val reminders: ReminderService,
    private val repository: ReminderRepository,
    private val settings: Settings,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val today: LocalDate get() = LocalDate.now(clock)

    // ============================================================== Shopping

    suspend fun ensureDefaultLists() {
        if (db.shopping().lists().isEmpty()) {
            listOf("Groceries", "Household", "Pharmacy").forEachIndexed { i, n -> db.shopping().upsertList(ShoppingListEntity(name = n, sortOrder = i)) }
        }
    }

    /** Adds "milk, eggs, 2 bread" as separate items; re-adds existing ones instead of duplicating. */
    suspend fun addItems(listId: Long, text: String): Int {
        val existing = db.shopping().items(listId).map { it.toModel() }
        val parsed = ShoppingParser.split(text)
        parsed.forEach { (name, qty, unit) ->
            val match = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val item = match?.copy(checked = false, quantity = qty ?: match.quantity, unit = unit ?: match.unit)
                ?: ShoppingItem(listId = listId, name = name, quantity = qty, unit = unit, aisle = Aisles.of(name))
            db.shopping().upsertItem(ShoppingItemEntity.from(item))
        }
        nudgeIfLong(listId)
        return parsed.size
    }

    suspend fun toggle(item: ShoppingItem) {
        val next = if (!item.checked) item.copy(checked = true, lastBought = today, timesBought = item.timesBought + 1, runningLow = false)
        else item.copy(checked = false)
        db.shopping().upsertItem(ShoppingItemEntity.from(next))
    }

    suspend fun buyAgain(item: ShoppingItem) {
        db.shopping().upsertItem(ShoppingItemEntity.from(item.copy(checked = false)))
        nudgeIfLong(item.listId)
    }

    suspend fun updateItem(item: ShoppingItem) = db.shopping().upsertItem(ShoppingItemEntity.from(item))

    suspend fun deleteItem(id: Long) = db.shopping().deleteItem(id)

    suspend fun addList(name: String): Long = db.shopping().upsertList(ShoppingListEntity(name = name, sortOrder = db.shopping().lists().size))

    suspend fun deleteList(id: Long) {
        listReminder(id)?.let { reminders.delete(it.id) }
        db.shopping().deleteItemsOf(id)
        db.shopping().deleteList(id)
    }

    /** The reminder attached to a list: at a shop (place) or on a day/time. */
    suspend fun listReminder(listId: Long): Reminder? =
        repository.allActive().firstOrNull { it.kind == Kind.SHOPPING_LIST && it.extras[LIST_ID] == listId.toString() }

    suspend fun setListReminder(listId: Long, placeId: Long?, at: LocalDateTime?, rrule: String?) {
        val list = db.shopping().list(listId) ?: return
        val existing = listReminder(listId)
        if (placeId == null && at == null) {
            existing?.let { reminders.delete(it.id) }
            return
        }
        val base = existing ?: Reminder(title = "${list.name} list", createdAt = Instant.now(clock))
        reminders.save(
            base.copy(
                title = "${list.name} list", kind = Kind.SHOPPING_LIST, category = Category.SHOPPING,
                trigger = if (placeId != null) Trigger.ARRIVE_PLACE else null, dueAt = at, recurrence = rrule,
                extras = buildMap { put(LIST_ID, listId.toString()); placeId?.let { put(app.rem.triggers.Triggers.PLACE_ID, it.toString()) } },
            ),
        )
    }

    /** The notification text for a list reminder, or null if there's nothing to buy. */
    suspend fun listText(listId: Long): String? {
        val list = db.shopping().list(listId) ?: return null
        val items = db.shopping().items(listId).map { it.toModel() }
        if (items.none { !it.checked }) return null
        return Shopping.asText(list.name, items)
    }

    private suspend fun nudgeIfLong(listId: Long) {
        val list = db.shopping().list(listId) ?: return
        if (!list.remindWhenLong) return
        val items = db.shopping().items(listId).map { it.toModel() }
        if (!Shopping.isLong(items)) return
        val prefs = context.getSharedPreferences("rem_spaces", Context.MODE_PRIVATE)
        val key = "longNudge_$listId"
        if (prefs.getString(key, null) == today.toString()) return
        prefs.edit().putString(key, today.toString()).apply()
        post(8000 + listId.toInt(), "${items.count { !it.checked }} things on ${list.name}", "Time for a run? Tap to see the list.", MainActivity.ACTION_SHOPPING)
    }

    /** Daily: staples bought long enough ago go back on the list. Returns how many came back. */
    suspend fun restockStaples(): Int {
        val due = db.shopping().allItems().map { it.toModel() }.filter { Shopping.stapleDue(it, today) }
        due.forEach { db.shopping().upsertItem(ShoppingItemEntity.from(it.copy(checked = false, runningLow = true))) }
        return due.size
    }

    // ============================================================== Wishlist

    suspend fun saveWish(w: Wish): Long = db.wishes().upsert(WishEntity.from(w)).let { if (w.id != 0L) w.id else it }

    suspend fun addShared(text: String): Long = saveWish(Wishes.fromShared(text).copy(addedOn = today, askOn = today.plusDays(Wishes.COOL_OFF_DAYS)))

    suspend fun setWishStatus(w: Wish, status: WishStatus) = saveWish(w.copy(status = status, askOn = null))

    /** "Still want it?" -> keep: ask again in another 30 days. */
    suspend fun keepWish(w: Wish) = saveWish(w.copy(askOn = today.plusDays(Wishes.COOL_OFF_DAYS)))

    suspend fun deleteWish(id: Long) = db.wishes().delete(id)

    /** Daily wishlist nudges: cool-off, payday, sale days, gifts, target price reached. */
    suspend fun wishlistNudges() {
        val wishes = db.wishes().all().map { it.toModel() }
        val s = settings.value
        wishes.filter { Wishes.coolOffDue(it, today) }.take(3).forEach { w ->
            post(8100 + w.id.toInt(), "Still want ${w.title}?", "You added it ${java.time.temporal.ChronoUnit.DAYS.between(w.addedOn, today)} days ago. Keep it, buy it, or let it go.", MainActivity.ACTION_WISHLIST)
        }
        if (Wishes.isPayday(today, s.payday)) {
            val wanted = wishes.filter { it.status == WishStatus.WANTED && it.price != null }
            if (wanted.isNotEmpty()) post(8090, "Payday", "${wanted.size} things on your wishlist. Treat yourself, or save towards one.", MainActivity.ACTION_WISHLIST)
        }
        if (today in s.saleDates) {
            val n = wishes.count { it.status == WishStatus.WANTED }
            if (n > 0) post(8091, "Sale day", "$n wishlist items to check prices on today.", MainActivity.ACTION_WISHLIST)
        }
        wishes.filter { Wishes.giftDue(it, today) }.forEach { w ->
            post(8200 + w.id.toInt(), "Gift: ${w.title}", "The occasion is coming up. Order it in time.", MainActivity.ACTION_WISHLIST)
        }
        wishes.filter { Wishes.atTarget(it) }.forEach { w ->
            post(8300 + w.id.toInt(), "${w.title} is at your price", "Now ${Money.format(w.price!!)}, target ${Money.format(w.targetPrice!!)}.", MainActivity.ACTION_WISHLIST)
        }
    }

    // ============================================================== Work

    suspend fun addProject(name: String): Long {
        val palette = listOf(0xFFB9C3F5, 0xFFA3D6AE, 0xFFF2A296, 0xFFEBCB8E, 0xFFCBB8F0, 0xFF98D2DB)
        return db.projects().upsert(ProjectEntity(name = name, color = palette[db.projects().all().size % palette.size]))
    }

    suspend fun deleteProject(id: Long) {
        repository.allActive().filter { it.projectId == id }.forEach { reminders.save(it.copy(projectId = null)) }
        db.projects().delete(id)
    }

    /** "Send the deck to Ravi by Thursday 3pm" -> a work task, held to work hours. */
    suspend fun addTask(text: String, projectId: Long?, status: WorkStatus = WorkStatus.TODO): Long {
        val parsed = QuickAddParser(clock).parse(text)
        val s = settings.value
        return reminders.save(
            Reminder(
                title = parsed.title, space = Space.WORK, category = Category.WORK, priority = parsed.priority,
                dueAt = parsed.dueAt, recurrence = parsed.recurrence?.toRRule(), projectId = projectId, workStatus = status.name,
                alertOffsets = listOf(0), alertStyle = AlertStyle.NOTIFICATION, createdAt = Instant.now(clock),
                window = if (s.respectWorkHours) s.workHours.window else null,
                extras = if (s.respectWorkHours) mapOf(AUTO_WINDOW to "1") else emptyMap(),
            ),
        )
    }

    suspend fun setStatus(task: Reminder, status: WorkStatus, waitingOn: String? = null) {
        when (status) {
            WorkStatus.DONE -> {
                reminders.complete(task.id)
                repository.get(task.id)?.takeIf { it.status == Status.DONE }?.let { repository.upsert(it.copy(workStatus = WorkStatus.DONE.name)) }
            }
            WorkStatus.WAITING -> {
                val who = waitingOn?.takeIf { it.isNotBlank() } ?: task.waitingOn
                // Follow up after two working days.
                reminders.save(task.copy(workStatus = status.name, waitingOn = who, dueAt = Work.followUpAt(LocalDateTime.now(clock), settings.value.workHours), alertOffsets = listOf(0)))
            }
            else -> reminders.save(task.copy(workStatus = status.name))
        }
    }

    /** Re-applies work hours to every work task when you change them. */
    suspend fun applyWorkHours() {
        val s = settings.value
        repository.allActive().filter { it.space == Space.WORK && (it.extras[AUTO_WINDOW] == "1" || it.window == null) }.forEach { t ->
            reminders.save(t.copy(window = if (s.respectWorkHours) s.workHours.window else null, extras = t.extras + (AUTO_WINDOW to "1")))
        }
    }

    /** Evening wrap-up: today's unfinished work moves to the next working morning. Returns how many moved. */
    suspend fun wrapUp(): Int {
        val morning = Work.nextWorkMorning(today, settings.value.workHours)
        val open = repository.allActive().filter { it.space == Space.WORK && it.dueAt != null && !it.dueAt!!.toLocalDate().isAfter(today) && it.recurrence == null }
        open.forEach { reminders.save(it.copy(dueAt = morning, snoozedUntil = null)) }
        return open.size
    }

    suspend fun workAgenda(): Pair<Int, Int> {
        val work = repository.allActive().filter { it.space == Space.WORK }
        val dueToday = work.count { it.dueAt?.toLocalDate()?.let { d -> !d.isAfter(today) } == true }
        val waiting = work.count { it.workStatus == WorkStatus.WAITING.name }
        return dueToday to waiting
    }

    // ============================================================== Notifications

    fun post(id: Int, title: String, text: String, action: String, extraAction: Pair<String, PendingIntent>? = null) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Shopping, wishlist & work", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        extraAction?.let { (label, pi) -> b.addAction(0, label, pi) }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    companion object {
        const val LIST_ID = "shoppingListId"
        const val AUTO_WINDOW = "autoWindow"
        const val CHANNEL = "spaces"
    }
}
