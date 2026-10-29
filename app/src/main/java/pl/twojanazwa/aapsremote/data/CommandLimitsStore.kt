package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.StateFlow
import pl.twojanazwa.aapsremote.commands.CommandLimits

interface CommandLimitsStore {
    val state: StateFlow<CommandLimitsState>
    suspend fun save(limits: CommandLimits): SaveLimitsResult
}
