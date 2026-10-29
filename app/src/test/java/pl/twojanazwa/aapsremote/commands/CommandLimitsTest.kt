package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CommandLimitsTest {
    private fun limits(carbs: Int = 50, bolus: Int = 500, interval: Int = 15): CommandLimits =
        (CommandLimits.of(carbs, bolus, interval) as LimitsValidation.Valid).limits

    private fun error(carbs: Int = 50, bolus: Int = 500, interval: Int = 15, field: LimitField): LimitValueError? =
        (CommandLimits.of(carbs, bolus, interval) as? LimitsValidation.Invalid)?.errors?.get(field)

    private fun carbs(grams: Int, l: CommandLimits) = (Command.Carbs.of(grams, l) as CarbsValidation.Valid).command

    @Test
    fun `LIM-01 limity domyslne to 50 g, 500 setnych i 15 minut`() {
        assertEquals(50, CommandLimits.DEFAULT.carbsMaxGrams)
        assertEquals(500, CommandLimits.DEFAULT.bolusMaxCentiUnits)
        assertEquals(15, CommandLimits.DEFAULT.bolusMinIntervalMinutes)
    }

    @Test
    fun `LIM-02 carbsMaxGrams 9 10 100 101`() {
        assertEquals(LimitValueError.BELOW_MIN, error(carbs = 9, field = LimitField.CARBS_MAX))
        assertEquals(null, error(carbs = 10, field = LimitField.CARBS_MAX))
        assertEquals(null, error(carbs = 100, field = LimitField.CARBS_MAX))
        assertEquals(LimitValueError.ABOVE_MAX, error(carbs = 101, field = LimitField.CARBS_MAX))
    }

    @Test
    fun `LIM-02 bolusMaxCentiUnits 0 5 1000 1005 252`() {
        assertEquals(LimitValueError.BELOW_MIN, error(bolus = 0, field = LimitField.BOLUS_MAX))
        assertEquals(null, error(bolus = 5, field = LimitField.BOLUS_MAX))
        assertEquals(null, error(bolus = 1000, field = LimitField.BOLUS_MAX))
        assertEquals(LimitValueError.ABOVE_MAX, error(bolus = 1005, field = LimitField.BOLUS_MAX))
        assertEquals(LimitValueError.NOT_STEP, error(bolus = 252, field = LimitField.BOLUS_MAX))
    }

    @Test
    fun `LIM-02 odstep -1 0 60 61`() {
        assertEquals(LimitValueError.BELOW_MIN, error(interval = -1, field = LimitField.BOLUS_INTERVAL))
        assertEquals(null, error(interval = 0, field = LimitField.BOLUS_INTERVAL))
        assertEquals(null, error(interval = 60, field = LimitField.BOLUS_INTERVAL))
        assertEquals(LimitValueError.ABOVE_MAX, error(interval = 61, field = LimitField.BOLUS_INTERVAL))
    }

    @Test
    fun `LIM-02 Int MIN_VALUE i MAX_VALUE w kazdym polu bez wyjatku`() {
        for (v in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            val expected = if (v < 0) LimitValueError.BELOW_MIN else LimitValueError.ABOVE_MAX
            assertEquals(expected, error(carbs = v, field = LimitField.CARBS_MAX))
            assertEquals(expected, error(bolus = v, field = LimitField.BOLUS_MAX))
            assertEquals(expected, error(interval = v, field = LimitField.BOLUS_INTERVAL))
        }
    }

    @Test
    fun `LIM-07 tabela progu - bez pytania i z pytaniem`() {
        for ((max, threshold) in listOf(50 to 40, 100 to 80, 10 to 8, 33 to 26)) {
            val l = limits(carbs = max)
            assertEquals(threshold, l.carbsExtraConfirmationAboveGrams)
            assertFalse(carbs(threshold, l).requiresExtraConfirmation(l), "max=$max")
            assertTrue(carbs(threshold + 1, l).requiresExtraConfirmation(l), "max=$max")
        }
    }

    @Test
    fun `LIM-08 szybkie wartosci zalezne od limitu`() {
        assertEquals(listOf(10, 20, 30), limits(carbs = 50).carbsQuickValues)
        assertEquals(listOf(10, 20), limits(carbs = 30).carbsQuickValues)
        assertEquals(emptyList<Int>(), limits(carbs = 10).carbsQuickValues)
    }

    @Test
    fun `BOL-04 ilosc powyzej polowy limitu wymaga potwierdzenia`() {
        val l = limits(bolus = 500)
        assertFalse(l.bolusRequiresExtraConfirmation(250))
        assertTrue(l.bolusRequiresExtraConfirmation(255))
        assertTrue(l.bolusRequiresExtraConfirmation(Int.MAX_VALUE))
    }

    @Test
    fun `LIM-12 toString limitow i wyniku walidacji nie zawiera wartosci`() {
        val validation = CommandLimits.of(37, 235, 23)
        val texts = listOf(validation.toString(), (validation as LimitsValidation.Valid).limits.toString())
        for (text in texts) {
            for (forbidden in listOf("37", "235", "2,35", "23")) assertFalse(text.contains(forbidden), text)
        }
    }

    @Test
    fun `LIM-12 zrodla commands nie wywoluja API logowania`() {
        val forbidden = listOf(
            Regex("""\bandroid\s*\.\s*util\s*\.\s*Log\b"""),
            Regex("""\bLog\s*\.\s*[a-z]+\s*\("""),
            Regex("""\b(?:print|println)\s*\("""),
            Regex("""\bSystem\s*\.\s*(?:out|err)\b"""),
        )
        val root = File("src/main/java/pl/twojanazwa/aapsremote/commands")
        assertTrue(root.isDirectory)
        val violations = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            forbidden.filter { it.containsMatchIn(f.readText()) }.map { "${f.name}: ${it.pattern}" }
        }.toList()
        assertTrue(violations.isEmpty(), "Użycie API logowania: $violations")
    }
}
