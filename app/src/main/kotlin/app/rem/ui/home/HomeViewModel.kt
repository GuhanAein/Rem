package app.rem.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rem.AppContainer
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface HomeEvent {
    data class Completed(val before: Reminder) : HomeEvent
    data class Snoozed(val title: String, val preset: SnoozePreset) : HomeEvent
    data class Saved(val title: String) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val smartList = MutableStateFlow(SmartList.TODAY)
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val needsSetup = MutableStateFlow(false)
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events

    /** Ticks every 30 s so "late" labels and sections stay current. */
    private val now = flow {
        while (true) {
            emit(LocalDateTime.now(container.clock))
            delay(30_000)
        }
    }

    private val completedToday = now.flatMapLatest { n ->
        container.repository.observeCompletedSince(n.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    val state: StateFlow<HomeUiState> = combine(
        container.repository.observeActive(),
        completedToday,
        now,
        combine(smartList, selectedCategory, needsSetup) { l, c, s -> Triple(l, c, s) },
    ) { active, done, n, (d, c, s) ->
        // Recurring reminders completed today stay active, so only count real completions.
        val completions = done.filter { it.completedAt != null }
        buildHomeState(active, completions, n, d, c, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectList(list: SmartList) {
        smartList.value = list
    }

    fun selectCategory(category: Category?) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
    }

    fun setNeedsSetup(value: Boolean) {
        needsSetup.value = value
    }

    fun complete(id: Long) = viewModelScope.launch {
        container.service.complete(id)?.let { _events.emit(HomeEvent.Completed(it)) }
    }

    fun undo(before: Reminder) = viewModelScope.launch { container.service.restore(before) }

    fun snooze(id: Long, preset: SnoozePreset) = viewModelScope.launch {
        val title = container.repository.get(id)?.title ?: return@launch
        container.service.snooze(id, preset)
        _events.emit(HomeEvent.Snoozed(title, preset))
    }

    fun save(reminder: Reminder) = viewModelScope.launch {
        container.service.save(reminder)
        _events.emit(HomeEvent.Saved(reminder.title))
    }
}
