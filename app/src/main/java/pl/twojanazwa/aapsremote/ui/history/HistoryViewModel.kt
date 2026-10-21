package pl.twojanazwa.aapsremote.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.data.CommandHistory
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import javax.inject.Inject

/** HISTUI-01, HISTUI-02, HISTUI-07 – HISTUI-09, HISTUI-12. Czyszczenie historii – osobne zadanie. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: CommandHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    /** Ostatnia lista i wybrany wpis – tylko w pamięci (HISTUI-07). */
    private var entries: List<HistoryEntry> = emptyList()
    private var selectedId: Long? = null

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            retryTrigger
                .flatMapLatest {
                    flow {
                        emit(null)
                        emitAll(history.entries)
                    }
                }
                .collect { snapshot -> onSnapshot(snapshot) }
        }
    }

    private fun onSnapshot(snapshot: HistorySnapshot?) {
        when (snapshot) {
            null -> {
                entries = emptyList()
                selectedId = null
                _uiState.update { it.copy(content = HistoryContent.Loading, details = null) }
            }
            HistorySnapshot.Unavailable -> {
                entries = emptyList()
                selectedId = null
                _uiState.update {
                    it.copy(content = HistoryContent.Unavailable, details = null, confirmingClearCount = null)
                }
            }
            is HistorySnapshot.Entries -> {
                entries = snapshot.entries
                if (selectedId != null && entries.none { it.id == selectedId }) selectedId = null
                if (entries.isEmpty()) {
                    _uiState.update {
                        it.copy(content = HistoryContent.Empty, details = null, confirmingClearCount = null)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            content = HistoryContent.Entries(entries.map { e -> HistoryEntryMapper.item(e) }),
                            details = selectedDetails(),
                        )
                    }
                }
            }
        }
    }

    private fun selectedDetails(): HistoryDetails? =
        entries.firstOrNull { it.id == selectedId }?.let { HistoryEntryMapper.details(it) }

    /** HISTUI-09: tylko w błędzie odczytu. */
    fun onRetry() {
        if (_uiState.value.content != HistoryContent.Unavailable) return
        retryTrigger.update { it + 1 }
    }

    /** HISTUI-07: tylko na liście; nieznany identyfikator nic nie otwiera. */
    fun onEntryClicked(id: Long) {
        if (_uiState.value.content !is HistoryContent.Entries) return
        if (entries.none { it.id == id }) return
        selectedId = id
        _uiState.update { it.copy(details = selectedDetails()) }
    }

    fun onDetailsClosed() {
        selectedId = null
        _uiState.update { it.copy(details = null) }
    }

    /** HISTUI-12 (część: szczegóły). */
    fun onBack() {
        if (_uiState.value.details != null) onDetailsClosed()
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
