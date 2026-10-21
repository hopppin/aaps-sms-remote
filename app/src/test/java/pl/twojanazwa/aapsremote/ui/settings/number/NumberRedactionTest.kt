package pl.twojanazwa.aapsremote.ui.settings.number

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NumberRedactionTest {
    @Test
    fun `UI-21 toString stanu numeru nie zawiera numeru ani wpisanego tekstu`() {
        val state = AapsNumberUiState(
            display = NumberDisplay.Configured("+447700900123"),
            editing = true,
            input = "+44 7700 900999",
        )
        val text = state.toString()
        listOf("900123", "900999", "7700").forEach {
            assertFalse(text.contains(it), "toString zawiera $it")
        }
        assertFalse(NumberDisplay.Configured("+447700900123").toString().contains("7700"))
    }
}
