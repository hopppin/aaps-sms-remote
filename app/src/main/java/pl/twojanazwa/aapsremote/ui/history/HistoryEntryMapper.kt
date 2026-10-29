package pl.twojanazwa.aapsremote.ui.history

import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionTimeouts
import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage
import pl.twojanazwa.aapsremote.ui.commands.minutesArg
import pl.twojanazwa.aapsremote.ui.commands.timedOutResultMessage
import pl.twojanazwa.aapsremote.ui.commands.toResultMessage

object HistoryEntryMapper {
    /** HISTUI-03, HISTUI-05. */
    fun item(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryItem = HistoryItem(
        id = entry.id,
        command = commandLabel(entry),
        grams = carbsGrams(entry),
        result = result(entry, timeouts),
        listTimeEpochMillis = entry.startedAtEpochMillis ?: entry.finishedAtEpochMillis,
    )

    /** HISTUI-05, HISTUI-06. */
    fun details(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryDetails = HistoryDetails(
        id = entry.id,
        command = commandLabel(entry),
        grams = carbsGrams(entry),
        result = result(entry, timeouts),
        times = HistoryTimeLabel.entries.map { label ->
            label to when (label) {
                HistoryTimeLabel.STARTED -> entry.startedAtEpochMillis
                HistoryTimeLabel.COMMAND_SENT -> entry.commandSentAtEpochMillis
                HistoryTimeLabel.CODE_SENT -> entry.codeSentAtEpochMillis
                HistoryTimeLabel.FINISHED -> entry.finishedAtEpochMillis
            }
        },
        replyText = entry.replyText,
        unexpectedCount = entry.unexpectedMessageCount.takeIf { it > 0 },
    )

    private fun commandLabel(entry: HistoryEntry): HistoryCommandLabel = when (entry.command) {
        HistoryCommand.BG -> HistoryCommandLabel.BG
        HistoryCommand.PUMP -> HistoryCommandLabel.PUMP
        HistoryCommand.CARBS ->
            if (entry.carbsGrams != null) HistoryCommandLabel.CARBS else HistoryCommandLabel.CARBS_NO_AMOUNT
        // Etykieta BOLUS w historii: zad. 5 (spec 012); do tego czasu jak nierozpoznane.
        HistoryCommand.BOLUS, HistoryCommand.UNRECOGNIZED -> HistoryCommandLabel.UNRECOGNIZED
    }

    private fun carbsGrams(entry: HistoryEntry): Int? =
        if (entry.command == HistoryCommand.CARBS) entry.carbsGrams else null

    /** HISTUI-04. Wyczerpujący when po HistoryOutcome (bez else); null i Unrecognized bez wyjątku (DATA-09). */
    fun result(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryResultView {
        val outcome = entry.outcome
        val kind: ResultKind?
        val message: ResultMessage?
        when (outcome) {
            HistoryOutcome.Completed -> {
                kind = ResultKind.SUCCESS
                message = when (entry.command) {
                    HistoryCommand.BG -> ResultMessage.BG_REPLY
                    HistoryCommand.PUMP -> ResultMessage.PUMP_REPLY
                    HistoryCommand.CARBS, HistoryCommand.BOLUS, HistoryCommand.UNRECOGNIZED -> null
                }
            }
            HistoryOutcome.CarbsConfirmed -> {
                kind = ResultKind.SUCCESS
                message = if (entry.command == HistoryCommand.CARBS && entry.carbsGrams != null) {
                    ResultMessage.CARBS_CONFIRMED
                } else {
                    null
                }
            }
            is HistoryOutcome.Failed -> {
                kind = ResultKind.FAILURE
                message = outcome.reason?.toResultMessage()
            }
            is HistoryOutcome.TimedOut -> {
                kind = ResultKind.NO_RESPONSE
                message = timedOutResultMessage(outcome.phase)
            }
            is HistoryOutcome.Unknown -> {
                kind = ResultKind.UNKNOWN
                message = outcome.reason?.toResultMessage()
            }
            HistoryOutcome.Cancelled -> {
                kind = ResultKind.CANCELLED
                message = ResultMessage.CANCELLED
            }
            // BolusConfirmed: wynik w UI w zad. 5 (spec 012); do tego czasu jak nierozpoznany.
            HistoryOutcome.BolusConfirmed, HistoryOutcome.Unrecognized -> {
                kind = null
                message = null
            }
        }
        val arg = when (message) {
            null -> null
            ResultMessage.CARBS_CONFIRMED -> entry.carbsGrams
            else -> message.minutesArg(timeouts)
        }
        val codeNotSent = entry.command == HistoryCommand.CARBS &&
            (
                (outcome is HistoryOutcome.Failed && outcome.reason != null && outcome.reason != FailureReason.WRONG_CODE) ||
                    outcome is HistoryOutcome.TimedOut ||
                    outcome is HistoryOutcome.Cancelled
                )
        return HistoryResultView(
            kind = kind,
            message = message,
            messageArg = arg,
            codeNotSent = codeNotSent,
            unknownWarning = outcome is HistoryOutcome.Unknown,
        )
    }
}
