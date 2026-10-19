package pl.twojanazwa.aapsremote.otp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class Base32Test {

    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val rfcBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    private fun success(text: String): ByteArray =
        (Base32.decode(text) as Base32Result.Success).bytes

    @Test
    fun `TOTP-05 sekret RFC 6238 daje 20 bajtow ASCII`() {
        assertArrayEquals(rfcBytes, success(rfcSecret))
    }

    @Test
    fun `TOTP-05 male litery daja identyczny wynik`() {
        assertArrayEquals(rfcBytes, success(rfcSecret.lowercase()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["MZXW6===", "MZXW6"])
    fun `TOTP-05 wektor RFC 4648 foo z dopelnieniem i bez`(text: String) {
        assertArrayEquals("foo".toByteArray(Charsets.US_ASCII), success(text))
    }

    @ParameterizedTest
    @CsvSource("MY,f", "MY======,f", "MZXQ====,fo", "MZXW6YQ=,foob", "MZXW6YTB,fooba", "MZXW6YTBOI======,foobar")
    fun `TOTP-05 pozostale wektory RFC 4648`(text: String, expected: String) {
        assertArrayEquals(expected.toByteArray(Charsets.US_ASCII), success(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["GEZD GNBV", "GEZ1", "GE=ZD", "G", "GEZ0", "GEZ8", "GEZ9", "GE-ZD", "ĄBCDEFGH", "=ABCDEFG"])
    fun `TOTP-05 nieprawidlowy znak lub polozenie dopelnienia jest bledem`(text: String) {
        assertEquals(Base32Result.Invalid, Base32.decode(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ABC", "ABCDEF", "ABCDEFGHI", "ABCDEFGHIJK"])
    fun `TOTP-05 reszta dlugosci 1 3 lub 6 jest bledem`(text: String) {
        assertEquals(Base32Result.Invalid, Base32.decode(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "====", "="])
    fun `TOTP-05 pusty tekst lub same znaki dopelnienia daje pusty sekret`(text: String) {
        assertEquals(Base32Result.Empty, Base32.decode(text))
    }

    @Test
    fun `TOTP-05 bity dopelnienia ostatniego znaku sa ignorowane`() {
        assertArrayEquals(success("MY"), success("MZ"))
    }

    @Test
    fun `TOTP-09 wyniki bledow dekodera nie zawieraja fragmentow wejscia`() {
        val invalid = Base32.decode("GEZDGNBV1").toString()
        assertEquals("Invalid", invalid)
        assertFalse(invalid.contains("GEZD"))
        assertEquals("Empty", Base32.decode("====").toString())
    }
}
