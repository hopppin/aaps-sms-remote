package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class CommandsRedactionTest {
    @Test
    fun `CMDUI-26 toString stanu panelu nie zawiera PIN-u ani ilosci`() {
        val text = CommandsUiState(carbsInput = "37", summaryGrams = 37, pinText = "4321").toString()
        assertFalse(text.contains("4321"))
        assertFalse(text.contains("37"))
    }
}
