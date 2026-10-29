package pl.twojanazwa.aapsremote.data

import pl.twojanazwa.aapsremote.commands.CommandLimits

/** LIM-04. toString() nie ujawnia wartości (CommandLimits.toString() = "CommandLimits(***)"). */
sealed interface CommandLimitsState {
    data object Loading : CommandLimitsState
    data class Loaded(val limits: CommandLimits) : CommandLimitsState
    data object LoadFailed : CommandLimitsState
}

enum class SaveLimitsResult { Saved, Failed }
