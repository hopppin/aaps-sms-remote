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
                    HistoryCommand.CARBS, HistoryCommand.UNRECOGNIZED -> null
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
            HistoryOutcome.Unrecognized -> {
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
