package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage

class HistoryEntryMapperTest {

    private fun result(outcome: HistoryOutcome, command: HistoryCommand = HistoryCommand.CARBS, grams: Int? = 20) =
        HistoryEntryMapper.result(historyEntry(outcome, command, grams))

    private fun assertView(view: HistoryResultView, kind: ResultKind?, message: ResultMessage?, arg: Int? = null) {
        assertEquals(kind, view.kind)
        assertEquals(message, view.message)
        assertEquals(arg, view.messageArg)
    }

    @Test
    fun `HISTUI-03 etykiety polecenia`() {
        fun item(command: HistoryCommand, grams: Int?) =
            HistoryEntryMapper.item(historyEntry(HistoryOutcome.Cancelled, command, grams))
        assertEquals(HistoryCommandLabel.BG, item(HistoryCommand.BG, null).command)
        assertEquals(HistoryCommandLabel.PUMP, item(HistoryCommand.PUMP, null).command)
        val carbs = item(HistoryCommand.CARBS, 20)
        assertEquals(HistoryCommandLabel.CARBS, carbs.command)
        assertEquals(20, carbs.grams)
        val noAmount = item(HistoryCommand.CARBS, null)
        assertEquals(HistoryCommandLabel.CARBS_NO_AMOUNT, noAmount.command)
        assertNull(noAmount.grams)
        assertEquals(HistoryCommandLabel.UNRECOGNIZED, item(HistoryCommand.UNRECOGNIZED, null).command)
    }

    @Test
    fun `HISTUI-05 czas wiersza to rozpoczecie albo zakonczenie albo brak`() {
        val fallback = historyEntry(HistoryOutcome.Cancelled, startedAt = null, finishedAt = T0 + 5000)
        assertEquals(T0 + 5000, HistoryEntryMapper.item(fallback).listTimeEpochMillis)
        val none = historyEntry(
            HistoryOutcome.Cancelled, startedAt = null, commandSentAt = null, finishedAt = null,
        )
        assertNull(HistoryEntryMapper.item(none).listTimeEpochMillis)
        assertEquals(T0, HistoryEntryMapper.item(historyEntry(HistoryOutcome.Cancelled)).listTimeEpochMillis)
    }

    @Test
    fun `HISTUI-05 szczegoly maja cztery czasy w kolejnosci`() {
        val entry = historyEntry(
            HistoryOutcome.Completed, HistoryCommand.BG, startedAt = T0, commandSentAt = T0 + 1000,
            codeSentAt = null, finishedAt = T0 + 5000,
        )
        assertEquals(
            listOf(
                HistoryTimeLabel.STARTED to T0,
                HistoryTimeLabel.COMMAND_SENT to T0 + 1000,
                HistoryTimeLabel.CODE_SENT to null,
                HistoryTimeLabel.FINISHED to T0 + 5000,
            ),
            HistoryEntryMapper.details(entry).times,
        )
    }

    @Test
    fun `HISTUI-06 tresc odpowiedzi znak w znak i brak tresci`() {
        val bg = File("src/test/resources/parser/aaps-3.4.2.6-pl/bg.txt").readText()
        fun details(text: String?, outcome: HistoryOutcome = HistoryOutcome.Completed) =
            HistoryEntryMapper.details(historyEntry(outcome, HistoryCommand.BG, replyText = text))
        assertEquals(bg, details(bg).replyText)
        assertEquals(2000, details("a".repeat(2000)).replyText!!.length)
        assertNull(details(null, HistoryOutcome.TimedOut(SessionPhase.AWAITING_REPLY)).replyText)
    }

    @Test
    fun `HISTUI-06 liczba nieoczekiwanych wiadomosci null przy zerze`() {
        assertNull(HistoryEntryMapper.details(historyEntry(HistoryOutcome.Cancelled, unexpected = 0)).unexpectedCount)
        assertEquals(2, HistoryEntryMapper.details(historyEntry(HistoryOutcome.Cancelled, unexpected = 2)).unexpectedCount)
    }

