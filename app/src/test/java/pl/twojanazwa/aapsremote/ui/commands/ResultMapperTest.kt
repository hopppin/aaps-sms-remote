package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.IncomingSms
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.SessionTimeouts
import pl.twojanazwa.aapsremote.session.UnknownReason
import java.io.File
import kotlin.time.Duration.Companion.minutes

class ResultMapperTest {

    private fun carbs(grams: Int): Command =
        (Command.Carbs.of(grams) as CarbsValidation.Valid).command

    private fun result(
        command: Command,
        outcome: SessionOutcome,
        unexpected: Int = 0,
    ) = SessionResult(
        command = command,
        startedAtEpochMillis = 1L,
        commandSentAtEpochMillis = 2L,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = 3L,
        outcome = outcome,
        unexpectedMessages = List(unexpected) { IncomingSms("x", it.toLong()) },
    )

    private fun bgText(): String =
        File("src/test/resources/parser/aaps-3.4.2.6-pl/bg.txt").readText()

    private val failureMessages = mapOf(
        FailureReason.SEND_FAILED to ResultMessage.SEND_FAILED,
        FailureReason.UNRECOGNIZED_REPLY to ResultMessage.UNRECOGNIZED_REPLY,
        FailureReason.AMOUNT_MISMATCH to ResultMessage.AMOUNT_MISMATCH,
        FailureReason.UNEXPECTED_REPLY to ResultMessage.UNEXPECTED_REPLY,
        FailureReason.WRONG_CODE to ResultMessage.WRONG_CODE,
        FailureReason.CODE_REQUEST_EXPIRED to ResultMessage.CODE_REQUEST_EXPIRED,
        FailureReason.CODE_PREPARATION_TIMEOUT to ResultMessage.CODE_PREPARATION_TIMEOUT,
        FailureReason.INTERNAL_ERROR to ResultMessage.INTERNAL_ERROR,
    )

    private val unknownMessages = mapOf(
        UnknownReason.CONFIRMATION_TIMEOUT to ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT,
        UnknownReason.CANCELLED_AFTER_CODE to ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE,
        UnknownReason.CODE_SEND_FAILED to ResultMessage.UNKNOWN_CODE_SEND_FAILED,
        UnknownReason.UNRECOGNIZED_REPLY to ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY,
        UnknownReason.AMOUNT_MISMATCH to ResultMessage.UNKNOWN_AMOUNT_MISMATCH,
        UnknownReason.UNEXPECTED_REPLY to ResultMessage.UNKNOWN_UNEXPECTED_REPLY,
        UnknownReason.INTERNAL_ERROR to ResultMessage.UNKNOWN_INTERNAL_ERROR,
    )

    @Test
    fun `CMDUI-19 kazdy FailureReason daje FAILURE i komunikat o tej samej nazwie`() {
        assertEquals(FailureReason.entries.toSet(), failureMessages.keys)
        failureMessages.forEach { (reason, message) ->
            val view = ResultMapper.map(result(carbs(20), SessionOutcome.Failed(reason, null)))
            assertEquals(ResultKind.FAILURE, view.kind, "$reason")
            assertEquals(message, view.message, "$reason")
            assertNull(view.messageArg, "$reason")
            assertFalse(view.unknownWarning, "$reason")
        }
    }

    @Test
    fun `CMDUI-19 kazdy UnknownReason daje UNKNOWN z ostrzezeniem i minutami tylko dla CONFIRMATION_TIMEOUT`() {
        assertEquals(UnknownReason.entries.toSet(), unknownMessages.keys)
        unknownMessages.forEach { (reason, message) ->
            val view = ResultMapper.map(result(carbs(20), SessionOutcome.Unknown(reason, null)))
            assertEquals(ResultKind.UNKNOWN, view.kind, "$reason")
            assertEquals(message, view.message, "$reason")
            assertTrue(view.unknownWarning, "$reason")
            val expectedArg = if (reason == UnknownReason.CONFIRMATION_TIMEOUT) 6 else null
            assertEquals(expectedArg, view.messageArg, "$reason")
        }
    }

