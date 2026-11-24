package app.rem.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rem.container
import app.rem.core.scheduling.SnoozePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles Done / Snooze / Dismiss buttons on notifications. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, intent.action, id, intent.getStringExtra(EXTRA_SNOOZE))
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, action: String?, id: Long, snooze: String?) {
        val service = context.container.service
        when (action) {
            ACTION_DONE -> service.complete(id)
            ACTION_SNOOZE -> service.snooze(id, snooze?.let { runCatching { SnoozePreset.valueOf(it) }.getOrNull() } ?: SnoozePreset.MIN_15)
            ACTION_DISMISS -> service.dismiss(id)
        }
    }

    companion object {
        const val ACTION_DONE = "app.rem.action.DONE"
        const val ACTION_SNOOZE = "app.rem.action.SNOOZE"
        const val ACTION_DISMISS = "app.rem.action.DISMISS"
        const val EXTRA_SNOOZE = "snooze"
    }
}
