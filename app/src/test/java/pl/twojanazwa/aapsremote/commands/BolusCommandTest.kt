package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.ExpectedCommand

class BolusCommandTest {
    private fun limits(max: Int = 500) =
        (CommandLimits.of(50, max, 15) as LimitsValidation.Valid).limits

    private fun bolus(centi: Int, max: Int = 1000) =
        (Command.Bolus.of(centi, limits(max)) as BolusValidation.Valid).command

    private fun err(v: BolusValidation) = (v as BolusValidation.Invalid).error

    @Test
    fun `BOL-01 wpis BOLUS jest czwarty, wymaga kodu i ma podsumowanie z PIN-em`() {
        val d = CommandCatalog.entries[3]
        assertEquals(CommandId.BOLUS, d.id)
        assertEquals("BOLUS", d.keyword)
        assertTrue(d.requiresCode)
        assertEquals(Approval.SUMMARY_WITH_PIN, d.approval)
        assertEquals(CommandParameter.InsulinUnits(5, 5), d.parameter)
    }

    @Test
    fun `BOL-01 BOLUS 0,05 i 5,00 maja podsumowanie z PIN-em`() {
        assertEquals(Approval.SUMMARY_WITH_PIN, bolus(5).definition.approval)
        assertEquals(Approval.SUMMARY_WITH_PIN, bolus(500).definition.approval)
    }

    @Test
    fun `BOL-02 poprawne ilosci przy limicie 500`() {
        for (c in listOf(5, 10, 500)) assertTrue(Command.Bolus.of(c, limits()) is BolusValidation.Valid)
    }

    @Test
    fun `BOL-02 bledne ilosci przy limicie 500`() {
        val expected = listOf(
            0 to BolusAmountError.BELOW_MIN, 4 to BolusAmountError.BELOW_MIN, -5 to BolusAmountError.BELOW_MIN,
            505 to BolusAmountError.ABOVE_MAX, 501 to BolusAmountError.ABOVE_MAX, 12 to BolusAmountError.NOT_STEP,
        )
        for ((c, e) in expected) assertEquals(e, err(Command.Bolus.of(c, limits())))
    }

    @Test
    fun `BOL-02 limit 200 odrzuca 205 i skrajne Int bez wyjatku`() {
        assertEquals(BolusAmountError.ABOVE_MAX, err(Command.Bolus.of(205, limits(200))))
        assertEquals(BolusAmountError.BELOW_MIN, err(Command.Bolus.of(Int.MIN_VALUE, limits())))
        assertEquals(BolusAmountError.ABOVE_MAX, err(Command.Bolus.of(Int.MAX_VALUE, limits())))
    }

    @Test
    fun `BOL-03 parse z limitem 500 zgodnie z tabela`() {
        val ok = mapOf("0,1" to 10, "0.1" to 10, "0,05" to 5, "2" to 200, "2,50" to 250, "05" to 500, "5,00" to 500)
        for ((t, c) in ok) {
            assertEquals(bolus(c), (Command.Bolus.parse(t, limits()) as BolusValidation.Valid).command, t)
        }
        val bad = listOf(
            "" to BolusAmountError.EMPTY, "0" to BolusAmountError.BELOW_MIN, "0,04" to BolusAmountError.BELOW_MIN,
            "5,05" to BolusAmountError.ABOVE_MAX, "99" to BolusAmountError.ABOVE_MAX,
            "99999999999999999999" to BolusAmountError.ABOVE_MAX, "1,23" to BolusAmountError.NOT_STEP,
            "1,234" to BolusAmountError.TOO_MANY_DECIMALS, "1," to BolusAmountError.INVALID_CHARACTERS,
            ",5" to BolusAmountError.INVALID_CHARACTERS, "1,2,3" to BolusAmountError.INVALID_CHARACTERS,
            "1 ,5" to BolusAmountError.INVALID_CHARACTERS, " 1" to BolusAmountError.INVALID_CHARACTERS,
            "-1" to BolusAmountError.INVALID_CHARACTERS, "1,5j" to BolusAmountError.INVALID_CHARACTERS,
            "１" to BolusAmountError.INVALID_CHARACTERS,
        )
        for ((t, e) in bad) assertEquals(e, err(Command.Bolus.parse(t, limits())), t)
    }

    @Test
    fun `BOL-04 dodatkowe potwierdzenie powyzej polowy limitu`() {
        assertFalse(bolus(250, 500).requiresExtraConfirmation(limits(500)))
        assertTrue(bolus(255, 500).requiresExtraConfirmation(limits(500)))
        assertTrue(bolus(5, 5).requiresExtraConfirmation(limits(5)))
        assertFalse(bolus(500).requiresExtraConfirmation(limits(1000)))
        assertTrue(bolus(505).requiresExtraConfirmation(limits(1000)))
    }

    @Test
    fun `BOL-05 tresc SMS z kropka`() {
        val table = mapOf(
            10 to "BOLUS 0.1", 5 to "BOLUS 0.05", 125 to "BOLUS 1.25",
            250 to "BOLUS 2.5", 200 to "BOLUS 2", 1000 to "BOLUS 10",
        )
        for ((c, s) in table) assertEquals(s, bolus(c).smsText())
    }

    @Test
    fun `BOL-05 kazda poprawna ilosc pasuje do wzorca i wraca do wartosci`() {
        val re = Regex("""BOLUS (0|[1-9][0-9]?)(\.[0-9]?[1-9])?""")
        for (c in 5..1000 step 5) {
            val sms = bolus(c).smsText()
            assertTrue(re.matches(sms), sms)
            assertEquals(c, Math.round(sms.removePrefix("BOLUS ").toDouble() * 100).toInt())
        }
    }

    @Test
    fun `BOL-06 kontekst parsera i walidacja ilosci`() {
        for (c in 5..1000 step 5) assertEquals(ExpectedCommand.Bolus(c), bolus(c).toExpectedCommand())
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.Bolus(0) }
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.Bolus(-5) }
    }

    @Test
    fun `BOL-06 toString nie ujawnia ilosci`() {
        val l = limits(1000)
        val texts = listOf(
            bolus(235).toString(), Command.Bolus.of(235, l).toString(), Command.Bolus.parse("2,35", l).toString(),
            bolus(235).toExpectedCommand().toString(),
            runCatching { ExpectedCommand.Bolus(0) }.exceptionOrNull().toString(),
        )
        for (t in texts) for (s in listOf("235", "2,35", "2.35")) assertFalse(t.contains(s), t)
    }
}
