package app.rem.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.rem.R
import app.rem.container
import app.rem.core.model.Reminder
import app.rem.ui.MainActivity
import app.rem.ui.format.describeLateness
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatTime
import java.time.LocalDateTime

// Rem Noir on the home screen: charcoal card, ivory buttons, gold accents.
private val Card = ColorProvider(day = Color(0xFF131316), night = Color(0xFF131316))
private val Label = ColorProvider(day = Color(0xFFF3F0E8), night = Color(0xFFF3F0E8))
private val Secondary = ColorProvider(day = Color(0xFF8E8B84), night = Color(0xFF8E8B84))
private val Accent = ColorProvider(day = Color(0xFFD9BC83), night = Color(0xFFD9BC83))
private val Danger = ColorProvider(day = Color(0xFFEC7B6B), night = Color(0xFFEC7B6B))
private val Ivory = ColorProvider(day = Color(0xFFF3F0E8), night = Color(0xFFF3F0E8))
private val Ink = ColorProvider(day = Color(0xFF0B0B0D), night = Color(0xFF0B0B0D))
private val White = Ink

private fun launch(context: Context, action: String? = null, openId: Long? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        action?.let { this.action = it }
        openId?.let { putExtra(MainActivity.EXTRA_OPEN_ID, it) }
    }

data class WidgetRow(val id: Long, val title: String, val time: String, val late: Boolean)

/** Overdue first, then today, then the next few upcoming. */
internal fun widgetRows(active: List<Reminder>, now: LocalDateTime, max: Int = 8): Pair<Int, List<WidgetRow>> {
    val today = now.toLocalDate()
    val dated = active.filter { it.dueAt != null }.sortedBy { it.dueAt }
    val dueToday = dated.count { it.dueAt!!.toLocalDate() <= today }
    val rows = dated.take(max).map { r ->
        val due = r.dueAt!!
        val late = due.isBefore(now)
        val time = when {
            late && due.toLocalDate() == today -> describeLateness(due, now)
            late -> "Overdue"
            due.toLocalDate() == today -> formatTime(due.toLocalTime())
            else -> "${formatDay(due.toLocalDate(), today)}, ${formatTime(due.toLocalTime())}"
        }
        WidgetRow(r.id, r.title, time, late)
    }
    return dueToday to rows
}

/** "Today" widget: count, the list, tick to complete, + and mic to add. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val active = context.container.repository.activeScheduled()
        val (count, rows) = widgetRows(active, LocalDateTime.now())
        provideContent { TodayContent(context, count, rows) }
    }

    @Composable
    private fun TodayContent(context: Context, count: Int, rows: List<WidgetRow>) {
        Column(GlanceModifier.fillMaxSize().background(Card).cornerRadius(26.dp).padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(launch(context)))) {
                    Text("TODAY", style = TextStyle(color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                    Text("$count", style = TextStyle(color = Label, fontSize = 34.sp, fontFamily = androidx.glance.text.FontFamily.Serif))
                }
                RoundButton(R.drawable.ic_widget_add, launch(context, MainActivity.ACTION_QUICK_ADD))
                Spacer(GlanceModifier.width(8.dp))
                RoundButton(R.drawable.ic_widget_mic, launch(context, MainActivity.ACTION_VOICE))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("All clear.", style = TextStyle(color = Secondary, fontSize = 18.sp, fontFamily = androidx.glance.text.FontFamily.Serif))
                }
            } else {
                LazyColumn {
                    items(rows, itemId = { it.id }) { row ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                ImageProvider(R.drawable.widget_ring), "Mark done",
                                modifier = GlanceModifier.size(22.dp).clickable(
                                    actionRunCallback<CompleteAction>(actionParametersOf(CompleteAction.KEY_ID to row.id)),
                                ),
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(launch(context, openId = row.id)))) {
                                Text(row.title, maxLines = 1, style = TextStyle(color = Label, fontSize = 15.sp))
                                Text(row.time, maxLines = 1, style = TextStyle(color = if (row.late) Danger else Secondary, fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RoundButton(icon: Int, intent: Intent) {
        Box(
            GlanceModifier.size(36.dp).cornerRadius(18.dp).background(Ivory).clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) { Image(ImageProvider(icon), null, colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(20.dp)) }
    }
}

/** Compact widget: "New Reminder" and a mic. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().background(Card).cornerRadius(26.dp).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    GlanceModifier.defaultWeight().fillMaxSize().cornerRadius(26.dp).background(Ivory)
                        .clickable(actionStartActivity(launch(context, MainActivity.ACTION_QUICK_ADD))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(ImageProvider(R.drawable.ic_widget_add), null, colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(20.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("New reminder", style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    GlanceModifier.size(52.dp).cornerRadius(26.dp).background(Accent)
                        .clickable(actionStartActivity(launch(context, MainActivity.ACTION_VOICE))),
                    contentAlignment = Alignment.Center,
                ) { Image(ImageProvider(R.drawable.ic_widget_mic), "Say a reminder", colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(24.dp)) }
            }
        }
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY_ID] ?: return
        context.container.service.complete(id)
    }

    companion object {
        val KEY_ID = ActionParameters.Key<Long>("reminderId")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

object Widgets {
    /** Refresh widgets after any data change. Never fails the caller. */
    suspend fun refresh(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
    }
}
