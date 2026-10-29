package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandCatalog
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.SessionTimeouts

object ResultMapper {
    /** CMDUI-19, CMDUI-20. Powody i fazy odwzorowuje ResultMessages.kt (wspólne z historią, HISTUI-04). */
    fun map(result: SessionResult, timeouts: SessionTimeouts = SessionTimeouts()): ResultView {
        val command = result.command
        val outcome = result.outcome

        val kind: ResultKind
        val message: ResultMessage
        var arg: Int? = null
        var text: String? = null

        when (outcome) {
            is SessionOutcome.Completed -> {
                kind = ResultKind.SUCCESS
                message = when (command) {
                    is Command.Bg -> ResultMessage.BG_REPLY
                    is Command.Pump -> ResultMessage.PUMP_REPLY
                    is Command.Carbs -> ResultMessage.DETAILS_MISSING
                    is Command.Bolus -> ResultMessage.DETAILS_MISSING
                }
                text = outcome.text
            }
            is SessionOutcome.CarbsConfirmed -> {
                kind = ResultKind.SUCCESS
                message = ResultMessage.CARBS_CONFIRMED
                arg = outcome.grams
                text = outcome.text
            }
            is SessionOutcome.Failed -> {
                kind = ResultKind.FAILURE
                message = outcome.reason.toResultMessage()
                text = outcome.text
            }
            is SessionOutcome.TimedOut -> {
                kind = ResultKind.NO_RESPONSE
                message = timedOutResultMessage(outcome.phase)
                arg = message.minutesArg(timeouts)
            }
            is SessionOutcome.Unknown -> {
                kind = ResultKind.UNKNOWN
                message = outcome.reason.toResultMessage()
                arg = message.minutesArg(timeouts)
                text = outcome.text
            }
            SessionOutcome.Cancelled -> {
                kind = ResultKind.CANCELLED
                message = ResultMessage.CANCELLED
            }
        }

        val codeNotSent = CommandCatalog.definitionOf(command.id).requiresCode &&
            (
                (outcome is SessionOutcome.Failed && outcome.reason != FailureReason.WRONG_CODE) ||
                    outcome is SessionOutcome.TimedOut ||
                    outcome is SessionOutcome.Cancelled
                )

        return ResultView(
            commandId = command.id,
            commandGrams = (command as? Command.Carbs)?.grams,
            kind = kind,
            message = message,
            messageArg = arg,
            replyText = text,
            codeNotSent = codeNotSent,
            unknownWarning = outcome is SessionOutcome.Unknown,
            unexpectedCount = result.unexpectedMessages.size,
        )
    }
}
