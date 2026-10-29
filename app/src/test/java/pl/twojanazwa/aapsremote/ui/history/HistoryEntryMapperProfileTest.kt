package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage

class HistoryEntryMapperProfileTest {
    private fun switch(outcome: HistoryOutcome, number: Int? = 2, percent: Int? = 115) =
        historyEntry(outcome, command = HistoryCommand.PROFILE_SWITCH).copy(profileNumber = number, profilePercent = percent)

    @Test
    fun `PROF-17 etykiety PROFILE STATUS i PROFILE LIST oraz wynik odpowiedzi`() {
        val status = historyEntry(HistoryOutcome.Completed, command = HistoryCommand.PROFILE_STATUS)
        assertEquals(HistoryCommandLabel.PROFILE_STATUS, HistoryEntryMapper.item(status).command)
        assertEquals(ResultMessage.PROFILE_STATUS_REPLY, HistoryEntryMapper.result(status).message)
        val list = historyEntry(HistoryOutcome.Completed, command = HistoryCommand.PROFILE_LIST)
        assertEquals(HistoryCommandLabel.PROFILE_LIST, HistoryEntryMapper.item(list).command)
        assertEquals(ResultMessage.PROFILE_LIST_REPLY, HistoryEntryMapper.result(list).message)
        assertFalse(HistoryEntryMapper.result(list).codeNotSent)
    }

    @Test
    fun `PROF-17 zmiana profilu 2 115 ma etykiete z numerem i procentem, bez pol - szczegoly nieznane`() {
        val entry = switch(HistoryOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY))
        val item = HistoryEntryMapper.item(entry)
        assertEquals(HistoryCommandLabel.PROFILE_SWITCH, item.command)
        assertEquals(2, item.profileNumber)
        assertEquals(115, item.profilePercent)
        assertEquals(115, HistoryEntryMapper.details(entry).profilePercent)
        for (broken in listOf(switch(entry.outcome, number = null), switch(entry.outcome, percent = null))) {
            val view = HistoryEntryMapper.item(broken)
            assertEquals(HistoryCommandLabel.PROFILE_SWITCH_NO_DETAILS, view.command)
        }
    }

    @Test
    fun `PROF-17 pola profilu innych polecen sa ignorowane`() {
        val carbs = historyEntry(HistoryOutcome.Completed).copy(profileNumber = 3, profilePercent = 100)
        val item = HistoryEntryMapper.item(carbs)
        assertEquals(null, item.profileNumber)
        assertEquals(null, item.profilePercent)
    }

    @Test
    fun `PROF-17 PROF-16 niezgodnosc i nierozpoznana odpowiedz maja teksty profilu, kod nie wyslany`() {
        val failed = HistoryEntryMapper.result(switch(HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH)))
        assertEquals(ResultKind.FAILURE, failed.kind)
        assertEquals(ResultMessage.PROFILE_AMOUNT_MISMATCH, failed.message)
        assertTrue(failed.codeNotSent)
        val unknown = HistoryEntryMapper.result(switch(HistoryOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY)))
        assertEquals(ResultMessage.PROFILE_UNKNOWN_UNRECOGNIZED_REPLY, unknown.message)
        assertTrue(unknown.unknownWarning)
        assertFalse(unknown.codeNotSent)
        val afterCode = HistoryEntryMapper.result(switch(HistoryOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH)))
        assertEquals(ResultMessage.PROFILE_UNKNOWN_AMOUNT_MISMATCH, afterCode.message)
    }

    @Test
    fun `PROF-17 zly kod i anulowanie zmiany profilu - kod nie wyslany tylko przed kodem`() {
        assertFalse(HistoryEntryMapper.result(switch(HistoryOutcome.Failed(FailureReason.WRONG_CODE))).codeNotSent)
        assertTrue(HistoryEntryMapper.result(switch(HistoryOutcome.Cancelled)).codeNotSent)
    }

    @Test
    fun `PROF-17 zadne pole poza trescia odpowiedzi nie zawiera nazwy profilu`() {
        val entry = switch(HistoryOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY))
        val shown = listOf(HistoryEntryMapper.item(entry).toString(), HistoryEntryMapper.details(entry).toString())
        shown.forEach { assertFalse("LocalProfile" in it) }
    }
}
