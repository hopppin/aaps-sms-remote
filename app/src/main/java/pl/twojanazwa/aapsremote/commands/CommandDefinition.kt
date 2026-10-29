package pl.twojanazwa.aapsremote.commands

/** Wpis katalogu. keyword: A-Z, słowa rozdzielone pojedynczą spacją. */
data class CommandDefinition(
    val id: CommandId,
    val keyword: String,
    val parameter: CommandParameter,
    val requiresCode: Boolean,
    val approval: Approval,
)
