package pl.twojanazwa.aapsremote.ui.settings.limits

import pl.twojanazwa.aapsremote.commands.LimitField
import pl.twojanazwa.aapsremote.commands.LimitValueError

enum class LimitsLoad { LOADING, LOADED, LOAD_FAILED }

/** Jednorazowy komunikat sekcji limitów; tekst dobiera warstwa widoku. */
enum class LimitsMessage { SAVED, SAVE_FAILED, COMMAND_ACTIVE }

data class CommandLimitsUiState(
    val load: LimitsLoad = LimitsLoad.LOADING,
    val carbsMax: String = "",
    val bolusMax: String = "",
    val interval: String = "",
    val errors: Map<LimitField, LimitValueError> = emptyMap(),
    val busy: Boolean = false,
    val commandActive: Boolean = false,
    val message: LimitsMessage? = null,
) {
    val canSave: Boolean get() = !busy && !commandActive && load != LimitsLoad.LOADING

    /** LIM-12: bez tekstów pól (tylko długości). */
    override fun toString(): String =
        "CommandLimitsUiState(load=$load, lengths=${carbsMax.length}/${bolusMax.length}/${interval.length}, " +
            "errors=${errors.keys}, busy=$busy, commandActive=$commandActive, message=$message)"
}
