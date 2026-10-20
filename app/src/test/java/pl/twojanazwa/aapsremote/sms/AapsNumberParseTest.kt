package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class AapsNumberParseTest {

    @ParameterizedTest(name = "SMS-01 numer {0} ma adres {1}")
    @CsvSource(
        "'+44 7700 900123', '+447700900123'",
        "'+44-7700-900-123', '+447700900123'",
        "'+44 (0)7700 900123', '+4407700900123'",
        "'07700900123', '07700900123'",
        "'700900123', '700900123'",
    )
    fun `SMS-01 poprawny numer ma adres docelowy`(raw: String, destination: String) {
        val number = AapsNumber.parse(raw)
        assertNotNull(number)
        assertEquals(destination, number!!.destination)
    }

    @ParameterizedTest(name = "SMS-01 numer {0} ma koncowke 700900123")
    @ValueSource(strings = ["+44 7700 900123", "+44 (0)7700 900123", "07700900123", "700900123"])
    fun `SMS-01 poprawny numer ma koncowke 9 cyfr`(raw: String) {
        val number = AapsNumber.parse(raw)!!
        assertEquals(true, number.matchesSender("700900123"))
        assertEquals(false, number.matchesSender("700900124"))
    }

    @ParameterizedTest(name = "SMS-01 numer {0} to nieprawidlowa konfiguracja")
    @ValueSource(strings = ["70090012", "", "   ", "+44 7700 9OO123", "+44.7700.900123", "44+7700900123"])
    fun `SMS-01 nieprawidlowy numer daje null`(raw: String) {
        assertNull(AapsNumber.parse(raw))
    }

    @ParameterizedTest
    @NullSource
    fun `SMS-01 null daje null`(raw: String?) {
        assertNull(AapsNumber.parse(raw))
    }

    @Test
    fun `SMS-01 parse nie rzuca dla dziwnych znakow`() {
        assertNull(AapsNumber.parse("\u0000‮+44"))
    }
}
