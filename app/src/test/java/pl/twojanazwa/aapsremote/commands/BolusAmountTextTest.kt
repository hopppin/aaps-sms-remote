package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BolusAmountTextTest {
    private fun value(text: String): Int = (BolusAmountText.parse(text) as BolusAmountText.Parsed.Value).centiUnits
    private fun error(text: String): BolusAmountError = (BolusAmountText.parse(text) as BolusAmountText.Parsed.Error).error

    @Test
    fun `BOL-03 wartosci w setnych`() {
        assertEquals(10, value("0,1"))
        assertEquals(10, value("0.1"))
        assertEquals(5, value("0,05"))
        assertEquals(200, value("2"))
        assertEquals(250, value("2,50"))
        assertEquals(500, value("05"))
        assertEquals(500, value("5,00"))
        assertEquals(0, value("0"))
        assertEquals(4, value("0,04"))
        assertEquals(505, value("5,05"))
        assertEquals(9900, value("99"))
        assertEquals(123, value("1,23"))
    }

    @Test
    fun `BOL-03 pusty i zla postac`() {
        assertEquals(BolusAmountError.EMPTY, error(""))
        for (t in listOf("1,", ",5", "1,2,3", "1 ,5", " 1", "-1", "1,5j", "１", "+1", "1..2", ".")) {
            assertEquals(BolusAmountError.INVALID_CHARACTERS, error(t), t)
        }
    }

    @Test
    fun `BOL-03 za duzo miejsc po przecinku ma pierwszenstwo przed zakresem`() {
        assertEquals(BolusAmountError.TOO_MANY_DECIMALS, error("1,234"))
        assertEquals(BolusAmountError.TOO_MANY_DECIMALS, error("99999,999"))
    }

    @Test
    fun `BOL-03 ponad 3 cyfry czesci calkowitej to powyzej maksimum bez przepelnienia`() {
        assertEquals(BolusAmountError.ABOVE_MAX, error("99999999999999999999"))
        assertEquals(BolusAmountError.ABOVE_MAX, error("1000"))
        assertEquals(99900, value("0999"))
    }

    @Test
    fun `BOL-03 zera wiodace przed czescia calkowita sa dozwolone (00,5 to 0,50)`() {
        assertEquals(50, value("00,5"))
    }

    @Test
    fun `BOL-03 1000,5 to powyzej maksimum (cztery cyfry czesci calkowitej)`() {
        assertEquals(BolusAmountError.ABOVE_MAX, error("1000,5"))
    }

    @Test
    fun `BOL-06 toString wartosci bez ilosci`() {
        assertFalse(BolusAmountText.parse("2,35").toString().contains("235"))
    }

    @Test
    fun `BOL-05 tresc SMS dla wierszy tabeli`() {
        assertEquals("0.1", BolusAmountText.toSms(10))
        assertEquals("0.05", BolusAmountText.toSms(5))
        assertEquals("1.25", BolusAmountText.toSms(125))
        assertEquals("2.5", BolusAmountText.toSms(250))
        assertEquals("2", BolusAmountText.toSms(200))
        assertEquals("10", BolusAmountText.toSms(1000))
    }

    @Test
    fun `BOL-05 kazda ilosc 5 do 1000 co 5 pasuje do wzorca i odwraca sie do ilosci`() {
        val pattern = Regex("""(0|[1-9][0-9]?)(\.[0-9]?[1-9])?""")
        for (c in 5..1000 step 5) {
            val sms = BolusAmountText.toSms(c)
            assertTrue(pattern.matches(sms), sms)
            assertEquals(c, value(sms))
        }
    }
}
