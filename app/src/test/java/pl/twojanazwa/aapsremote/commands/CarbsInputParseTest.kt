package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class CarbsInputParseTest {

    private fun grams(input: String): Int? =
        (Command.Carbs.parse(input) as? CarbsValidation.Valid)?.command?.grams

    private fun error(input: String): CarbsAmountError? =
        (Command.Carbs.parse(input) as? CarbsValidation.Invalid)?.error

    @ParameterizedTest(name = "CMD-04 tekst {0} daje {1} g")
    @CsvSource("20,20", "1,1", "50,50", "020,20", "0050,50", "001,1")
    fun `CMD-04 poprawny tekst`(input: String, expected: Int) {
        assertEquals(expected, grams(input))
    }

    @Test
    fun `CMD-04 pusty tekst to EMPTY`() {
        assertEquals(CarbsAmountError.EMPTY, error(""))
    }

    @ParameterizedTest(name = "CMD-04 tekst {0} jest ponizej minimum")
    @ValueSource(strings = ["0", "000"])
    fun `CMD-04 same zera to BELOW_MIN`(input: String) {
        assertEquals(CarbsAmountError.BELOW_MIN, error(input))
    }

    @ParameterizedTest(name = "CMD-04 tekst {0} jest powyzej maksimum")
    @ValueSource(strings = ["51", "100", "0051", "99999999999999999999", "2147483648"])
    fun `CMD-04 tekst powyzej maksimum`(input: String) {
        assertEquals(CarbsAmountError.ABOVE_MAX, error(input))
    }

    @ParameterizedTest(name = "CMD-04 niedozwolone znaki")
    @ValueSource(
        strings = [
            "1,5", "1.5", "20.0", "-5", "+5", "2 0", " 20", "20 ", "20\n", " ", "20g", "abc",
            "２０", "٢٠", "-99999999999999999999",
        ],
    )
    fun `CMD-04 niedozwolone znaki`(input: String) {
        assertEquals(CarbsAmountError.INVALID_CHARACTERS, error(input))
    }

    @Test
    fun `CMD-04 10000 cyfr 9 to ABOVE_MAX`() {
        assertEquals(CarbsAmountError.ABOVE_MAX, error("9".repeat(10_000)))
    }

    @Test
    fun `CMD-04 10000 zer i 20 to poprawna 20`() {
        assertEquals(20, grams("0".repeat(10_000) + "20"))
    }
}
