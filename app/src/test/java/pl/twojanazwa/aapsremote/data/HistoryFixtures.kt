package pl.twojanazwa.aapsremote.data

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.parser.AapsReplyParser
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.parser.ExpectedCommand
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.IncomingSms
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.UnknownReason

/** Wspolne wyniki sesji z kryteriow DATA-07 (dane fikcyjne). */
object HistoryFixtures {
    const val T0 = 1111111051000L

    val carbs20: Command.Carbs = (Command.Carbs.of(20, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    val bolus: Command.Bolus = (Command.Bolus.of(500, CommandLimits.DEFAULT) as BolusValidation.Valid).command

    fun sms(body: String) = IncomingSms(body, T0 + 2000)

    fun bgCompleted(): SessionResult = SessionResult(
        command = Command.Bg,
        startedAtEpochMillis = T0,
        commandSentAtEpochMillis = T0 + 1000,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = T0 + 5000,
        outcome = SessionOutcome.Completed(
            AapsReplyParser.parse(AapsSamples.bg, ExpectedCommand.Bg),
            AapsSamples.bg,
        ),
        unexpectedMessages = emptyList(),
    )

    fun carbsConfirmed(unexpected: List<IncomingSms> = listOf(sms("a"), sms("b"))): SessionResult = SessionResult(
        command = carbs20,
        startedAtEpochMillis = T0,
        commandSentAtEpochMillis = T0 + 1000,
        codeSentAtEpochMillis = T0 + 61000,
        finishedAtEpochMillis = T0 + 120000,
        outcome = SessionOutcome.CarbsConfirmed(20, AapsSamples.carbsDone),
        unexpectedMessages = unexpected,
    )

    fun withOutcome(outcome: SessionOutcome): SessionResult = SessionResult(
        command = Command.Bg,
        startedAtEpochMillis = T0,
        commandSentAtEpochMillis = T0 + 1000,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = T0 + 5000,
        outcome = outcome,
        unexpectedMessages = emptyList(),
    )

    fun wrongCode() = withOutcome(SessionOutcome.Failed(FailureReason.WRONG_CODE, AapsSamples.wrongCode))
    fun sendFailed() = withOutcome(SessionOutcome.Failed(FailureReason.SEND_FAILED, null))
    fun confirmationTimeout() = withOutcome(SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null))
    fun unknownMismatch() =
        withOutcome(SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, AapsSamples.carbsCodeRequest))
    fun timedOut() = withOutcome(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY))

    fun cancelledNoTimes(): SessionResult = SessionResult(
        command = Command.Bg,
        startedAtEpochMillis = null,
        commandSentAtEpochMillis = null,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = null,
        outcome = SessionOutcome.Cancelled,
        unexpectedMessages = emptyList(),
    )
}
