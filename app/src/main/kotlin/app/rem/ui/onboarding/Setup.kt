package app.rem.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class SetupStatus(
    val notifications: Boolean = true,
    val exactAlarms: Boolean = true,
    val fullScreen: Boolean = true,
    val batteryUnrestricted: Boolean = true,
) {
    /** The two that actually stop reminders arriving on time. */
    val essentialsOk: Boolean get() = notifications && exactAlarms
}

fun readSetupStatus(context: Context): SetupStatus {
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreen = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    return SetupStatus(notifications, exact, fullScreen, battery)
}

private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

fun exactAlarmSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
    else appSettingsIntent(context)

fun fullScreenSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
    else appSettingsIntent(context)

@SuppressLint("BatteryLife")
fun batterySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

fun appSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

/** Phone makers that kill background apps, with the one-line fix users need. Bundled: no network. */
fun oemTip(manufacturer: String = Build.MANUFACTURER): String? = when (manufacturer.lowercase()) {
    "xiaomi", "redmi", "poco" -> "On Xiaomi: Settings › Apps › Rem › Autostart ON, and Battery saver › No restrictions."
    "samsung" -> "On Samsung: Settings › Battery › Background usage limits › add Rem to “Never sleeping apps”."
    "oneplus" -> "On OnePlus: Settings › Battery › Battery optimisation › Rem › Don't optimise."
    "oppo", "realme" -> "On Oppo/Realme: Settings › Battery › Rem › Allow background activity and Auto launch."
    "vivo", "iqoo" -> "On Vivo: Settings › Battery › Background power consumption › Rem › Allow."
    "huawei", "honor" -> "On Huawei/Honor: Settings › Battery › App launch › Rem › Manage manually, all ON."
    else -> null
}

private const val PREFS = "rem_prefs"
private const val KEY_ONBOARDED = "onboarded"

fun isOnboarded(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

fun setOnboarded(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
