package pl.twojanazwa.aapsremote.commands

/** Wpis katalogu. keyword: tylko A-Z. */
data class CommandDefinition(
    val id: CommandId,
    val keyword: String,
    val parameter: CommandParameter,
    val requiresCode: Boolean,
    val approval: Approval,
)
