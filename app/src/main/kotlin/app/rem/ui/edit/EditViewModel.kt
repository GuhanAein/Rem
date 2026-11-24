package app.rem.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rem.AppContainer
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.model.Kind
import app.rem.core.model.RandomTimes
import app.rem.core.model.TimeWindow
import app.rem.core.model.Trigger
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
    /** Several times a day (hours), e.g. [8, 14, 20]. */
    val hours: List<Int> = emptyList(),
    /** Stop repeating after this date. */
    val until: LocalDate? = null,
) {
    fun toRule(anchor: LocalDate?): RecurrenceRule? {
        val f = freq ?: return null
        return RecurrenceRule(
            freq = f,
            interval = interval.coerceAtLeast(1),
            byDay = if (f == Freq.WEEKLY) weekdays.ifEmpty { setOfNotNull(anchor?.dayOfWeek) }.sorted().map { WeekdayNum(it) } else emptyList(),
            byMonthDay = if (f == Freq.MONTHLY && lastDayOfMonth) listOf(-1) else emptyList(),
            byHour = hours.sorted(),
            until = until?.atTime(23, 59, 59),
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
                hours = rule.byHour,
                until = rule.until?.toLocalDate(),
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
    val kind: Kind = Kind.STANDARD,
    val window: TimeWindow? = null,
    val randomTimes: RandomTimes? = null,
    val trigger: Trigger? = null,
    val followUp: String = "",
    val firstStep: String = "",
    val isPrivate: Boolean = false,
    val extras: Map<String, String> = emptyMap(),
    val space: app.rem.core.model.Space = app.rem.core.model.Space.PERSONAL,
) {
    /** "Deadline mode": gentle notes a week and 3 days before, a firm alarm on the day. */
    val escalating: Boolean get() = alertOffsets.containsAll(DEADLINE_OFFSETS) && style == AlertStyle.ALARM

    companion object {
        val DEADLINE_OFFSETS = setOf(7 * 1440, 3 * 1440, 1440, 0)
    }
    val canSave: Boolean get() = title.isNotBlank()
}

class EditViewModel(private val container: AppContainer, private val id: Long, private val prefill: String?, private val kind: Kind? = null) : ViewModel() {
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
            if (existing == null && kind != null) setKind(kind)
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
        kind = r.kind,
        window = r.window,
        randomTimes = r.randomTimes,
        trigger = r.trigger,
        followUp = r.followUp.orEmpty(),
        firstStep = r.firstStep.orEmpty(),
        isPrivate = r.isPrivate,
        extras = r.extras,
        space = r.space,
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
            kind = s.kind,
            window = s.window,
            randomTimes = s.randomTimes,
            trigger = s.trigger,
            followUp = s.followUp.trim().ifEmpty { null },
            firstStep = s.firstStep.trim().ifEmpty { null },
            isPrivate = s.isPrivate,
            extras = s.extras,
            space = s.space,
            workStatus = if (s.space == app.rem.core.model.Space.WORK) (s.original?.workStatus ?: "TODO") else null,
        ).let { r ->
            // Random-time reminders start at their next random slot.
            val rt = s.randomTimes
            if (rt != null) r.copy(
                recurrence = r.recurrence ?: "FREQ=DAILY",
                dueAt = app.rem.core.scheduling.ReminderPlanner.nextRandomSlot(if (r.id == 0L) 1L else r.id, rt, LocalDateTime.now(container.clock).minusMinutes(1)),
            ) else r
        }
    }

    /** Set after saving if the chosen time is crowded: the screen offers to spread them out. */
    private val _crowd = MutableStateFlow<List<Long>>(emptyList())
    val crowd: StateFlow<List<Long>> = _crowd

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (!_state.value.canSave) return@launch
        val reminder = buildReminder()
        val id = container.service.save(reminder)
        val due = reminder.dueAt
        val crowd = if (due != null) app.rem.core.insights.ConflictDetector.crowdAt(container.repository.allActive(), due) else emptyList()
        if (crowd.size >= 4) _crowd.value = crowd.map { it.id }.ifEmpty { listOf(id) } else onDone()
    }

    fun spreadOut(onDone: () -> Unit) = viewModelScope.launch {
        container.service.spread(_crowd.value)
        _crowd.value = emptyList()
        onDone()
    }

    fun keepAsIs(onDone: () -> Unit) {
        _crowd.value = emptyList()
        onDone()
    }

    /** "Remind them": a calendar invite with an alert, shareable to anyone. */
    fun inviteFile(): java.io.File = app.rem.backup.Backup.remindThemIcs(container.appContext, buildReminder())

    fun setKind(kind: Kind) = _state.update { s ->
        when (kind) {
            Kind.SUBSCRIPTION -> s.copy(kind = kind, category = Category.BILL, repeat = if (s.repeat.freq == null) RepeatForm(Freq.MONTHLY) else s.repeat, alertOffsets = setOf(3 * 1440, 0))
            Kind.WARRANTY -> s.copy(kind = kind, category = Category.DOCUMENT, alertOffsets = setOf(30 * 1440, 7 * 1440))
            Kind.CAR_SERVICE -> s.copy(kind = kind, category = Category.VEHICLE, alertOffsets = setOf(7 * 1440, 1440))
            Kind.PLANT -> s.copy(kind = kind, category = Category.HOME, repeat = if (s.repeat.freq == null) RepeatForm(Freq.DAILY, interval = 3) else s.repeat)
            Kind.LEND, Kind.BORROW -> s.copy(kind = kind, date = s.date ?: LocalDate.now(container.clock).plusWeeks(2))
            Kind.RETURN -> s.copy(kind = kind, category = Category.SHOPPING, alertOffsets = setOf(2 * 1440, 0))
            Kind.CHORE -> s.copy(kind = kind, category = Category.HOME, repeat = if (s.repeat.freq == null) RepeatForm(Freq.WEEKLY, weekdays = setOf(DayOfWeek.SATURDAY)) else s.repeat)
            else -> s.copy(kind = kind)
        }
    }

    fun setExtra(key: String, value: String?) = _state.update { s ->
        s.copy(extras = if (value.isNullOrBlank()) s.extras - key else s.extras + (key to value))
    }

    fun setEscalating(on: Boolean) = _state.update { s ->
        if (on) s.copy(alertOffsets = EditUiState.DEADLINE_OFFSETS, style = AlertStyle.ALARM, nagMinutes = s.nagMinutes ?: 10, date = s.date ?: LocalDate.now(container.clock).plusWeeks(1))
        else s.copy(alertOffsets = setOf(0), style = AlertStyle.NOTIFICATION, nagMinutes = null)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (id > 0) container.service.delete(id)
        onDone()
    }
}
