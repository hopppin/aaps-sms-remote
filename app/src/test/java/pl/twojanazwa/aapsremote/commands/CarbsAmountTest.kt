package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CarbsAmountTest {

    @ParameterizedTest(name = "CMD-03 ilosc {0} jest poprawna")
    @ValueSource(ints = [1, 50])
    fun `CMD-03 ilosc w zakresie daje polecenie z ta iloscia`(grams: Int) {
        val result = Command.Carbs.of(grams, CommandLimits.DEFAULT)
        assertTrue(result is CarbsValidation.Valid)
        assertEquals(grams, (result as CarbsValidation.Valid).command.grams)
    }

    @ParameterizedTest(name = "CMD-03 ilosc {0} jest ponizej minimum")
    @ValueSource(ints = [0, -5, Int.MIN_VALUE])
    fun `CMD-03 ilosc ponizej minimum`(grams: Int) {
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.BELOW_MIN), Command.Carbs.of(grams, CommandLimits.DEFAULT))
    }

    @ParameterizedTest(name = "CMD-03 ilosc {0} jest powyzej maksimum")
    @ValueSource(ints = [51, Int.MAX_VALUE])
    fun `CMD-03 ilosc powyzej maksimum`(grams: Int) {
        assertEquals(CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX), Command.Carbs.of(grams, CommandLimits.DEFAULT))
    }

    @Test
    fun `CMD-03 dwa polecenia CARBS 20 sa rowne a 20 i 21 rozne`() {
        val a = (Command.Carbs.of(20, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        val b = (Command.Carbs.of(20, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        val c = (Command.Carbs.of(21, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `CMD-03 Carbs nie ma publicznego konstruktora`() {
        assertTrue(Command.Carbs::class.java.constructors.none { !it.isSynthetic })
    }
}
