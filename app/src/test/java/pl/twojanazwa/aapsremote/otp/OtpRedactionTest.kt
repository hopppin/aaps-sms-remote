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

    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val p = "otpauth://totp/Przyklad:test?"

    private val badUris = listOf(
        "https://example.com/?secret=GEZDGNBV",
        "otpauth://hotp/Przyklad:test?secret=$rfcSecret&counter=0",
        "${p}issuer=Przyklad",
        "${p}secret=$rfcSecret&secret=$rfcSecret",
        "${p}secret=$rfcSecret&digits=6&digits=6",
        "${p}secret=GEZ1",
        "${p}secret=%ZZ",
        "${p}secret",
        "${p}secret=$rfcSecret&algorithm=SHA256",
        "${p}secret=$rfcSecret&algorithm=",
        "${p}secret=$rfcSecret&digits=8",
        "${p}secret=$rfcSecret&digits=06",
        "${p}secret=$rfcSecret&period=60",
        "${p}secret=$rfcSecret&period=-30",
        "${p}secret=====",
    )

    @Test
    fun `TOTP-09 toString bledu parsera nie zawiera sekretu ani wejscia`() {
        for (uri in badUris) {
            val result = OtpauthParser.parse(uri)
            assertTrue(result is OtpauthResult.Failure, "oczekiwano Failure")
            val text = result.toString()
            assertFalse(text.contains(rfcSecret), "sekret w toString")
            assertFalse(text.contains("GEZDGNBV"), "fragment sekretu w toString")
            assertFalse(text.contains(uri), "wejscie w toString")
            assertFalse(text.contains("Przyklad"), "etykieta w toString")
        }
    }

    @Test
    fun `TOTP-09 toString Success parsera nie ujawnia sekretu`() {
        val result = OtpauthParser.parse("${p}secret=$rfcSecret")
        assertTrue(result is OtpauthResult.Success)
        assertEquals("OtpauthResult.Success(***)", result.toString())
        assertEquals("TotpSecret(***)", (result as OtpauthResult.Success).secret.toString())
    }

    @Test
    fun `TOTP-09 parser nie rzuca wyjatkow dla zlych danych`() {
        val inputs = badUris + listOf("", "otpauth://", "otpauth://totp", "${p}secret=%", "${p}x=%C3%28&secret=$rfcSecret")
        for (uri in inputs) {
            OtpauthParser.parse(uri)
        }
    }
}
