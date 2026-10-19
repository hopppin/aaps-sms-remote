package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSmsTextTest {
    private fun carbs(g: Int) = (Command.Carbs.of(g) as CarbsValidation.Valid).command

    @Test
    fun `CMD-07 BG to tekst BG`() {
        assertEquals("BG", Command.Bg.smsText())
        assertEquals(2, Command.Bg.smsText().length)
    }

    @Test
    fun `CMD-07 PUMP to tekst PUMP`() {
        assertEquals("PUMP", Command.Pump.smsText())
        assertEquals(4, Command.Pump.smsText().length)
    }

    @Test
    fun `CMD-07 CARBS 20 to tekst CARBS 20`() {
        assertEquals("CARBS 20", carbs(20).smsText())
        assertEquals(8, carbs(20).smsText().length)
    }

    @Test
    fun `CMD-07 CARBS 1 i CARBS 50`() {
        assertEquals("CARBS 1", carbs(1).smsText())
        assertEquals("CARBS 50", carbs(50).smsText())
    }

    @Test
    fun `CMD-07 tekst 007 daje CARBS 7`() {
        val command = (Command.Carbs.parse("007") as CarbsValidation.Valid).command
        assertEquals("CARBS 7", command.smsText())
    }

    @Test
    fun `CMD-07 dla 1 do 50 tresc pasuje do wzorca i zawiera ilosc`() {
        val pattern = Regex("CARBS [1-9][0-9]?")
        for (g in 1..50) {
            val text = carbs(g).smsText()
            assertTrue(pattern.matches(text))
            assertEquals(g, text.substringAfter(' ').toInt())
        }
    }
}
