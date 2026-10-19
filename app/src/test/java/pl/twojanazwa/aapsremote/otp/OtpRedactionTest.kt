package pl.twojanazwa.aapsremote.otp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtpRedactionTest {

    private val secretBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun `TOTP-09 toString sekretu nie zawiera wartosci`() {
        val text = TotpSecret.fromBytes(secretBytes).toString().lowercase()
        assertFalse(text.contains("gezdgnbv"))
        assertFalse(text.contains("12345678901234567890"))
        for (b in secretBytes) {
            assertFalse(text.contains("%02x".format(b)), "bajt sekretu w postaci szesnastkowej")
        }
    }

    @Test
    fun `TOTP-09 toString kodu nie zawiera kodu`() {
        val code = TotpCode("287082", 0, 30000)
        assertFalse(code.toString().contains("287082"))
    }

    @Test
    fun `TOTP-09 wyjatek dla chwili spoza zakresu nie zawiera sekretu ani kodu`() {
        val secret = TotpSecret.fromBytes(secretBytes)
        val forbidden = listOf("12345678901234567890", "GEZDGNBV", "287082")
        val errors = listOf(
            assertThrows(IllegalArgumentException::class.java) { TotpGenerator.generate(secret, -1) },
            assertThrows(IllegalArgumentException::class.java) { TotpGenerator.window(-1) },
            assertThrows(IllegalArgumentException::class.java) { TotpGenerator.generate(secret, Long.MAX_VALUE) },
        )
        for (e in errors) {
            val message = e.message.orEmpty()
            forbidden.forEach { assertFalse(message.contains(it)) }
            assertNull(e.cause)
        }
    }

    @Test
    fun `TOTP-09 zerowanie tablicy wejsciowej nie zmienia kodu`() {
        val input = secretBytes.copyOf()
        val secret = TotpSecret.fromBytes(input)
        val before = TotpGenerator.generate(secret, 59000).value
        input.fill(0)
        assertEquals("287082", before)
        assertEquals(before, TotpGenerator.generate(secret, 59000).value)
    }

    @Test
    fun `TOTP-09 copyBytes zwraca nowa kopie`() {
        val secret = TotpSecret.fromBytes(secretBytes)
        val copy = secret.copyBytes()
        assertNotSame(copy, secret.copyBytes())
        copy.fill(0)
        assertTrue(secret.copyBytes().contentEquals(secretBytes))
    }

    @Test
    fun `TOTP-09 pusta tablica sekretu rzuca wyjatek bez danych`() {
        val e = assertThrows(IllegalArgumentException::class.java) { TotpSecret.fromBytes(ByteArray(0)) }
        assertFalse(e.message.orEmpty().contains("GEZD"))
    }

    @Test
    fun `TOTP-09 toString wyniku Base32 Success nie zawiera sekretu ani bajtow`() {
        val text = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ").toString()
        assertEquals("Base32Result.Success(***)", text)
        assertFalse(text.contains("GEZDGNBV"))
        assertFalse(text.contains("12345678901234567890"))
    }
}
