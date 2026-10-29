package pl.twojanazwa.aapsremote.ui.settings.limits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitField
import pl.twojanazwa.aapsremote.commands.LimitValueError
import pl.twojanazwa.aapsremote.commands.LimitsInput
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.data.SaveLimitsResult
import pl.twojanazwa.aapsremote.session.SessionCoordinator

/** LIM-10, LIM-11: sekcja „Limity poleceń”. */
@HiltViewModel
class CommandLimitsViewModel @Inject constructor(
    private val store: CommandLimitsStore,
    private val coordinator: SessionCoordinator,
) : ViewModel() {

    private data class Local(
        val filled: Boolean = false,
        val carbsMax: String = "",
        val bolusMax: String = "",
        val interval: String = "",
        val errors: Map<LimitField, LimitValueError> = emptyMap(),
        val busy: Boolean = false,
        val message: LimitsMessage? = null,
    ) {
        override fun toString(): String = "Local(filled=$filled, busy=$busy, message=$message)"
    }

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<CommandLimitsUiState> = combine(
        store.state,
        coordinator.activeSession.map { it != null },
        local,
    ) { state, commandActive, l ->
        CommandLimitsUiState(
            load = when (state) {
                CommandLimitsState.Loading -> LimitsLoad.LOADING
                is CommandLimitsState.Loaded -> LimitsLoad.LOADED
                CommandLimitsState.LoadFailed -> LimitsLoad.LOAD_FAILED
            },
            carbsMax = l.carbsMax,
            bolusMax = l.bolusMax,
            interval = l.interval,
            errors = l.errors,
            busy = l.busy,
            commandActive = commandActive,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CommandLimitsUiState())

    init {
        // Pola wypełniane raz, przy pierwszym stanie innym niż Loading; potem edycja użytkownika nie ginie.
        viewModelScope.launch {
            store.state.collect { state ->
                when (state) {
                    CommandLimitsState.Loading -> Unit
                    is CommandLimitsState.Loaded -> fillOnce(state.limits)
                    CommandLimitsState.LoadFailed -> fillOnce(CommandLimits.DEFAULT)
                }
            }
        }
    }

    private fun fillOnce(limits: CommandLimits) {
        local.update { if (it.filled) it else it.filledWith(limits) }
    }

    private fun Local.filledWith(limits: CommandLimits) = copy(
        filled = true,
        carbsMax = limits.carbsMaxGrams.toString(),
        bolusMax = formatCentiUnits(limits.bolusMaxCentiUnits),
        interval = limits.bolusMinIntervalMinutes.toString(),
        errors = emptyMap(),
    )

    fun onCarbsChanged(text: String) = edit { it.copy(carbsMax = text, errors = it.errors - LimitField.CARBS_MAX) }

    fun onBolusChanged(text: String) = edit { it.copy(bolusMax = text, errors = it.errors - LimitField.BOLUS_MAX) }

    fun onIntervalChanged(text: String) =
        edit { it.copy(interval = text, errors = it.errors - LimitField.BOLUS_INTERVAL) }

    private fun edit(change: (Local) -> Local) {
        local.update { if (it.busy) it else change(it) } // wynik zapisu dotyczy wysłanych tekstów
    }

    fun onSaveClicked() {
        val current = local.value
        if (current.busy || store.state.value == CommandLimitsState.Loading) return // LIM-11
        if (coordinator.activeSession.value != null) { // LIM-11: sprawdzenie w chwili akcji
            local.update { it.copy(message = LimitsMessage.COMMAND_ACTIVE) }
            return
        }
        when (val v = LimitsInput.parse(current.carbsMax, current.bolusMax, current.interval)) {
            is LimitsValidation.Invalid -> local.update { it.copy(errors = v.errors) }
            is LimitsValidation.Valid -> {
                local.update { it.copy(busy = true, errors = emptyMap()) }
                viewModelScope.launch {
                    val result = try {
                        store.save(v.limits)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SaveLimitsResult.Failed
                    }
                    local.update {
                        when (result) {
                            SaveLimitsResult.Saved ->
                                it.filledWith(v.limits).copy(busy = false, message = LimitsMessage.SAVED)
                            SaveLimitsResult.Failed -> it.copy(busy = false, message = LimitsMessage.SAVE_FAILED)
                        }
                    }
                }
            }
        }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }
}
