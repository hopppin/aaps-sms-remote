package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SmsRedactionTest {

    @Test
    fun `SMS-16 toString numeru AAPS nie zawiera cyfr numeru`() {
        val number = AapsNumber.parse("+44 7700 900123")
        assertNotNull(number)
        val text = number.toString()
        assertFalse(text.contains("7700"))
        assertFalse(text.contains("900123"))
        assertFalse(text.contains("700900123"))
    }
}