    @Test
    fun `HISTUI-04 BG PUMP i CARBS 1oraz Cancelled daja rodzaj i komunikat jak w CMDUI-19`() {
        assertView(
            result(HistoryOutcome.Completed, HistoryCommand.BG, null),
            ResultKind.SUCCESS, ResultMessage.BG_REPLY,
        )
        assertView(
            result(HistoryOutcome.Completed, HistoryCommand.PUMP, null),
            ResultKind.SUCCESS, ResultMessage.PUMP_REPLY,
        )
        assertView(
            result(HistoryOutcome.CarbsConfirmed, grams = 1),
            ResultKind.SUCCESS, ResultMessage.CARBS_CONFIRMED, 1,
        )
        assertView(
            result(HistoryOutcome.Cancelled, HistoryCommand.BG, null),
            ResultKind.CANCELLED, ResultMessage.CANCELLED,
        )
    }

    @Test
    fun `HISTUI-04 wartosci null i nierozpoznane daja brak szczegolow bez wyjatku`() {
        assertView(result(HistoryOutcome.Failed(null)), ResultKind.FAILURE, null)
        assertView(result(HistoryOutcome.Unknown(null)), ResultKind.UNKNOWN, null)
        assertView(result(HistoryOutcome.TimedOut(null)), ResultKind.NO_RESPONSE, ResultMessage.NO_RESPONSE, 3)
        assertView(result(HistoryOutcome.Unrecognized), null, null)
        assertView(result(HistoryOutcome.Completed), ResultKind.SUCCESS, null)
        assertView(result(HistoryOutcome.CarbsConfirmed, grams = null), ResultKind.SUCCESS, null)
        assertView(
            result(HistoryOutcome.Completed, HistoryCommand.UNRECOGNIZED, null),
            ResultKind.SUCCESS, null,
        )
        assertView(
            result(HistoryOutcome.CarbsConfirmed, HistoryCommand.BG, null),
            ResultKind.SUCCESS, null,
        )
        assertView(
            result(HistoryOutcome.CarbsConfirmed, HistoryCommand.UNRECOGNIZED, null),
            ResultKind.SUCCESS, null,
        )
    }

    @Test
    fun `HISTUI-04 TimedOut w SENDING_COMMAND daje SEND_TIMEOUT z 3 minutami`() {
        assertView(
            result(HistoryOutcome.TimedOut(SessionPhase.SENDING_COMMAND)),
            ResultKind.NO_RESPONSE, ResultMessage.SEND_TIMEOUT, 3,
        )
    }

    @Test
    fun `HISTUI-04 Unknown CONFIRMATION_TIMEOUT daje 6 minut`() {
        assertView(
            result(HistoryOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT)),
            ResultKind.UNKNOWN, ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT, 6,
        )
    }

    @Test
    fun `HISTUI-04 kod nie zostal wyslany dla CARBS 20`() {
        listOf(
            HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH),
            HistoryOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST),
            HistoryOutcome.Cancelled,
        ).forEach { assertTrue(result(it).codeNotSent, "$it") }
        listOf(
            HistoryOutcome.Failed(FailureReason.WRONG_CODE),
            HistoryOutcome.Failed(null),
            HistoryOutcome.CarbsConfirmed,
            HistoryOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT),
        ).forEach { assertFalse(result(it).codeNotSent, "$it") }
        assertFalse(result(HistoryOutcome.Cancelled, HistoryCommand.BG, null).codeNotSent)
    }

    @Test
    fun `HISTUI-04 ostrzezenie tylko dla Unknown w tym Unknown null`() {
        UnknownReason.entries.forEach { assertTrue(result(HistoryOutcome.Unknown(it)).unknownWarning, "$it") }
        assertTrue(result(HistoryOutcome.Unknown(null)).unknownWarning)
        listOf(
            HistoryOutcome.Completed, HistoryOutcome.CarbsConfirmed, HistoryOutcome.Failed(null),
            HistoryOutcome.TimedOut(null), HistoryOutcome.Cancelled, HistoryOutcome.Unrecognized,
        ).forEach { assertFalse(result(it).unknownWarning, "$it") }
        assertNull(result(HistoryOutcome.Unrecognized).kind)
    }
}
