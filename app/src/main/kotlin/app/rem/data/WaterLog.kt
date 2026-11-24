package app.rem.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.LocalDate

/** Glasses of water today. Resets itself at midnight; kept in preferences, not the database. */
class WaterLog(context: Context, private val clock: Clock = Clock.systemDefaultZone()) {
    private val prefs = context.getSharedPreferences("rem_water", Context.MODE_PRIVATE)
    private val _count = MutableStateFlow(read())
    val count: StateFlow<Int> get() = _count.also { it.value = read() }

    fun today(): Int = read().also { _count.value = it }

    fun add(delta: Int = 1): Int {
        val next = (read() + delta).coerceIn(0, 30)
        prefs.edit().putString("date", LocalDate.now(clock).toString()).putInt("count", next).apply()
        _count.value = next
        return next
    }

    private fun read(): Int =
        if (prefs.getString("date", null) == LocalDate.now(clock).toString()) prefs.getInt("count", 0) else 0
}
