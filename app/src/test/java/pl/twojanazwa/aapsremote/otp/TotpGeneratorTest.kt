package pl.twojanazwa.aapsremote.otp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TotpGeneratorTest {

    private val secret = TotpSecret.fromBytes("12345678901234567890".toByteArray(Charsets.US_ASCII))

    @ParameterizedTest(name = "TOTP-01 kod dla {0} ms to {1}")
    @CsvSource(
        "59000,287082",
        "59999,287082",
        "1111111109000,081804",
        "1111111111000,050471",
        "1234567890000,005924",
        "2000000000000,279037",
        "20000000000000,353130",
    )
    fun `TOTP-01 kod zgodny z wektorami RFC 6238`(epochMillis: Long, expected: String) {
        val code = TotpGenerator.generate(secret, epochMillis)
        assertEquals(expected, code.value)
        assertEquals(6, code.value.length)
    }

    @Test
    fun `TOTP-01 zero wiodace zachowane dla 1111111109000 ms`() {
        assertEquals("081804", TotpGenerator.generate(secret, 1111111109000).value)
    }

    @Test
    fun `TOTP-01 kod dla 1234567890000 ms to 005924`() {
        assertEquals("005924", TotpGenerator.generate(secret, 1234567890000).value)
    }

    @Test
    fun `TOTP-01 kod dla 59000 i 59999 ms jest ten sam`() {
        assertEquals("287082", TotpGenerator.generate(secret, 59000).value)
        assertEquals("287082", TotpGenerator.generate(secret, 59999).value)
    }

    @Test
    fun `TOTP-01 kod dla 20000000000000 ms nie przepelnia licznika`() {
        assertEquals("353130", TotpGenerator.generate(secret, 20000000000000).value)
    }

    @Test
    fun `TOTP-03 okno dla 1111111109000 ms`() {
        val w = TotpGenerator.window(1111111109000)
        assertEquals(1111111080000, w.validFromEpochMillis)
        assertEquals(1111111110000, w.validUntilEpochMillis)
        assertEquals(1000, w.remainingMillis)
    }

    @Test
    fun `TOTP-03 poczatek kroku ma pozostalo 30000`() {
        assertEquals(30000, TotpGenerator.window(1111111110000).remainingMillis)
    }

    @Test
    fun `TOTP-03 ostatnia milisekunda kroku ma pozostalo 1`() {
        assertEquals(1, TotpGenerator.window(1111111109999).remainingMillis)
    }

    @Test
    fun `TOTP-03 okno dla chwili 0`() {
        val w = TotpGenerator.window(0)
        assertEquals(0, w.validFromEpochMillis)
        assertEquals(30000, w.validUntilEpochMillis)
        assertEquals(30000, w.remainingMillis)
    }

    @Test
    fun `TOTP-03 kod niesie okno waznosci`() {
        val code = TotpGenerator.generate(secret, 1111111109000)
        assertEquals(1111111080000, code.validFromEpochMillis)
        assertEquals(1111111110000, code.validUntilEpochMillis)
        assertEquals(1000, code.remainingMillis(1111111109000))
    }

    @Test
    fun `TOTP-04 chwila ujemna rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.generate(secret, -1) }
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.window(-1) }
    }

    @Test
    fun `TOTP-04 najwieksza dozwolona chwila dziala`() {
        val t = Long.MAX_VALUE - 30_000
        val w = TotpGenerator.window(t)
        TotpGenerator.generate(secret, t)
        assertTrue(w.validUntilEpochMillis > 0)
        assertTrue(w.validUntilEpochMillis > w.validFromEpochMillis)
    }

    @Test
    fun `TOTP-04 pierwsza niedozwolona chwila rzuca IllegalArgumentException`() {
        val t = Long.MAX_VALUE - 29_999
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.generate(secret, t) }
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.window(t) }
    }

    @Test
    fun `TOTP-04 Long MAX_VALUE rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.generate(secret, Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { TotpGenerator.window(Long.MAX_VALUE) }
    }
}
