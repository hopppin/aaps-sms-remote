package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class AapsNumberSenderMatchTest {

    private val number = AapsNumber.parse("+44 7700 900123")!!

    @ParameterizedTest(name = "SMS-02 nadawca {0} pasuje")
    @ValueSource(
        strings = ["+447700900123", "07700900123", "00447700900123", "700900123", "+44 7700 900123", "+17700900123"],
    )
    fun `SMS-02 nadawca z ta sama koncowka pasuje`(sender: String) {
        assertTrue(number.matchesSender(sender))
    }

    @ParameterizedTest(name = "SMS-02 nadawca {0} nie pasuje")
    @ValueSource(strings = ["+447700900124", "+447700900321", "70090012", "AAPS", "Info700900123"])
    fun `SMS-02 inny nadawca nie pasuje`(sender: String) {
        assertFalse(number.matchesSender(sender))
    }

    @ParameterizedTest(name = "SMS-02 pusty lub null nadawca nie pasuje")
    @NullAndEmptySource
    fun `SMS-02 pusty i null nadawca nie pasuja`(sender: String?) {
        assertFalse(number.matchesSender(sender))
    }

    @Test
    fun `SMS-02 nieprawidlowy numer z ustawien nie pasuje do zadnego nadawcy`() {
        assertFalse(AapsNumber.parse("70090012")?.matchesSender("70090012") ?: false)
    }
}
