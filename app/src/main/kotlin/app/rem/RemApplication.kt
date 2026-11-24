package app.rem

import android.app.Application
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
    private val appContext = context.applicationContext
    val database: RemDatabase = database ?: RemDatabase.create(appContext)
    val repository = ReminderRepository(this.database.reminders())
    val planner = ReminderPlanner(clock)
    val parser get() = QuickAddParser(Clock.systemDefaultZone())
    val notifier = Notifier(appContext)
    val scheduler = AlarmScheduler(appContext, planner)
    val service = ReminderService(repository, planner, scheduler, notifier)
}

class RemApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
    }
}

val Context.container: AppContainer get() = (applicationContext as RemApplication).container
