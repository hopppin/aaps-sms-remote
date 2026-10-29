package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.ExpectedCommand

class CommandToExpectedTest {
    @Test
    fun `CMD-08 Bg daje ExpectedCommand Bg`() {
        assertEquals(ExpectedCommand.Bg, Command.Bg.toExpectedCommand())
    }

    @Test
    fun `CMD-08 Pump daje ExpectedCommand Pump`() {
        assertEquals(ExpectedCommand.Pump, Command.Pump.toExpectedCommand())
    }

    @Test
    fun `CMD-08 CARBS od 1 do 50 daje ExpectedCommand Carbs z ta sama iloscia`() {
        for (g in 1..50) {
            val command = (Command.Carbs.of(g, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
            assertEquals(ExpectedCommand.Carbs(g), command.toExpectedCommand())
        }
    }
}
