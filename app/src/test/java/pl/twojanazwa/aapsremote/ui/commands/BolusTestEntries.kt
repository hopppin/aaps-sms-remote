package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome

fun bolus(centiUnits: Int): Command.Bolus =
    (Command.Bolus.of(centiUnits, CommandLimits.DEFAULT) as BolusValidation.Valid).command

/** Wpis historii do testów odstępu (BOL-12); dane fikcyjne. */
fun bolusEntry(
    outcome: HistoryOutcome,
    command: HistoryCommand = HistoryCommand.BOLUS,
    codeSentAt: Long? = null,
    finishedAt: Long? = null,
    startedAt: Long? = null,
) = HistoryEntry(
    id = 1L,
    command = command,
    carbsGrams = null,
    outcome = outcome,
    startedAtEpochMillis = startedAt,
    commandSentAtEpochMillis = null,
    codeSentAtEpochMillis = codeSentAt,
    finishedAtEpochMillis = finishedAt,
    replyText = null,
    unexpectedMessageCount = 0,
    bolusCentiUnits = null,
)
