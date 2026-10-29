package pl.twojanazwa.aapsremote.ui.settings.limits

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitField
import pl.twojanazwa.aapsremote.commands.LimitValueError
import pl.twojanazwa.aapsremote.commands.LimitsValidation

class LimitsTextsTest {
    @Test
    fun `LIM-10 teksty bledow z granicami z LIM-02`() {
        assertEquals(
            LimitErrorText(R.string.limits_error_empty, listOf("0,05", "10,00")),
            limitErrorText(LimitField.BOLUS_MAX, LimitValueError.EMPTY),
        )
        assertEquals(
            LimitErrorText(R.string.limits_error_below_min, listOf("10")),
            limitErrorText(LimitField.CARBS_MAX, LimitValueError.BELOW_MIN),
        )
        assertEquals(
            LimitErrorText(R.string.limits_error_above_max, listOf("60")),
            limitErrorText(LimitField.BOLUS_INTERVAL, LimitValueError.ABOVE_MAX),
        )
        assertEquals(R.string.limits_error_invalid_digits, limitErrorText(LimitField.CARBS_MAX, LimitValueError.INVALID_CHARACTERS).res)
        assertEquals(R.string.limits_error_invalid_decimal, limitErrorText(LimitField.BOLUS_MAX, LimitValueError.INVALID_CHARACTERS).res)
        assertEquals(R.string.limits_error_not_step, limitErrorText(LimitField.BOLUS_MAX, LimitValueError.NOT_STEP).res)
        assertEquals(R.string.limits_error_decimals, limitErrorText(LimitField.BOLUS_MAX, LimitValueError.TOO_MANY_DECIMALS).res)
    }

    @Test
    fun `LIM-10 formatowanie setnych jednostek`() {
        assertEquals("2,50", formatCentiUnits(250))
        assertEquals("0,05", formatCentiUnits(5))
        assertEquals("10,00", formatCentiUnits(1000))
    }

    @Test
    fun `LIM-12 toString stanu sekcji i limitow bez wartosci`() {
        val limits = (CommandLimits.of(37, 235, 23) as LimitsValidation.Valid).limits
        val state = CommandLimitsUiState(
            load = LimitsLoad.LOADED,
            carbsMax = "37",
            bolusMax = "2,35",
            interval = "23",
        )
        listOf(state.toString(), limits.toString()).forEach { text ->
            listOf("37", "235", "2,35", "23").forEach { assertFalse(text.contains(it), "toString zawiera $it") }
        }
    }
}
