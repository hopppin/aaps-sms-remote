package pl.twojanazwa.aapsremote.commands

import pl.twojanazwa.aapsremote.parser.ExpectedCommand

/** CMD-01: wpis katalogu dla polecenia. */
val Command.definition: CommandDefinition
    get() = CommandCatalog.definitionOf(id)

/** CMD-07: "BG", "PUMP", "CARBS <g>". */
fun Command.smsText(): String = when (this) {
    Command.Bg -> definition.keyword
    Command.Pump -> definition.keyword
    is Command.Carbs -> "${definition.keyword} $grams"
}

/** CMD-08: kontekst parsera wyliczany z tego samego polecenia co treść SMS. */
fun Command.toExpectedCommand(): ExpectedCommand = when (this) {
    Command.Bg -> ExpectedCommand.Bg
    Command.Pump -> ExpectedCommand.Pump
    is Command.Carbs -> ExpectedCommand.Carbs(grams)
}
