package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandCatalog
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.SessionTimeouts

private fun ResultMessage.forCommand(command: Command): ResultMessage =
    when (command) {
        is Command.Bolus -> forBolus()
        is Command.ProfileSwitch -> forProfile()
        else -> this
    }

object ResultMapper {
    /** CMDUI-19, CMDUI-20. Powody i fazy odwzorowuje ResultMessages.kt (wspólne z historią, HISTUI-04). */
    fun map(result: SessionResult, timeouts: SessionTimeouts = SessionTimeouts()): ResultView {
        val command = result.command
        val outcome = result.outcome

        var items: List<ProfileChoice>? = null
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
                    is Command.ProfileStatus -> ResultMessage.PROFILE_STATUS_REPLY
                    is Command.ProfileList -> ResultMessage.PROFILE_LIST_REPLY
                    is Command.ProfileSwitch -> ResultMessage.DETAILS_MISSING
                }
                text = outcome.text
                val reply = outcome.reply
                if (command is Command.ProfileList && reply is AapsReply.ProfileListReport) {
                    items = reply.items.map { ProfileChoice(it.number, it.name) }
                }
            }
            is SessionOutcome.CarbsConfirmed -> {
                kind = ResultKind.SUCCESS
                message = ResultMessage.CARBS_CONFIRMED
                arg = outcome.grams
                text = outcome.text
            }
            is SessionOutcome.BolusConfirmed -> {
                kind = ResultKind.SUCCESS
                message = ResultMessage.BOLUS_CONFIRMED
                arg = outcome.centiUnits
                text = outcome.text
            }
            is SessionOutcome.Failed -> {
                kind = ResultKind.FAILURE
                message = outcome.reason.toResultMessage().forCommand(command)
                text = outcome.text
            }
            is SessionOutcome.TimedOut -> {
                kind = ResultKind.NO_RESPONSE
                message = timedOutResultMessage(outcome.phase)
                arg = message.minutesArg(timeouts)
            }
            is SessionOutcome.Unknown -> {
                kind = ResultKind.UNKNOWN
                message = outcome.reason.toResultMessage().forCommand(command)
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
            commandCentiUnits = (command as? Command.Bolus)?.centiUnits,
            profileNumber = (command as? Command.ProfileSwitch)?.number,
            profilePercent = (command as? Command.ProfileSwitch)?.percent,
            profileItems = items,
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
