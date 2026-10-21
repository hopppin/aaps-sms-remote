package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandCatalog
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.SessionTimeouts
import pl.twojanazwa.aapsremote.session.UnknownReason

object ResultMapper {
    /** CMDUI-19, CMDUI-20. Wyczerpujący when po SessionOutcome, FailureReason, UnknownReason. */
    fun map(result: SessionResult, timeouts: SessionTimeouts = SessionTimeouts()): ResultView {
        val command = result.command
        val outcome = result.outcome
        val replyMinutes = timeouts.reply.inWholeMinutes.toInt()
        val confirmationMinutes = timeouts.confirmation.inWholeMinutes.toInt()

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
                    is Command.Carbs -> ResultMessage.CARBS_CONFIRMED
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
                message = failureMessage(outcome.reason)
                text = outcome.text
            }
            is SessionOutcome.TimedOut -> {
                kind = ResultKind.NO_RESPONSE
                message = when (outcome.phase) {
                    SessionPhase.SENDING_COMMAND -> ResultMessage.SEND_TIMEOUT
                    SessionPhase.NOT_STARTED,
                    SessionPhase.AWAITING_REPLY,
                    SessionPhase.AWAITING_CODE_REQUEST,
                    SessionPhase.PREPARING_CODE,
                    SessionPhase.SENDING_CODE,
                    SessionPhase.AWAITING_CONFIRMATION,
                    SessionPhase.FINISHED,
                    -> ResultMessage.NO_RESPONSE
                }
                arg = replyMinutes
            }
            is SessionOutcome.Unknown -> {
                kind = ResultKind.UNKNOWN
                message = unknownMessage(outcome.reason)
                if (outcome.reason == UnknownReason.CONFIRMATION_TIMEOUT) arg = confirmationMinutes
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

    private fun failureMessage(reason: FailureReason): ResultMessage = when (reason) {
        FailureReason.SEND_FAILED -> ResultMessage.SEND_FAILED
        FailureReason.UNRECOGNIZED_REPLY -> ResultMessage.UNRECOGNIZED_REPLY
        FailureReason.AMOUNT_MISMATCH -> ResultMessage.AMOUNT_MISMATCH
        FailureReason.UNEXPECTED_REPLY -> ResultMessage.UNEXPECTED_REPLY
        FailureReason.WRONG_CODE -> ResultMessage.WRONG_CODE
        FailureReason.CODE_REQUEST_EXPIRED -> ResultMessage.CODE_REQUEST_EXPIRED
        FailureReason.CODE_PREPARATION_TIMEOUT -> ResultMessage.CODE_PREPARATION_TIMEOUT
        FailureReason.INTERNAL_ERROR -> ResultMessage.INTERNAL_ERROR
    }

    private fun unknownMessage(reason: UnknownReason): ResultMessage = when (reason) {
        UnknownReason.CONFIRMATION_TIMEOUT -> ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT
        UnknownReason.CANCELLED_AFTER_CODE -> ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE
        UnknownReason.CODE_SEND_FAILED -> ResultMessage.UNKNOWN_CODE_SEND_FAILED
        UnknownReason.UNRECOGNIZED_REPLY -> ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY
        UnknownReason.AMOUNT_MISMATCH -> ResultMessage.UNKNOWN_AMOUNT_MISMATCH
        UnknownReason.UNEXPECTED_REPLY -> ResultMessage.UNKNOWN_UNEXPECTED_REPLY
        UnknownReason.INTERNAL_ERROR -> ResultMessage.UNKNOWN_INTERNAL_ERROR
    }
}