    @Test
    fun `CMDUI-19 TimedOut w SENDING_COMMAND to SEND_TIMEOUT a w innych fazach NO_RESPONSE z 3 minutami`() {
        val sending = ResultMapper.map(
            result(Command.Bg, SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND)),
        )
        assertEquals(ResultKind.NO_RESPONSE, sending.kind)
        assertEquals(ResultMessage.SEND_TIMEOUT, sending.message)
        assertEquals(3, sending.messageArg)
        listOf(SessionPhase.AWAITING_REPLY, SessionPhase.AWAITING_CODE_REQUEST).forEach { phase ->
            val view = ResultMapper.map(result(Command.Bg, SessionOutcome.TimedOut(phase)))
            assertEquals(ResultKind.NO_RESPONSE, view.kind, "$phase")
            assertEquals(ResultMessage.NO_RESPONSE, view.message, "$phase")
            assertEquals(3, view.messageArg, "$phase")
            assertNull(view.replyText, "$phase")
        }
    }

    @Test
    fun `CMDUI-19 minuty pochodza z SessionTimeouts`() {
        val timeouts = SessionTimeouts(reply = 4.minutes, confirmation = 7.minutes)
        val noReply = ResultMapper.map(
            result(Command.Bg, SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY)),
            timeouts,
        )
        val unknown = ResultMapper.map(
            result(carbs(20), SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null)),
            timeouts,
        )
        assertEquals(4, noReply.messageArg)
        assertEquals(7, unknown.messageArg)
    }

    @Test
    fun `CMDUI-19 Completed dla BG i PUMP oraz CarbsConfirmed z iloscia w argumencie`() {
        val bg = ResultMapper.map(
            result(Command.Bg, SessionOutcome.Completed(AapsReply.BgReport(210, bgText()), bgText())),
        )
        assertEquals(ResultKind.SUCCESS, bg.kind)
        assertEquals(ResultMessage.BG_REPLY, bg.message)
        assertNull(bg.commandGrams)
        val pump = ResultMapper.map(
            result(Command.Pump, SessionOutcome.Completed(AapsReply.PumpStatus("p"), "p")),
        )
        assertEquals(ResultKind.SUCCESS, pump.kind)
        assertEquals(ResultMessage.PUMP_REPLY, pump.message)
        val done = ResultMapper.map(result(carbs(1), SessionOutcome.CarbsConfirmed(1, "t")))
        assertEquals(ResultKind.SUCCESS, done.kind)
        assertEquals(ResultMessage.CARBS_CONFIRMED, done.message)
        assertEquals(1, done.messageArg)
        assertEquals(1, done.commandGrams)
    }

    @Test
    fun `CMDUI-19 Cancelled daje CANCELLED bez argumentu`() {
        val view = ResultMapper.map(result(Command.Bg, SessionOutcome.Cancelled))
        assertEquals(ResultKind.CANCELLED, view.kind)
        assertEquals(ResultMessage.CANCELLED, view.message)
        assertNull(view.messageArg)
        assertNull(view.replyText)
    }

    @Test
    fun `CMDUI-19 dodatek kod nie zostal wyslany dla CARBS 20`() {
        val c = carbs(20)
        fun notSent(o: SessionOutcome) = ResultMapper.map(result(c, o)).codeNotSent
        assertTrue(notSent(SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, "t")))
        assertTrue(notSent(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST)))
        assertTrue(notSent(SessionOutcome.Cancelled))
        assertFalse(notSent(SessionOutcome.Failed(FailureReason.WRONG_CODE, "t")))
        assertFalse(notSent(SessionOutcome.CarbsConfirmed(20, "t")))
        assertFalse(notSent(SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null)))
    }

    @Test
    fun `CMDUI-19 dodatek kod nie zostal wyslany jest false dla BG z Cancelled`() {
        assertFalse(ResultMapper.map(result(Command.Bg, SessionOutcome.Cancelled)).codeNotSent)
    }

    @Test
    fun `CMDUI-19 ostrzezenie unknownWarning tylko dla Unknown`() {
        val c = carbs(20)
        val others = listOf(
            SessionOutcome.Failed(FailureReason.SEND_FAILED, null),
            SessionOutcome.CarbsConfirmed(20, "t"),
            SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY),
            SessionOutcome.Cancelled,
        )
        others.forEach { assertFalse(ResultMapper.map(result(c, it)).unknownWarning, "$it") }
    }

    @Test
    fun `CMDUI-20 tresc odpowiedzi AAPS z bg txt dla Completed`() {
        val text = bgText()
        val view = ResultMapper.map(
            result(Command.Bg, SessionOutcome.Completed(AapsReply.BgReport(210, text), text)),
        )
        assertEquals(text, view.replyText)
    }

    @Test
    fun `CMDUI-20 tresc dla UNRECOGNIZED_REPLY a brak tresci dla pozostalych`() {
        val failed = ResultMapper.map(
            result(
                carbs(20),
                SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, "Czesc, kupisz chleb?"),
            ),
        )
        assertEquals("Czesc, kupisz chleb?", failed.replyText)
        val none = listOf(
            SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY),
            SessionOutcome.Cancelled,
            SessionOutcome.Failed(FailureReason.SEND_FAILED, null),
            SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null),
        )
        none.forEach { assertNull(ResultMapper.map(result(carbs(20), it)).replyText, "$it") }
    }

    @Test
    fun `CMDUI-20 unexpectedCount to liczba wiadomosci nieoczekiwanych po TimedOut`() {
        val view = ResultMapper.map(
            result(carbs(1), SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), unexpected = 2),
        )
        assertEquals(2, view.unexpectedCount)
        assertEquals(1, view.commandGrams)
    }
}
