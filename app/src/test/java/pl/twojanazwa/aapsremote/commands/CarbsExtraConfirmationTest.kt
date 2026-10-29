package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarbsExtraConfirmationTest {

    private fun carbs(grams: Int): Command.Carbs =
        (Command.Carbs.of(grams, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    @Test
    fun `CMD-05 CARBS 40 nie wymaga dodatkowego potwierdzenia`() {
        assertFalse(carbs(40).requiresExtraConfirmation(CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-05 CARBS 41 wymaga dodatkowego potwierdzenia`() {
        assertTrue(carbs(41).requiresExtraConfirmation(CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-05 CARBS 50 wymaga dodatkowego potwierdzenia`() {
        assertTrue(carbs(50).requiresExtraConfirmation(CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-05 CARBS 1 nie wymaga dodatkowego potwierdzenia`() {
        assertFalse(carbs(1).requiresExtraConfirmation(CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-05 CARBS z tekstu 041 wymaga dodatkowego potwierdzenia`() {
        val command = (Command.Carbs.parse("041", CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        assertTrue(command.requiresExtraConfirmation(CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-05 Bg i Pump nie wymagaja dodatkowego potwierdzenia`() {
        assertFalse(Command.Bg.requiresExtraConfirmation(CommandLimits.DEFAULT))
        assertFalse(Command.Pump.requiresExtraConfirmation(CommandLimits.DEFAULT))
    }
}
