package app.rem.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.rem.container
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import app.rem.core.model.Trigger
import app.rem.data.PlaceEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Event-based reminders ("when I plug in tonight", "when headphones connect", "when I leave home").
 * Each trigger uses the most reliable mechanism Android allows in the background:
 *  - Charging: WorkManager with a requires-charging constraint (survives Doze and reboots).
 *  - Bluetooth headphones: the ACL-connected broadcast (allowed for manifest receivers).
 *  - Wired headphones: only while Rem's process is alive (Android limits this broadcast).
 *  - Places: Play Services geofences.
 */
object Triggers {
    const val PLACE_ID = "placeId"
    private const val LAST_FIRED = "lastTriggered"

    suspend fun sync(context: Context) {
        val all = runCatching { context.container.repository.allActive() }.getOrDefault(emptyList())
        syncCharging(context, all.filter { it.trigger == Trigger.CHARGING_TONIGHT })
        syncGeofences(context, all.filter { it.trigger in GEO_TRIGGERS })
    }

    // ---------------------------------------------------------------- Charging tonight

    private fun syncCharging(context: Context, reminders: List<Reminder>) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (reminders.isEmpty()) {
            wm.cancelUniqueWork(CHARGING_WORK)
            return
        }
        val start = context.container.settings.value.chargingWindowStart
        val now = LocalDateTime.now()
        val delay = if (inChargingWindow(now.toLocalTime(), start)) Duration.ZERO else Duration.between(now, now.toLocalDate().atTime(start))
        val request = OneTimeWorkRequestBuilder<ChargingWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(CHARGING_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** The evening window runs from [start] until 4am. */
    fun inChargingWindow(t: LocalTime, start: LocalTime): Boolean = !t.isBefore(start) || t.isBefore(LocalTime.of(4, 0))

    class ChargingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = applicationContext.container
            val start = c.settings.value.chargingWindowStart
            if (inChargingWindow(LocalTime.now(), start)) {
                c.repository.allActive().filter { it.trigger == Trigger.CHARGING_TONIGHT }.forEach { fire(applicationContext, it, "You're charging. ${it.title}") }
            }
            // Re-arm for the next evening.
            sync(applicationContext)
            return Result.success()
        }
    }

    // ---------------------------------------------------------------- Headphones

    /** Bluetooth audio devices (headphones, earbuds, car kits). */
    class BluetoothReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val isAudio = runCatching { device?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }.getOrDefault(true)
            if (isAudio) fireHeadphones(context, goAsync())
        }
    }

    /** Wired headsets: registered at runtime, so best-effort while Rem is running. */
    class WiredReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_HEADSET_PLUG && intent.getIntExtra("state", 0) == 1) fireHeadphones(context, goAsync())
        }
    }

    private fun fireHeadphones(context: Context, pending: BroadcastReceiver.PendingResult) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.container.repository.allActive().filter { it.trigger == Trigger.HEADPHONES }
                    .forEach { fire(context, it, "Headphones connected. ${it.title}", cooldownMinutes = 30) }
            } finally {
                pending.finish()
            }
        }
    }

    // ---------------------------------------------------------------- Places

    private val GEO_TRIGGERS = setOf(Trigger.LEAVE_HOME, Trigger.ARRIVE_PLACE, Trigger.LEAVE_PLACE)

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    @SuppressLint("MissingPermission")
    private suspend fun syncGeofences(context: Context, reminders: List<Reminder>) {
        val client = runCatching { LocationServices.getGeofencingClient(context) }.getOrNull() ?: return
        runCatching { client.removeGeofences(geofenceIntent(context)) }
        if (reminders.isEmpty() || !hasLocationPermission(context)) return
        val places = context.container.database.places().all()
        val needed = reminders.mapNotNull { placeFor(it, places) }.distinctBy { it.id }
        if (needed.isEmpty()) return
        val fences = needed.map { p ->
            Geofence.Builder()
                .setRequestId(p.id.toString())
                .setCircularRegion(p.latitude, p.longitude, p.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        runCatching { client.addGeofences(request, geofenceIntent(context)) }
    }

    fun placeFor(r: Reminder, places: List<PlaceEntity>): PlaceEntity? = when (r.trigger) {
        Trigger.LEAVE_HOME -> places.firstOrNull { it.isHome }
        Trigger.ARRIVE_PLACE, Trigger.LEAVE_PLACE -> r.extras[PLACE_ID]?.toLongOrNull()?.let { id -> places.firstOrNull { it.id == id } }
        else -> null
    }

    private fun geofenceIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7001, Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    class GeofenceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = GeofencingEvent.fromIntent(intent) ?: return
            if (event.hasError()) return
            val entering = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
            val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }.toSet()
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val c = context.container
                    val places = c.database.places().all()
                    c.repository.allActive().forEach { r ->
                        val place = placeFor(r, places) ?: return@forEach
                        if (place.id !in ids) return@forEach
                        val matches = when (r.trigger) {
                            Trigger.ARRIVE_PLACE -> entering
                            Trigger.LEAVE_PLACE, Trigger.LEAVE_HOME -> !entering
                            else -> false
                        }
                        if (matches) fire(context, r, (if (entering) "You're at ${place.name}. " else "Leaving ${place.name}. ") + r.title, cooldownMinutes = 20)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    // ---------------------------------------------------------------- Firing

    /** Shows the reminder now (respecting a cooldown so a flapping signal doesn't spam). */
    suspend fun fire(context: Context, r: Reminder, text: String, cooldownMinutes: Long = 6 * 60) {
        if (r.status != Status.ACTIVE) return
        val listText = r.extras[app.rem.data.SpacesService.LIST_ID]?.toLongOrNull()?.let { context.container.spaces.listText(it) ?: return }
        val now = LocalDateTime.now()
        val last = r.extras[LAST_FIRED]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (last != null && Duration.between(last, now).toMinutes() < cooldownMinutes) return
        val c = context.container
        c.repository.upsert(r.copy(extras = r.extras + (LAST_FIRED to now.toString())))
        c.notifier.show(r, isNag = false, ring = true, textOverride = listText?.let { "You're at the shop.\n" + it.substringAfter('\n') } ?: text)
    }

    fun firedToday(r: Reminder, today: LocalDate = LocalDate.now()): Boolean =
        r.extras[LAST_FIRED]?.let { runCatching { LocalDateTime.parse(it).toLocalDate() == today }.getOrNull() } == true

    private const val CHARGING_WORK = "rem-charging-tonight"
}
