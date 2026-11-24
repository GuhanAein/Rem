package app.rem.data

import android.content.Context
import android.content.SharedPreferences
import app.rem.core.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalTime

enum class Accent(val label: String, val argb: Long, val deep: Long) {
    GOLD("Gold", 0xFFD9BC83, 0xFFB08D4F),
    SILVER("Silver", 0xFFC9CCD3, 0xFF8E939C),
    ROSE("Rose gold", 0xFFE7B3A3, 0xFFB9806F),
    EMERALD("Emerald", 0xFF8FCDB0, 0xFF5E9C80),
}

enum class ThemeMode(val label: String) { SYSTEM("Match phone"), DARK("Dark"), LIGHT("Light") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** A note at bedtime listing what's left from today, with "Move to tomorrow". */
    val bedtimeSummary: Boolean = true,
    val waterEnabled: Boolean = false,
    val waterGlasses: Int = 8,
    /** Offer a reminder when a bill arrives by SMS. */
    val smsBills: Boolean = false,
    val accent: Accent = Accent.GOLD,
    val bedtime: LocalTime = LocalTime.of(22, 30),
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val morningCardEnabled: Boolean = true,
    val morningCardTime: LocalTime = LocalTime.of(8, 0),
    val focusMode: Boolean = false,
    val appLock: Boolean = false,
    val panicUntil: Instant? = null,
    val nightlyBackup: Boolean = true,
    val respectCalls: Boolean = true,
    val respectMeetings: Boolean = false,
    val chargingWindowStart: LocalTime = LocalTime.of(19, 0),
    val accountabilityName: String = "",
    val accountabilityPhone: String = "",
    /** Custom ringtone URI per category (alarm-style reminders). */
    val sounds: Map<Category, String> = emptyMap(),
    val workHours: app.rem.core.spaces.WorkHours = app.rem.core.spaces.WorkHours.DEFAULT,
    val respectWorkHours: Boolean = true,
    /** Day of month you get paid, for wishlist budgeting. */
    val payday: Int? = null,
    val saleDates: List<java.time.LocalDate> = emptyList(),
) {
    fun panicActive(now: Instant): Boolean = panicUntil?.isAfter(now) == true
}

/** Small key-value settings, observable from Compose. */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rem_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): AppSettings = AppSettings(
        theme = prefs.getString("theme", null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        bedtimeSummary = prefs.getBoolean("bedtimeSummary", true),
        waterEnabled = prefs.getBoolean("water", false),
        waterGlasses = prefs.getInt("waterGlasses", 8),
        smsBills = prefs.getBoolean("smsBills", false),
        accent = prefs.getString("accent", null)?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.GOLD,
        bedtime = prefs.time("bedtime", LocalTime.of(22, 30)),
        wakeTime = prefs.time("wakeTime", LocalTime.of(7, 0)),
        morningCardEnabled = prefs.getBoolean("morningCard", true),
        morningCardTime = prefs.time("morningCardTime", LocalTime.of(8, 0)),
        focusMode = prefs.getBoolean("focusMode", false),
        appLock = prefs.getBoolean("appLock", false),
        panicUntil = prefs.getLong("panicUntil", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        nightlyBackup = prefs.getBoolean("nightlyBackup", true),
        respectCalls = prefs.getBoolean("respectCalls", true),
        respectMeetings = prefs.getBoolean("respectMeetings", false),
        chargingWindowStart = prefs.time("chargingStart", LocalTime.of(19, 0)),
        accountabilityName = prefs.getString("buddyName", "").orEmpty(),
        accountabilityPhone = prefs.getString("buddyPhone", "").orEmpty(),
        sounds = Category.entries.mapNotNull { c -> prefs.getString("sound_${c.name}", null)?.let { c to it } }.toMap(),
        workHours = app.rem.core.spaces.WorkHours.decode(prefs.getString("workHours", null)) ?: app.rem.core.spaces.WorkHours.DEFAULT,
        respectWorkHours = prefs.getBoolean("respectWorkHours", true),
        payday = prefs.getInt("payday", 0).takeIf { it in 1..31 },
        saleDates = prefs.getString("saleDates", "").orEmpty().split(',').mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
    )

    private fun write(s: AppSettings) {
        prefs.edit().apply {
            putString("theme", s.theme.name)
            putBoolean("bedtimeSummary", s.bedtimeSummary)
            putBoolean("water", s.waterEnabled)
            putInt("waterGlasses", s.waterGlasses)
            putBoolean("smsBills", s.smsBills)
            putString("accent", s.accent.name)
            putString("bedtime", s.bedtime.toString())
            putString("wakeTime", s.wakeTime.toString())
            putBoolean("morningCard", s.morningCardEnabled)
            putString("morningCardTime", s.morningCardTime.toString())
            putBoolean("focusMode", s.focusMode)
            putBoolean("appLock", s.appLock)
            putLong("panicUntil", s.panicUntil?.toEpochMilli() ?: 0L)
            putBoolean("nightlyBackup", s.nightlyBackup)
            putBoolean("respectCalls", s.respectCalls)
            putBoolean("respectMeetings", s.respectMeetings)
            putString("chargingStart", s.chargingWindowStart.toString())
            putString("buddyName", s.accountabilityName)
            putString("buddyPhone", s.accountabilityPhone)
            Category.entries.forEach { c -> s.sounds[c]?.let { putString("sound_${c.name}", it) } ?: remove("sound_${c.name}") }
            putString("workHours", s.workHours.encode())
            putBoolean("respectWorkHours", s.respectWorkHours)
            putInt("payday", s.payday ?: 0)
            putString("saleDates", s.saleDates.joinToString(","))
        }.apply()
    }

    private fun SharedPreferences.time(key: String, default: LocalTime): LocalTime =
        getString(key, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default
}
