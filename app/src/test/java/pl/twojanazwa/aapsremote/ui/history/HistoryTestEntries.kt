package pl.twojanazwa.aapsremote.ui.history

import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome

const val T0 = 1111111051000L

/** Fabryka wpisów historii do testów (dane fikcyjne). */
fun historyEntry(
    outcome: HistoryOutcome,
    command: HistoryCommand = HistoryCommand.CARBS,
    carbsGrams: Int? = if (command == HistoryCommand.CARBS) 20 else null,
    id: Long = 1L,
    startedAt: Long? = T0,
    commandSentAt: Long? = T0 + 1_000,
    codeSentAt: Long? = null,
    finishedAt: Long? = T0 + 2_000,
    replyText: String? = null,
    unexpected: Int = 0,
) = HistoryEntry(
    id = id,
    command = command,
    carbsGrams = carbsGrams,
    outcome = outcome,
    startedAtEpochMillis = startedAt,
    commandSentAtEpochMillis = commandSentAt,
    codeSentAtEpochMillis = codeSentAt,
    finishedAtEpochMillis = finishedAt,
    replyText = replyText,
    unexpectedMessageCount = unexpected,
    bolusCentiUnits = null,
)
