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
}
