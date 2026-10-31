package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage

class HistoryEntryMapperBolusTest {
    private fun bolus(outcome: HistoryOutcome, centi: Int? = 120) =
        historyEntry(outcome, command = HistoryCommand.BOLUS, bolusCentiUnits = centi)

    @Test
    fun `BOL-17 etykieta BOLUS z iloscia ma argument 120, bez ilosci jest nieznana`() {
        val withAmount = HistoryEntryMapper.item(bolus(HistoryOutcome.BolusConfirmed))
        assertEquals(HistoryCommandLabel.BOLUS, withAmount.command)
        assertEquals(120, withAmount.bolusCentiUnits)
        val without = HistoryEntryMapper.item(bolus(HistoryOutcome.BolusConfirmed, centi = null))
        assertEquals(HistoryCommandLabel.BOLUS_NO_AMOUNT, without.command)
        assertEquals(120, HistoryEntryMapper.details(bolus(HistoryOutcome.BolusConfirmed)).bolusCentiUnits)
    }

    @Test
    fun `BOL-17 wynik BolusConfirmed z iloscia to potwierdzenie podania, bez ilosci brak szczegolow`() {
        val ok = HistoryEntryMapper.result(bolus(HistoryOutcome.BolusConfirmed))
        assertEquals(ResultKind.SUCCESS, ok.kind)
        assertEquals(ResultMessage.BOLUS_CONFIRMED, ok.message)
        assertEquals(120, ok.messageArg)
        val missing = HistoryEntryMapper.result(bolus(HistoryOutcome.BolusConfirmed, centi = null))
        assertEquals(ResultKind.SUCCESS, missing.kind)
        assertEquals(null, missing.message)
    }

    @Test
    fun `BOL-22 wpis z odmowa i bledem bolusa ma tekst wyniku jak w tabeli`() {
        val interval = HistoryEntryMapper.result(bolus(HistoryOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL)))
        assertEquals(ResultKind.FAILURE, interval.kind)
        assertEquals(ResultMessage.AAPS_BOLUS_INTERVAL, interval.message)
        assertTrue(interval.codeNotSent)
        val queue = HistoryEntryMapper.result(bolus(HistoryOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE)))
        assertEquals(ResultMessage.AAPS_BOLUS_QUEUE, queue.message)
        val failed = HistoryEntryMapper.result(bolus(HistoryOutcome.Unknown(UnknownReason.BOLUS_FAILED)))
        assertEquals(ResultKind.UNKNOWN, failed.kind)
        assertEquals(ResultMessage.UNKNOWN_BOLUS_FAILED, failed.message)
        assertTrue(failed.unknownWarning)
    }

    @Test
    fun `BOL-17 niezgodna ilosc i nierozpoznana odpowiedz bolusa maja teksty bolusowe, ilosc z CARBS nie wycieka`() {
        val failed = HistoryEntryMapper.result(bolus(HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH)))
        assertEquals(ResultMessage.BOLUS_AMOUNT_MISMATCH, failed.message)
        assertTrue(failed.codeNotSent)
        val unknown = HistoryEntryMapper.result(bolus(HistoryOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY)))
        assertEquals(ResultMessage.BOLUS_UNKNOWN_UNRECOGNIZED_REPLY, unknown.message)
        assertTrue(unknown.unknownWarning)
        val carbs = historyEntry(HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH), bolusCentiUnits = 50)
        assertEquals(ResultMessage.AMOUNT_MISMATCH, HistoryEntryMapper.result(carbs).message)
        assertEquals(null, HistoryEntryMapper.item(carbs).bolusCentiUnits)
    }
}
