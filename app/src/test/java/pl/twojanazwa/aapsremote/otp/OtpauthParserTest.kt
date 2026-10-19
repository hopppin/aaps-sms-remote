package pl.twojanazwa.aapsremote.otp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OtpauthParserTest {

    private val rfc = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val rfcBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val base = "otpauth://totp/Przyklad:test?"

    private fun bytes(uri: String): ByteArray {
        val result = OtpauthParser.parse(uri)
        assertTrue(result is OtpauthResult.Success, "oczekiwano Success")
        return (result as OtpauthResult.Success).secret.copyBytes()
    }

    private fun assertError(expected: OtpauthError, uri: String) {
        assertEquals(OtpauthResult.Failure(expected), OtpauthParser.parse(uri))
    }

    // TOTP-06

    @Test
    fun `TOTP-06 poprawny URI daje sekret RFC`() {
        assertArrayEquals(rfcBytes, bytes("${base}secret=$rfc&issuer=Przyklad"))
    }

    @Test
    fun `TOTP-06 wielkie litery spacja i nowa linia daja ten sam wynik`() {
        val uri = "  OTPAUTH://TOTP/Przyklad:test?secret=$rfc&issuer=Przyklad \n"
        assertArrayEquals(rfcBytes, bytes(uri))
    }

    @Test
    fun `TOTP-06 kodowanie procentowe w wartosci jest dekodowane`() {
        assertArrayEquals("foo".toByteArray(Charsets.US_ASCII), bytes("${base}secret=MZXW6%3D%3D%3D"))
    }

    @Test
    fun `TOTP-06 inny schemat to NOT_OTPAUTH_URI`() {
        assertError(OtpauthError.NOT_OTPAUTH_URI, "https://example.com/?secret=GEZDGNBV")
    }

    @Test
    fun `TOTP-06 typ hotp to UNSUPPORTED_TYPE`() {
        assertError(OtpauthError.UNSUPPORTED_TYPE, "otpauth://hotp/Przyklad:test?secret=$rfc&counter=0")
    }

    @Test
    fun `TOTP-06 brak secret to MISSING_SECRET`() {
        assertError(OtpauthError.MISSING_SECRET, "${base}issuer=Przyklad")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "secret=GEZDGNBV&secret=GEZDGNBV",
            "secret=GEZDGNBV&algorithm=SHA1&algorithm=SHA1",
            "secret=GEZDGNBV&digits=6&digits=6",
            "secret=GEZDGNBV&period=30&period=30",
        ],
    )
    fun `TOTP-06 zduplikowany parametr to DUPLICATE_PARAMETER`(query: String) {
        assertError(OtpauthError.DUPLICATE_PARAMETER, base + query)
    }

    @Test
    fun `TOTP-06 niepoprawny Base32 to INVALID_BASE32`() {
        assertError(OtpauthError.INVALID_BASE32, "${base}secret=GEZ1")
    }

    @ParameterizedTest
    @ValueSource(strings = ["secret=%ZZ", "secret", "secret=GEZDGNBV&x=%C3", "secret=GEZDGNBV&x=%4", "secret=M%５AXW6", "secret=M%5ＡXW6"])
    fun `TOTP-06 zle kodowanie lub parametr bez rownosci to MALFORMED_URI`(query: String) {
        assertError(OtpauthError.MALFORMED_URI, base + query)
    }

    @Test
    fun `TOTP-06 nieznane parametry sa pomijane`() {
        val uri = "${base}secret=$rfc&image=https%3A%2F%2Fexample.com%2Flogo.png&nieznany=1"
        assertArrayEquals(rfcBytes, bytes(uri))
    }

    // TOTP-07

    @Test
    fun `TOTP-07 wartosci domyslne gdy brak algorithm digits period`() {
        assertArrayEquals(rfcBytes, bytes("${base}secret=$rfc"))
    }

    @Test
    fun `TOTP-07 jawne SHA1 6 30 jest przyjete`() {
        assertArrayEquals(rfcBytes, bytes("${base}secret=$rfc&algorithm=SHA1&digits=6&period=30"))
    }

    @Test
    fun `TOTP-07 pelny URI AAPS daje sekret RFC`() {
        val uri = "otpauth://totp/AAPS?secret=$rfc&issuer=AAPS&algorithm=SHA1&digits=6&period=30"
        assertArrayEquals(rfcBytes, bytes(uri))
    }

    @Test
    fun `TOTP-07 algorithm bez wzgledu na wielkosc liter`() {
        assertArrayEquals(rfcBytes, bytes("${base}secret=$rfc&algorithm=sha1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["SHA256", "SHA512", ""])
    fun `TOTP-07 inny algorithm to UNSUPPORTED_ALGORITHM`(value: String) {
        assertError(OtpauthError.UNSUPPORTED_ALGORITHM, "${base}secret=$rfc&algorithm=$value")
    }

    @ParameterizedTest
    @ValueSource(strings = ["8", "06", "", "abc"])
    fun `TOTP-07 inne digits to UNSUPPORTED_DIGITS`(value: String) {
        assertError(OtpauthError.UNSUPPORTED_DIGITS, "${base}secret=$rfc&digits=$value")
    }

    @ParameterizedTest
    @ValueSource(strings = ["60", "030", "", "-30"])
    fun `TOTP-07 inny period to UNSUPPORTED_PERIOD`(value: String) {
        assertError(OtpauthError.UNSUPPORTED_PERIOD, "${base}secret=$rfc&period=$value")
    }

    // TOTP-08

    @Test
    fun `TOTP-08 sekret 20 bajtow`() {
        assertArrayEquals(rfcBytes, bytes("${base}secret=$rfc"))
    }

    @Test
    fun `TOTP-08 sekret 16 bajtow z dopelnieniem`() {
        val expected = "1234567890123456".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(expected, bytes("${base}secret=GEZDGNBVGY3TQOJQGEZDGNBVGY======"))
    }

    @Test
    fun `TOTP-08 sekret 3 bajty`() {
        assertArrayEquals("foo".toByteArray(Charsets.US_ASCII), bytes("${base}secret=MZXW6"))
    }

    @Test
    fun `TOTP-08 sekret 1 bajt`() {
        assertArrayEquals("f".toByteArray(Charsets.US_ASCII), bytes("${base}secret=MY"))
    }

    @Test
    fun `TOTP-08 sama sekwencja dopelnienia to EMPTY_SECRET`() {
        assertError(OtpauthError.EMPTY_SECRET, "${base}secret=====")
    }
}
