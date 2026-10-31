package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.data.HistoryFixtures.T0
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.session.SessionResult

class HistoryCodecBolusTest {

    private val bolus120: Command.Bolus =
        (Command.Bolus.of(120, CommandLimits.DEFAULT) as BolusValidation.Valid).command

    private fun bolusResult() = SessionResult(
        command = bolus120,
        startedAtEpochMillis = T0,
        commandSentAtEpochMillis = T0 + 1000,
        codeSentAtEpochMillis = T0 + 61000,
        finishedAtEpochMillis = T0 + 120000,
        outcome = SessionOutcome.BolusConfirmed(120, AapsSamples.bolusDone),
        unexpectedMessages = emptyList(),
    )

    @Test
    fun `BOL-17 wynik BOLUS 1,20 BolusConfirmed daje wiersz z iloscia bolusa`() {
        val row = HistoryCodec.toRow(bolusResult())
        assertEquals("BOLUS", row.command)
        assertEquals(120, row.bolusCentiUnits)
        assertNull(row.carbsGrams)
        assertEquals("BOLUS_CONFIRMED", row.outcome)
    }

    @Test
    fun `BOL-17 wiersz BOLUS daje wpis BOLUS BolusConfirmed z iloscia bolusa`() {
        val entry = HistoryCodec.toEntry(HistoryCodec.toRow(bolusResult()))
        assertEquals(HistoryCommand.BOLUS, entry.command)
        assertEquals(120, entry.bolusCentiUnits)
        assertNull(entry.carbsGrams)
        assertEquals(HistoryOutcome.BolusConfirmed, entry.outcome)
    }

    @Test
    fun `BOL-17 wiersz CARBS z bolus_centi_units 50 daje po odczycie brak ilosci bolusa`() {
        val row = HistoryRow(1, "CARBS", 20, "CARBS_CONFIRMED", null, T0, T0, T0, T0, null, 0, 50, null, null)
        val entry = HistoryCodec.toEntry(row)
        assertEquals(HistoryCommand.CARBS, entry.command)
        assertNull(entry.bolusCentiUnits)
        assertEquals(20, entry.carbsGrams)
    }

    @Test
    fun `BOL-17 zapis CARBS i BG nie ma ilosci bolusa`() {
        assertNull(HistoryCodec.toRow(HistoryFixtures.carbsConfirmed()).bolusCentiUnits)
        assertNull(HistoryCodec.toRow(HistoryFixtures.bgCompleted()).bolusCentiUnits)
    }

    @Test
    fun `BOL-22 odmowy i blad bolusa zapisuja tekst powodu i po odczycie daja ten sam powod`() {
        fun result(outcome: SessionOutcome) = bolusResult().copy(outcome = outcome)
        val interval = HistoryCodec.toRow(result(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL, "t")))
        assertEquals("AAPS_BOLUS_INTERVAL", interval.reason)
        assertEquals(
            HistoryOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL),
            HistoryCodec.toEntry(interval).outcome,
        )
        val queue = HistoryCodec.toRow(result(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE, "t")))
        assertEquals("AAPS_BOLUS_QUEUE", queue.reason)
        assertEquals(HistoryOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE), HistoryCodec.toEntry(queue).outcome)
        val failed = HistoryCodec.toRow(result(SessionOutcome.Unknown(UnknownReason.BOLUS_FAILED, "t")))
        assertEquals("BOLUS_FAILED", failed.reason)
        assertEquals(HistoryOutcome.Unknown(UnknownReason.BOLUS_FAILED), HistoryCodec.toEntry(failed).outcome)
    }

    @Test
    fun `BOL-18 toString wpisu i wiersza BOLUS nie zawiera ilosci`() {
        val row = HistoryCodec.toRow(bolusResult())
        val entry = HistoryCodec.toEntry(row)
        for (text in listOf(row.toString(), entry.toString())) {
            assertFalse(text.contains("120"))
            assertFalse(text.contains("1,20"))
        }
    }
}
