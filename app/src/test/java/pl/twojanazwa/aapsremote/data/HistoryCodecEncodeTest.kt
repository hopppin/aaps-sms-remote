package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import pl.twojanazwa.aapsremote.data.HistoryFixtures.T0
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult

class HistoryCodecEncodeTest {

    @Test
    fun `DATA-07 wynik BG Completed trafia do wiersza`() {
        val row = HistoryCodec.toRow(HistoryFixtures.bgCompleted())
        assertEquals(
            HistoryRow(0, "BG", null, "COMPLETED", null, T0, T0 + 1000, null, T0 + 5000, AapsSamples.bg, 0),
            row,
        )
    }

    @Test
    fun `DATA-07 wynik CARBS 20 CarbsConfirmed trafia do wiersza`() {
        val row = HistoryCodec.toRow(HistoryFixtures.carbsConfirmed())
        assertEquals(
            HistoryRow(
                0, "CARBS", 20, "CARBS_CONFIRMED", null, T0, T0 + 1000, T0 + 61000, T0 + 120000,
                AapsSamples.carbsDone, 2,
            ),
            row,
        )
    }

    @ParameterizedTest
    @MethodSource("kinds")
    fun `DATA-07 rodzaj powod i tresc`(result: SessionResult, outcome: String, reason: String?, text: String?) {
        val row = HistoryCodec.toRow(result)
        assertEquals(outcome, row.outcome)
        assertEquals(reason, row.reason)
        assertEquals(text, row.replyText)
    }

    @Test
    fun `BOL-11 wynik BolusConfirmed jest zapisywany bez ilosci a odczyt daje Unrecognized`() {
        val row = HistoryCodec.toRow(
            HistoryFixtures.withOutcome(SessionOutcome.BolusConfirmed(120, AapsSamples.bolusDone)),
        )
        assertEquals("BOLUS_CONFIRMED", row.outcome)
        assertNull(row.reason)
        assertNull(row.carbsGrams)
        assertEquals(AapsSamples.bolusDone, row.replyText)
        assertEquals(HistoryOutcome.Unrecognized, HistoryCodec.toEntry(row).outcome)
    }

    @Test
    fun `DATA-07 wynik Cancelled bez czasow daje cztery null`() {
        val row = HistoryCodec.toRow(HistoryFixtures.cancelledNoTimes())
        assertNull(row.startedAtEpochMillis)
        assertNull(row.commandSentAtEpochMillis)
        assertNull(row.codeSentAtEpochMillis)
        assertNull(row.finishedAtEpochMillis)
        assertEquals("CANCELLED", row.outcome)
    }

    companion object {
        @JvmStatic
        fun kinds() = listOf(
            Arguments.of(HistoryFixtures.wrongCode(), "FAILED", "WRONG_CODE", AapsSamples.wrongCode),
            Arguments.of(HistoryFixtures.sendFailed(), "FAILED", "SEND_FAILED", null),
            Arguments.of(HistoryFixtures.confirmationTimeout(), "UNKNOWN", "CONFIRMATION_TIMEOUT", null),
            Arguments.of(HistoryFixtures.unknownMismatch(), "UNKNOWN", "AMOUNT_MISMATCH", AapsSamples.carbsCodeRequest),
            Arguments.of(HistoryFixtures.timedOut(), "TIMED_OUT", "AWAITING_REPLY", null),
            Arguments.of(HistoryFixtures.cancelledNoTimes(), "CANCELLED", null, null),
        )
    }
}
