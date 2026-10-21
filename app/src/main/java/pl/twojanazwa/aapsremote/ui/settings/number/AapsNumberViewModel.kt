package pl.twojanazwa.aapsremote.ui.settings.number

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.ClearNumberResult
import pl.twojanazwa.aapsremote.data.SaveNumberResult
import pl.twojanazwa.aapsremote.session.SessionCoordinator

@HiltViewModel
class AapsNumberViewModel @Inject constructor(
    private val store: AapsNumberSettingsStore,
    private val coordinator: SessionCoordinator,
) : ViewModel() {

    /** Pola lokalne: edycja, okno potwierdzenia, operacja w toku i komunikat. */
    private data class Local(
        val editing: Boolean = false,
        val input: String = "",
        val inputError: NumberInputError? = null,
        val busy: Boolean = false,
        val confirmingClear: Boolean = false,
        val message: NumberMessage? = null,
    ) {
        override fun toString(): String = "Local(editing=$editing, busy=$busy, message=$message)"
    }

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<AapsNumberUiState> = combine(
        store.state,
        coordinator.activeSession.map { it != null },
        local,
    ) { state, commandActive, l ->
        AapsNumberUiState(
            display = state.toDisplay(),
            editing = l.editing,
            input = l.input,
            inputError = l.inputError,
            busy = l.busy,
            commandActive = commandActive,
            confirmingClear = l.confirmingClear,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AapsNumberUiState())

    init {
        // UI-15: start polecenia zamyka okno usunięcia; edycja zostaje, trwająca operacja jest dokańczana.
        viewModelScope.launch {
            coordinator.activeSession.map { it != null }.distinctUntilChanged().collect { active ->
                if (active) local.update { it.copy(confirmingClear = false) }
            }
        }
    }

    private fun commandActive(): Boolean = coordinator.activeSession.value != null

    /** „Ustaw numer” i „Zmień”. */
    fun onEditClicked() {
        if (local.value.busy || commandActive()) return // UI-15, UI-16: jak canSet/canChange, liczone ze źródeł
        val initial = when (val state = store.state.value) {
            is AapsNumberState.Configured -> state.number.destination
            AapsNumberState.NotConfigured, AapsNumberState.LoadFailed -> ""
            AapsNumberState.Loading -> return
        }
        local.update { it.copy(editing = true, input = initial, inputError = null) }
    }

    fun onInputChanged(text: String) {
        if (local.value.busy) return // UI-05, UI-16: wynik zapisu dotyczy tekstu wysłanego do magazynu
        local.update { it.copy(input = text, inputError = null) }
    }

    fun onSaveClicked() {
        val current = local.value
        if (!current.editing || current.busy) return // UI-16
        if (commandActive()) { // UI-15: sprawdzenie w chwili akcji
            local.update { it.copy(message = NumberMessage.COMMAND_ACTIVE) }
            return
        }
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = try {
                store.save(current.input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveNumberResult.Failed
            }
            local.update {
                when (result) {
                    SaveNumberResult.Saved ->
                        it.copy(busy = false, editing = false, input = "", message = NumberMessage.SAVED)
                    SaveNumberResult.Invalid -> it.copy(busy = false, inputError = NumberInputError.INVALID)
                    SaveNumberResult.Failed -> it.copy(busy = false, message = NumberMessage.SAVE_FAILED)
                }
            }
        }
    }

    fun onEditCancelled() {
        if (local.value.busy) return // UI-16
        local.update { it.copy(editing = false, input = "", inputError = null) }
    }

    fun onClearClicked() {
        if (store.state.value !is AapsNumberState.Configured || local.value.busy) return
        if (commandActive()) {
            local.update { it.copy(message = NumberMessage.COMMAND_ACTIVE) }
            return
        }
        local.update { it.copy(confirmingClear = true) }
    }

    fun onClearConfirmed() {
        val current = local.value
        if (!current.confirmingClear) return
        if (current.busy) return // UI-16
        if (commandActive()) { // UI-15
            local.update { it.copy(confirmingClear = false, message = NumberMessage.COMMAND_ACTIVE) }
            return
        }
        local.update { it.copy(confirmingClear = false, busy = true) }
        viewModelScope.launch {
            val result = try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClearNumberResult.Failed
            }
            val message = when (result) {
                ClearNumberResult.Cleared -> NumberMessage.CLEARED
                ClearNumberResult.Failed -> NumberMessage.CLEAR_FAILED
            }
            local.update { it.copy(busy = false, message = message) }
        }
    }

    fun onClearDismissed() {
        local.update { it.copy(confirmingClear = false) }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    private fun AapsNumberState.toDisplay(): NumberDisplay = when (this) {
        AapsNumberState.Loading -> NumberDisplay.Loading
        AapsNumberState.NotConfigured -> NumberDisplay.NotConfigured
        is AapsNumberState.Configured -> NumberDisplay.Configured(number.destination)
        AapsNumberState.LoadFailed -> NumberDisplay.LoadFailed
    }
}
