package app.rem.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rem.AppContainer
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.recurrence.Freq
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.WeekdayNum
import app.rem.ui.add.buildQuickReminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class RepeatForm(
    val freq: Freq? = null,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val lastDayOfMonth: Boolean = false,
) {
    fun toRule(anchor: LocalDate?): RecurrenceRule? {
        val f = freq ?: return null
        return RecurrenceRule(
            freq = f,
            interval = interval.coerceAtLeast(1),
            byDay = if (f == Freq.WEEKLY) weekdays.ifEmpty { setOfNotNull(anchor?.dayOfWeek) }.sorted().map { WeekdayNum(it) } else emptyList(),
            byMonthDay = if (f == Freq.MONTHLY && lastDayOfMonth) listOf(-1) else emptyList(),
        )
    }

    companion object {
        fun from(rrule: String?): RepeatForm {
            val rule = RecurrenceRule.parseOrNull(rrule) ?: return RepeatForm()
            return RepeatForm(
                freq = rule.freq,
                interval = rule.interval,
                weekdays = rule.byDay.filter { it.ordinal == null }.map { it.day }.toSet(),
                lastDayOfMonth = rule.byMonthDay == listOf(-1),
            )
        }
    }
}

data class EditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val category: Category = Category.TASK,
    val priority: Priority = Priority.NONE,
    val date: LocalDate? = null,
    val time: LocalTime = LocalTime.of(9, 0),
    val repeat: RepeatForm = RepeatForm(),
    val alertOffsets: Set<Int> = setOf(0),
    val style: AlertStyle = AlertStyle.NOTIFICATION,
    val nagMinutes: Int? = null,
    val amount: String = "",
    val original: Reminder? = null,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

class EditViewModel(private val container: AppContainer, private val id: Long, private val prefill: String?) : ViewModel() {
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            val existing = if (id > 0) container.repository.get(id) else null
            _state.value = when {
                existing != null -> fromReminder(existing).copy(isNew = false)
                !prefill.isNullOrBlank() -> {
                    val parsed = container.parser.parse(prefill)
                    val r = buildQuickReminder(parsed, parsed.category, parsed.dueAt, parsed.category.defaultStyle == AlertStyle.ALARM, container.clock.instant())
                    fromReminder(r)
                }
                else -> EditUiState(loaded = true, date = LocalDate.now(container.clock))
            }
        }
    }

    private fun fromReminder(r: Reminder) = EditUiState(
        loaded = true,
        title = r.title,
        notes = r.notes,
        category = r.category,
        priority = r.priority,
        date = r.dueAt?.toLocalDate(),
        time = r.dueAt?.toLocalTime() ?: LocalTime.of(9, 0),
        repeat = RepeatForm.from(r.recurrence),
        alertOffsets = r.alertOffsets.toSet(),
        style = r.alertStyle,
        nagMinutes = r.nagMinutes,
        amount = r.amount.orEmpty(),
        original = r.takeIf { it.id != 0L },
    )

    fun update(transform: (EditUiState) -> EditUiState) = _state.update(transform)

    /** Picking a category applies its smart defaults (alarm style, alerts, repeat). */
    fun selectCategory(category: Category) = _state.update { s ->
        s.copy(
            category = category,
            style = category.defaultStyle,
            nagMinutes = category.defaultNagMinutes,
            alertOffsets = (category.defaultAlertOffsets + 0).toSet(),
            repeat = if (s.repeat.freq == null && category.defaultRecurrence != null) RepeatForm.from(category.defaultRecurrence) else s.repeat,
        )
    }

    fun buildReminder(): Reminder {
        val s = _state.value
        val due: LocalDateTime? = s.date?.atTime(s.time)
        val base = s.original ?: Reminder(title = s.title, createdAt = container.clock.instant())
        return base.copy(
            title = s.title.trim(),
            notes = s.notes.trim(),
            category = s.category,
            priority = s.priority,
            dueAt = due,
            recurrence = s.repeat.toRule(s.date)?.toRRule(),
            alertStyle = s.style,
            alertOffsets = s.alertOffsets.ifEmpty { setOf(0) }.toList(),
            nagMinutes = s.nagMinutes,
            amount = s.amount.trim().ifEmpty { null },
            snoozedUntil = null,
        )
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (!_state.value.canSave) return@launch
        container.service.save(buildReminder())
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (id > 0) container.service.delete(id)
        onDone()
    }
}
