package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.data.SaveLimitsResult

/** Atrapa magazynu limitów; domyślnie `Loaded` z limitami domyślnymi (zachowanie jak przed Spec 011). */
class FakeCommandLimitsStore(initial: CommandLimitsState = CommandLimitsState.Loaded(CommandLimits.DEFAULT)) :
    CommandLimitsStore {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<CommandLimitsState> = mutableState

    /** Gdy ustawiona, save czeka na jej ukończenie (zapis zawieszony, LIM-11). */
    var gate: CompletableDeferred<Unit>? = null

    fun setState(value: CommandLimitsState) {
        mutableState.value = value
    }

    fun setCarbsMax(grams: Int) {
        mutableState.value = CommandLimitsState.Loaded(limits(grams))
    }

    override suspend fun save(limits: CommandLimits): SaveLimitsResult {
        gate?.await()
        mutableState.value = CommandLimitsState.Loaded(limits)
        return SaveLimitsResult.Saved
    }

    companion object {
        fun limits(carbsMax: Int): CommandLimits =
            (CommandLimits.of(carbsMax, 500, 15) as LimitsValidation.Valid).limits
    }
}
