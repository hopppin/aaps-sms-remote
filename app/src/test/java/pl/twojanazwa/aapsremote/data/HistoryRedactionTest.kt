package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

class HistoryRedactionTest {

    private val t0 = 1111111051000L

    private fun entry(id: Long, command: HistoryCommand, grams: Int?, outcome: HistoryOutcome, text: String) =
        HistoryEntry(id, command, grams, outcome, t0, t0 + 1000, null, t0 + 5000, text, 0, null)

    private fun row(id: Long, command: String, grams: Int?, outcome: String, text: String) =
        HistoryRow(id, command, grams, outcome, null, t0, t0 + 1000, null, t0 + 5000, text, 0, null)

    private fun assertRedacted(text: String, body: String, vararg words: String) {
        for (w in words) assertFalse(text.contains(w), "zawiera zabroniony fragment")
        for (line in body.lines().filter { it.isNotBlank() }) {
            assertFalse(text.contains(line), "zawiera linie tresci")
        }
    }

    @Test
    fun `DATA-17 toString wpisu CARBS nie zawiera ilosci ani tresci`() {
        val body = AapsSamples.carbsDone
        val e = entry(1, HistoryCommand.CARBS, 20, HistoryOutcome.CarbsConfirmed, body)
        assertRedacted(e.toString(), body, "20", "Pomyslnie", "89%")
    }

    @Test
    fun `DATA-17 toString wiersza CARBS nie zawiera ilosci ani tresci`() {
        val body = AapsSamples.carbsDone
        val r = row(1, "CARBS", 20, "CARBS_CONFIRMED", body)
        assertRedacted(r.toString(), body, "20", "Pomyslnie", "89%")
    }

    @Test
    fun `DATA-17 toString stanu Entries nie zawiera ilosci ani tresci`() {
        val body = AapsSamples.carbsDone
        val s = HistorySnapshot.Entries(listOf(entry(1, HistoryCommand.CARBS, 20, HistoryOutcome.CarbsConfirmed, body)))
        assertRedacted(s.toString(), body, "20", "Pomyslnie", "89%")
    }

    @Test
    fun `DATA-17 toString wpisu BG nie zawiera tresci`() {
        val body = AapsSamples.bg
        val e = entry(2, HistoryCommand.BG, null, HistoryOutcome.Completed, body)
        assertRedacted(e.toString(), body, "210", "IOB")
    }

    @Test
    fun `DATA-17 toString wiersza BG nie zawiera tresci`() {
        val body = AapsSamples.bg
        val r = row(2, "BG", null, "COMPLETED", body)
        assertRedacted(r.toString(), body, "210", "IOB")
    }
}
