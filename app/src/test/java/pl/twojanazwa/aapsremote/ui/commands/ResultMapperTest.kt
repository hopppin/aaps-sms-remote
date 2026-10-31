package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.commands.CommandLimits
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
        (Command.Carbs.of(grams, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

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
        FailureReason.AAPS_BOLUS_INTERVAL to ResultMessage.AAPS_BOLUS_INTERVAL,
        FailureReason.AAPS_BOLUS_QUEUE to ResultMessage.AAPS_BOLUS_QUEUE,
    )

    private val unknownMessages = mapOf(
        UnknownReason.CONFIRMATION_TIMEOUT to ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT,
        UnknownReason.CANCELLED_AFTER_CODE to ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE,
        UnknownReason.CODE_SEND_FAILED to ResultMessage.UNKNOWN_CODE_SEND_FAILED,
        UnknownReason.UNRECOGNIZED_REPLY to ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY,
        UnknownReason.AMOUNT_MISMATCH to ResultMessage.UNKNOWN_AMOUNT_MISMATCH,
        UnknownReason.UNEXPECTED_REPLY to ResultMessage.UNKNOWN_UNEXPECTED_REPLY,
        UnknownReason.INTERNAL_ERROR to ResultMessage.UNKNOWN_INTERNAL_ERROR,
        UnknownReason.BOLUS_FAILED to ResultMessage.UNKNOWN_BOLUS_FAILED,
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

    private fun bolus(centiUnits: Int): Command =
        (Command.Bolus.of(centiUnits, CommandLimits.DEFAULT) as BolusValidation.Valid).command

    @Test
    fun `BOL-16 BolusConfirmed 120 daje sukces z argumentem 120 i trescia potwierdzenia`() {
        val text = fileText("bolus-sukces-1-20.txt")
        val view = ResultMapper.map(result(bolus(120), SessionOutcome.BolusConfirmed(120, text)))
        assertEquals(ResultKind.SUCCESS, view.kind)
        assertEquals(ResultMessage.BOLUS_CONFIRMED, view.message)
        assertEquals(120, view.messageArg)
        assertEquals(120, view.commandCentiUnits)
        assertEquals(text, view.replyText)
        assertEquals("1,20", formatCentiUnits(120))
        assertEquals("AAPS potwierdził podanie %1\$s j.", strings["result_bolus_confirmed"])
    }

    @Test
    fun `BOL-16 kazdy UnknownReason bolusa ma ostrzezenie bolusowe, CARBS ogolne`() {
        UnknownReason.entries.forEach {
            assertTrue(ResultMapper.map(result(bolus(120), SessionOutcome.Unknown(it, null))).unknownWarning, "$it")
        }
        assertEquals(R.string.result_unknown_warning_bolus, unknownWarningRes(bolus = true))
        assertEquals(R.string.result_unknown_warning, unknownWarningRes(bolus = false))
        assertEquals(
            "Nie ponawiaj bolusa, zanim nie sprawdzisz w AAPS, czy został podany.",
            strings["result_unknown_warning_bolus"],
        )
    }

    @Test
    fun `BOL-16 AMOUNT_MISMATCH i UNRECOGNIZED_REPLY bolusa maja osobne teksty niz CARBS`() {
        val failed = ResultMapper.map(result(bolus(120), SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, null)))
        assertEquals(ResultMessage.BOLUS_AMOUNT_MISMATCH, failed.message)
        assertEquals(
            ResultMessage.AMOUNT_MISMATCH,
            ResultMapper.map(result(carbs(5), SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, null))).message,
        )
        val unknown = ResultMapper.map(result(bolus(120), SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "x")))
        assertEquals(ResultMessage.BOLUS_UNKNOWN_UNRECOGNIZED_REPLY, unknown.message)
        assertEquals(
            "Po wysłaniu kodu przyszła nierozpoznana odpowiedź AAPS. Przeczytaj treść SMS i sprawdź w AAPS.",
            strings["result_bolus_unknown_unrecognized_reply"],
        )
        assertTrue(failed.codeNotSent)
    }

    @Test
    fun `BOL-22 odmowy i blad bolusa maja rodzaj, komunikat, tresc SMS i dodatki`() {
        val interval = ResultMapper.map(
            result(bolus(10), SessionOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL, "tresc")),
        )
        assertEquals(ResultKind.FAILURE, interval.kind)
        assertEquals(ResultMessage.AAPS_BOLUS_INTERVAL, interval.message)
        assertEquals("tresc", interval.replyText)
        assertTrue(interval.codeNotSent)
        val queue = ResultMapper.map(result(bolus(10), SessionOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE, "tresc")))
        assertEquals(ResultMessage.AAPS_BOLUS_QUEUE, queue.message)
        assertTrue(queue.codeNotSent)
        val failed = ResultMapper.map(result(bolus(10), SessionOutcome.Unknown(UnknownReason.BOLUS_FAILED, "tresc")))
        assertEquals(ResultKind.UNKNOWN, failed.kind)
        assertEquals(ResultMessage.UNKNOWN_BOLUS_FAILED, failed.message)
        assertEquals("tresc", failed.replyText)
        assertTrue(failed.unknownWarning)
        assertFalse(failed.codeNotSent)
        assertEquals(
            "AAPS odmówił bolusa: od ostatniego zdalnego bolusa minęło za mało czasu (minimalny odstęp w AAPS).",
            strings["result_failed_aaps_bolus_interval"],
        )
        assertEquals("AAPS odmówił bolusa: w kolejce pompy czeka inny bolus.", strings["result_failed_aaps_bolus_queue"])
        assertEquals(
            "AAPS zgłosił, że bolus się nie powiódł. Sprawdź w AAPS, ile insuliny podano.",
            strings["result_unknown_bolus_failed"],
        )
    }

    @Test
    fun `BOL-16 BOLUS ma w kazdej fazie te same kroki co CARBS`() {
        SessionPhase.entries.forEach {
            assertEquals(ProgressSteps.of(CommandId.CARBS, it), ProgressSteps.of(CommandId.BOLUS, it), "$it")
        }
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

    private val strings: Map<String, String> by lazy {
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(File("src/main/res/values/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun fileText(name: String): String =
        File("src/test/resources/parser/aaps-3.4.2.6-pl/$name").readText()

    @Test
    fun `CMDUI-19 Completed dla CARBS 20 to sukces DETAILS_MISSING bez argumentu i bez wyjatku`() {
        val view = ResultMapper.map(
            result(carbs(20), SessionOutcome.Completed(AapsReply.PumpStatus("p"), "p")),
        )
        assertEquals(ResultKind.SUCCESS, view.kind)
        assertEquals(ResultMessage.DETAILS_MISSING, view.message)
        assertNull(view.messageArg)
        assertEquals("p", view.replyText)
        assertFalse(view.codeNotSent)
    }

    @Test
    fun `CMDUI-19 CarbsConfirmed 37 dla polecenia CARBS 1 daje argument z wyniku`() {
        val view = ResultMapper.map(result(carbs(1), SessionOutcome.CarbsConfirmed(37, "t")))
        assertEquals(37, view.messageArg)
        assertEquals(1, view.commandGrams)
    }

    @Test
    fun `CMDUI-19 codeNotSent dla CARBS 20 z kazdym FailureReason poza WRONG_CODE`() {
        FailureReason.entries.forEach { reason ->
            val view = ResultMapper.map(result(carbs(20), SessionOutcome.Failed(reason, null)))
            assertEquals(reason != FailureReason.WRONG_CODE, view.codeNotSent, "$reason")
        }
    }

    @Test
    fun `CMDUI-19 codeNotSent false i ostrzezenie dla CARBS 20 z kazdym UnknownReason`() {
        UnknownReason.entries.forEach { reason ->
            val view = ResultMapper.map(result(carbs(20), SessionOutcome.Unknown(reason, null)))
            assertFalse(view.codeNotSent, "$reason")
            assertTrue(view.unknownWarning, "$reason")
        }
    }

    @Test
    fun `CMDUI-19 codeNotSent dla CARBS 20 z TimedOut w trzech fazach i Cancelled`() {
        listOf(
            SessionPhase.SENDING_COMMAND,
            SessionPhase.AWAITING_REPLY,
            SessionPhase.AWAITING_CODE_REQUEST,
        ).forEach { phase ->
            assertTrue(ResultMapper.map(result(carbs(20), SessionOutcome.TimedOut(phase))).codeNotSent, "$phase")
        }
        assertTrue(ResultMapper.map(result(carbs(20), SessionOutcome.Cancelled)).codeNotSent)
    }

    @Test
    fun `CMDUI-19 codeNotSent false dla BG z kazdym wynikiem`() {
        FailureReason.entries.forEach {
            assertFalse(ResultMapper.map(result(Command.Bg, SessionOutcome.Failed(it, null))).codeNotSent, "$it")
        }
        UnknownReason.entries.forEach {
            assertFalse(ResultMapper.map(result(Command.Bg, SessionOutcome.Unknown(it, null))).codeNotSent, "$it")
        }
        listOf(
            SessionOutcome.Completed(AapsReply.BgReport(210, "t"), "t"),
            SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND),
            SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY),
            SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST),
            SessionOutcome.Cancelled,
        ).forEach { assertFalse(ResultMapper.map(result(Command.Bg, it)).codeNotSent, "$it") }
    }

    @Test
    fun `CMDUI-19 messageArg niepusty dokladnie dla komunikatow z argumentem`() {
        val seen = mutableSetOf<ResultMessage>()
        resultSamples().forEach {
            val view = ResultMapper.map(it)
            seen += view.message
            assertEquals(view.message in MESSAGES_WITH_ARG, view.messageArg != null, "${view.message}")
        }
        assertEquals(ResultMessage.entries.toSet(), seen)
    }

    @Test
    fun `CMDUI-20 tresc SMS dla Completed CarbsConfirmed Failed i Unknown`() {
        val wrong = fileText("zly-kod.txt")
        val chleb = "Czesc, kupisz chleb?"
        fun text(c: Command, o: SessionOutcome) = ResultMapper.map(result(c, o)).replyText
        assertEquals("p", text(carbs(20), SessionOutcome.Completed(AapsReply.PumpStatus("p"), "p")))
        assertEquals("ok", text(carbs(1), SessionOutcome.CarbsConfirmed(1, "ok")))
        assertEquals(chleb, text(carbs(1), SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, chleb)))
        assertEquals(wrong, text(carbs(1), SessionOutcome.Failed(FailureReason.WRONG_CODE, wrong)))
        assertEquals(chleb, text(carbs(1), SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, chleb)))
    }

    /** Po jednym wyniku na każdy [ResultMessage] (wspólne z RunTextsTest). */
    private fun profileSwitch(): Command =
        (Command.ProfileSwitch.of(115)
            as pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation.Valid).command

    internal fun resultSamples(): List<SessionResult> {
        val c = carbs(20)
        return listOf(
            result(Command.Bg, SessionOutcome.Completed(AapsReply.BgReport(210, "t"), "t")),
            result(Command.Pump, SessionOutcome.Completed(AapsReply.PumpStatus("p"), "p")),
            result(c, SessionOutcome.Completed(AapsReply.PumpStatus("p"), "p")),
            result(c, SessionOutcome.CarbsConfirmed(20, "t")),
            result(bolus(120), SessionOutcome.BolusConfirmed(120, "t")),
            result(bolus(120), SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, null)),
            result(bolus(120), SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, null)),
            result(Command.ProfileStatus, SessionOutcome.Completed(AapsReply.ProfileStatusReport("p"), "p")),
            result(Command.ProfileList, SessionOutcome.Completed(AapsReply.ProfileStatusReport("p"), "p")),
            result(profileSwitch(), SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, null)),
            result(profileSwitch(), SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, null)),
            result(profileSwitch(), SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, null)),
            result(Command.Bg, SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND)),
            result(Command.Bg, SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY)),
            result(c, SessionOutcome.Cancelled),
        ) + FailureReason.entries.map { result(c, SessionOutcome.Failed(it, null)) } +
            UnknownReason.entries.map { result(c, SessionOutcome.Unknown(it, null)) }
    }

    companion object {
        /** Komunikaty z argumentem `%1$d` (design, tabela odwzorowania). */
        val MESSAGES_WITH_ARG = setOf(
            ResultMessage.CARBS_CONFIRMED,
            ResultMessage.BOLUS_CONFIRMED,
            ResultMessage.NO_RESPONSE,
            ResultMessage.SEND_TIMEOUT,
            ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT,
        )
    }
}
