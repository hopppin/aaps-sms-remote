package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

class SecretRedactionTest {

    private val s1 = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val forbidden = listOf("12345678901234567890", "3132333435", "MTIzNDU2")

    private fun assertRedacted(text: String, record: String? = null) {
        forbidden.forEach { assertFalse(text.contains(it), "tekst zawiera zabroniony ciag") }
        if (record != null) {
            for (i in 0..record.length - 4) {
                assertFalse(text.contains(record.substring(i, i + 4)), "tekst zawiera fragment rekordu")
            }
        }
    }

    @Test
    fun `SECRET-15 SealedSecret toString nie zawiera bajtow ani fragmentu rekordu`() {
        val iv = ByteArray(12) { (0x40 + it).toByte() }
        val sealed = SealedSecret(SealedSecretFormat.VERSION_1, iv, s1 + ByteArray(16) { 0x5A })
        val text = sealed.toString()
        assertEquals("SealedSecret(***)", text)
        assertRedacted(text, SealedSecretFormat.encode(sealed))
    }

    @Test
    fun `SECRET-15 Loaded toString nie zawiera bajtow sekretu`() {
        val text = LoadResult.Loaded(TotpSecret.fromBytes(s1)).toString()
        assertEquals("Loaded(***)", text)
        assertRedacted(text)
    }

    @Test
    fun `SECRET-15 Failed STORAGE toString to Failed(STORAGE)`() {
        assertEquals("Failed(STORAGE)", SaveResult.Failed(SaveFailure.STORAGE).toString())
    }

    @Test
    fun `SECRET-15 komunikaty wyjatkow konstruktora SealedSecret sa stalymi tekstami`() {
        val badIv = assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(11) { 0x31 }, s1)
        }
        val badCt = assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(12) { 0x31 }, ByteArray(16) { 0x31 })
        }
        assertEquals("invalid iv length", badIv.message)
        assertEquals("invalid ciphertext length", badCt.message)
    }
}
