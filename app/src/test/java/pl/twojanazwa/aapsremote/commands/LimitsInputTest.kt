package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LimitsInputTest {
    private fun carbs(text: String) = result(LimitField.CARBS_MAX, LimitsInput.parse(text, "5", "15"))
    private fun interval(text: String) = result(LimitField.BOLUS_INTERVAL, LimitsInput.parse("50", "5", text))
    private fun bolus(text: String) = result(LimitField.BOLUS_MAX, LimitsInput.parse("50", text, "15"))

    /** Wartość (Int) albo rodzaj błędu. */
    private fun result(field: LimitField, v: LimitsValidation): Any = when (v) {
        is LimitsValidation.Valid -> when (field) {
            LimitField.CARBS_MAX -> v.limits.carbsMaxGrams
            LimitField.BOLUS_MAX -> v.limits.bolusMaxCentiUnits
            LimitField.BOLUS_INTERVAL -> v.limits.bolusMinIntervalMinutes
        }
        is LimitsValidation.Invalid -> v.errors.getValue(field)
    }

    @Test
    fun `LIM-03 pole CARBS`() {
        assertEquals(50, carbs("50"))
        assertEquals(75, carbs("075"))
        assertEquals(LimitValueError.EMPTY, carbs(""))
        assertEquals(LimitValueError.BELOW_MIN, carbs("5"))
        assertEquals(LimitValueError.ABOVE_MAX, carbs("101"))
        assertEquals(LimitValueError.ABOVE_MAX, carbs("99999999999999999999"))
        assertEquals(LimitValueError.INVALID_CHARACTERS, carbs("50 "))
        assertEquals(LimitValueError.INVALID_CHARACTERS, carbs("50,5"))
    }

    @Test
    fun `LIM-03 pole odstepu`() {
        assertEquals(0, interval("0"))
        assertEquals(15, interval("15"))
        assertEquals(LimitValueError.ABOVE_MAX, interval("61"))
        assertEquals(LimitValueError.INVALID_CHARACTERS, interval("-1"))
    }

    @Test
    fun `LIM-03 pole BOLUS`() {
        assertEquals(500, bolus("5"))
        assertEquals(250, bolus("2,5"))
        assertEquals(5, bolus("0.05"))
        assertEquals(1000, bolus("10,00"))
        assertEquals(LimitValueError.BELOW_MIN, bolus("0"))
        assertEquals(LimitValueError.ABOVE_MAX, bolus("10,05"))
        assertEquals(LimitValueError.NOT_STEP, bolus("2,52"))
        assertEquals(LimitValueError.TOO_MANY_DECIMALS, bolus("2,555"))
        assertEquals(LimitValueError.INVALID_CHARACTERS, bolus("2,5 j"))
    }

    @Test
    fun `LIM-03 bledy wszystkich pol naraz i toString bez tekstu`() {
        val v = LimitsInput.parse("", "x37", "99")
        assertEquals(3, (v as LimitsValidation.Invalid).errors.size)
        assertFalse(v.toString().contains("37"))
    }
}
