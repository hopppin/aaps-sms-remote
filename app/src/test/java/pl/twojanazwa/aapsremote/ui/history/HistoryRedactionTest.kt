package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome

class HistoryRedactionTest {
    @Test
    fun `HISTUI-15 toString HistoryResultView z CarbsConfirmed 37 g nie zawiera ilosci`() {
        val view = HistoryEntryMapper.result(
            historyEntry(HistoryOutcome.CarbsConfirmed, HistoryCommand.CARBS, carbsGrams = 37),
        )
        assertFalse(view.toString().contains("37"), view.toString())
    }

    @Test
    fun `HISTUI-15 toString wiersza i szczegolow nie zawiera ilosci tresci ani czasow`() {
        val entry = historyEntry(
            HistoryOutcome.CarbsConfirmed,
            HistoryCommand.CARBS,
            carbsGrams = 37,
            replyText = "Pomyslnie wprowadzono 37 g weglowodanow",
        )
        listOf(HistoryEntryMapper.item(entry).toString(), HistoryEntryMapper.details(entry).toString())
            .forEach { text ->
                listOf("37", "Pomyslnie", "1111111").forEach { assertFalse(text.contains(it), text) }
            }
    }
}
