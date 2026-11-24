package app.rem.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rem.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Alarms are lost on reboot and drift on clock/zone changes: re-plan everything. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.container.service.rescheduleAll()
                app.rem.jobs.DailyJobs.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
