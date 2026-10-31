package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.CommandTestFixtures.T0

class BolusIntervalTest {
    private fun left(vararg entries: HistoryEntry, interval: Int = 15) =
        BolusInterval.minutesLeft(entries.toList(), T0, interval)

    @Test
    fun `BOL-12 potwierdzony bolus sprzed 10 min przy odstepie 15 zostawia 5 min`() {
        assertEquals(5, left(bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 600_000)))
    }

    @Test
    fun `BOL-12 dokladnie 15 min po wyslaniu kodu pozwala`() {
        assertNull(left(bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 900_000)))
    }

    @Test
    fun `BOL-12 Unknown bez wyslania kodu liczy sie od zakonczenia`() {
        val entry = bolusEntry(HistoryOutcome.Unknown(UnknownReason.CODE_SEND_FAILED), finishedAt = T0 - 60_000)
        assertEquals(14, left(entry))
    }

    @Test
    fun `BOL-12 Failed AMOUNT_MISMATCH bez kodu sie nie liczy`() {
        val entry = bolusEntry(HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH), finishedAt = T0 - 60_000)
        assertNull(left(entry))
    }

    @Test
    fun `BOL-20 Failed AAPS_BOLUS_INTERVAL i AAPS_BOLUS_QUEUE bez kodu sie nie licza`() {
        listOf(FailureReason.AAPS_BOLUS_INTERVAL, FailureReason.AAPS_BOLUS_QUEUE).forEach {
            assertNull(left(bolusEntry(HistoryOutcome.Failed(it), finishedAt = T0 - 60_000)), "$it")
        }
    }

    @Test
    fun `BOL-21 Unknown BOLUS_FAILED z kodem sprzed 10 min zostawia 5 min`() {
        val entry = bolusEntry(HistoryOutcome.Unknown(UnknownReason.BOLUS_FAILED), codeSentAt = T0 - 600_000)
        assertEquals(5, left(entry))
    }

    @Test
    fun `BOL-12 Failed WRONG_CODE z wyslanym kodem sie liczy`() {
        val entry = bolusEntry(HistoryOutcome.Failed(FailureReason.WRONG_CODE), codeSentAt = T0 - 60_000)
        assertEquals(14, left(entry))
    }

    @Test
    fun `BOL-12 zegar cofniety daje pelny odstep`() {
        assertEquals(15, left(bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 + 60_000)))
    }

    @Test
    fun `BOL-12 CARBS sie nie liczy`() {
        val entry = bolusEntry(HistoryOutcome.CarbsConfirmed, command = HistoryCommand.CARBS, codeSentAt = T0 - 60_000)
        assertNull(left(entry))
    }

    @Test
    fun `BOL-12 wpis bez zadnej chwili jest pomijany, a wynik to najdluzsze oczekiwanie`() {
        val none = bolusEntry(HistoryOutcome.BolusConfirmed)
        val older = bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 600_000)
        val newer = bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 120_000)
        assertEquals(13, left(none, older, newer))
    }

    @Test
    fun `BOL-12 chwila odniesienia to kod, potem zakonczenie, potem rozpoczecie`() {
        val entry = bolusEntry(
            HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 600_000, finishedAt = T0 - 60_000, startedAt = T0 - 70_000,
        )
        assertEquals(5, left(entry))
        assertEquals(14, left(bolusEntry(HistoryOutcome.BolusConfirmed, startedAt = T0 - 60_000)))
    }

    @Test
    fun `BOL-12 czesc minuty zaokragla sie w gore`() {
        assertEquals(1, left(bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 899_000)))
    }
}
