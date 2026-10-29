package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarbsLimitsTest {
    private fun limits(carbs: Int) = (CommandLimits.of(carbs, 500, 15) as LimitsValidation.Valid).limits

    @Test
    fun `LIM-06 carbsMaxGrams 30 - 30 poprawne, 31 powyzej maksimum`() {
        val l = limits(30)
        assertEquals(30, (Command.Carbs.of(30, l) as CarbsValidation.Valid).command.grams)
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.of(31, l))
    }

    @Test
    fun `LIM-06 carbsMaxGrams 100 - tekst 100 i 0100 poprawne, 101 powyzej maksimum`() {
        val l = limits(100)
        assertEquals(100, (Command.Carbs.parse("100", l) as CarbsValidation.Valid).command.grams)
        assertEquals(100, (Command.Carbs.parse("0100", l) as CarbsValidation.Valid).command.grams)
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.parse("101", l))
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.parse("99999999999999999999", l))
    }

    @Test
    fun `LIM-06 limity domyslne - 50 poprawne, 51 i 100 powyzej maksimum`() {
        assertEquals(50, (Command.Carbs.parse("050", CommandLimits.DEFAULT) as CarbsValidation.Valid).command.grams)
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.parse("51", CommandLimits.DEFAULT))
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.parse("100", CommandLimits.DEFAULT))
    }
}
