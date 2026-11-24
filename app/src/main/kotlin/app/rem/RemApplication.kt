package app.rem

import android.app.Application
import kotlinx.coroutines.launch
import android.content.Context
import app.rem.alarm.AlarmScheduler
import app.rem.alarm.Notifier
import app.rem.core.parser.QuickAddParser
import app.rem.core.scheduling.ReminderPlanner
import app.rem.data.RemDatabase
import app.rem.data.ReminderRepository
import app.rem.data.ReminderService
import java.time.Clock

/** Manual dependency container: small app, no DI framework needed. */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone(), database: RemDatabase? = null) {
    val appContext: Context = context.applicationContext
    val database: RemDatabase = database ?: RemDatabase.create(appContext)
    val repository = ReminderRepository(this.database.reminders())
    val planner = ReminderPlanner(clock)
    val parser get() = QuickAddParser(Clock.systemDefaultZone())
    val notifier = Notifier(appContext)
    val scheduler = AlarmScheduler(appContext, planner)
    val settings = app.rem.data.Settings(appContext)
    val water = app.rem.data.WaterLog(appContext, clock)
    val service = ReminderService(
        repository, planner, scheduler, notifier,
        logDao = this.database.log(),
        firedDao = this.database.fired(),
        settings = settings,
        clock = clock,
        nextSystemAlarm = {
            appContext.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock
                ?.takeIf { it.showIntent?.creatorPackage != appContext.packageName }
                ?.triggerTime?.let(java.time.Instant::ofEpochMilli)
        },
        onChanged = {
            app.rem.widget.Widgets.refresh(appContext)
            app.rem.triggers.Triggers.sync(appContext)
            app.rem.jobs.Countdown.sync(appContext)
        },
    )
    val spaces = app.rem.data.SpacesService(appContext, this.database, service, repository, settings, clock)
}

class RemApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        // Wired headphones can only be observed while Rem is running.
        androidx.core.content.ContextCompat.registerReceiver(
            this, app.rem.triggers.Triggers.WiredReceiver(),
            android.content.IntentFilter(android.media.AudioManager.ACTION_HEADSET_PLUG),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { app.rem.triggers.Triggers.sync(this@RemApplication) }
            runCatching { app.rem.jobs.DailyJobs.schedule(this@RemApplication) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as RemApplication).container
