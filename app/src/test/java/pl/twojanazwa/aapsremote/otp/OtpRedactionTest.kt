package pl.twojanazwa.aapsremote.otp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OtpRedactionTest {

    @Test
    fun `TOTP-09 toString wyniku Base32 Success nie zawiera sekretu ani bajtow`() {
        val text = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ").toString()
        assertEquals("Base32Result.Success(***)", text)
        assertFalse(text.contains("GEZDGNBV"))
        assertFalse(text.contains("12345678901234567890"))
    }
}
