package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Base64

class SealedSecretFormatTest {

    private fun record(version: Int, ivLen: Int, ctLen: Int): String = base64(version, ivLen, ctLen)

    @Test
    fun `SECRET-03 rekord 30 bajtow daje prawidlowy SealedSecret`() {
        val sealed = SealedSecretFormat.decode(record(1, 12, 17))
        assertNotNull(sealed)
        assertEquals(SealedSecretFormat.VERSION_1, sealed!!.version)
        assertArrayEquals(ByteArray(12), sealed.iv)
        assertArrayEquals(ByteArray(17) { 0xAA.toByte() }, sealed.ciphertext)
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRecords")
    fun `SECRET-03 rekord nieprawidlowy daje null bez wyjatku`(
        @Suppress("UNUSED_PARAMETER") name: String,
        record: String,
    ) {
        assertNull(SealedSecretFormat.decode(record))
    }

    @Test
    fun `SECRET-03 encode daje Base64 standardowy a decode odtwarza rekord`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = ByteArray(20) { (it + 100).toByte() }
        val encoded = SealedSecretFormat.encode(SealedSecret(SealedSecretFormat.VERSION_1, iv, ct))
        assertTrue(Regex("^[A-Za-z0-9+/]+={0,2}$").matches(encoded))
        val decoded = SealedSecretFormat.decode(encoded)!!
        assertEquals(SealedSecretFormat.VERSION_1, decoded.version)
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ct, decoded.ciphertext)
    }

    @Test
    fun `SECRET-03 encode uzywa dopelnienia znakiem rownosci`() {
        val encoded = SealedSecretFormat.encode(
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(12), ByteArray(18)),
        )
        assertTrue(encoded.endsWith("="))
    }

    @Test
    fun `SECRET-03 rekord bez dopelnienia Base64 jest nieprawidlowy`() {
        // 31 bajtow (nie 30): 30 dzieli sie przez 3, wiec Base64 nie ma dopelnienia. 31 daje "==".
        val withPadding = Base64.getEncoder().encodeToString(
            ByteArray(1 + 12 + 18).also { bytes ->
                bytes[0] = 0x01
                for (i in 13 until bytes.size) bytes[i] = 0xAA.toByte()
            },
        )
        assertTrue(withPadding.endsWith("="))
        assertNotNull(SealedSecretFormat.decode(withPadding))
        assertNull(SealedSecretFormat.decode(withPadding.trimEnd('=')))
    }

    @Test
    fun `SECRET-03 aad wersji 1 to jeden bajt 0x01`() {
        assertArrayEquals(byteArrayOf(0x01), SealedSecretFormat.aad(0x01))
    }

    @Test
    fun `SECRET-03 konstruktor odrzuca IV inny niz 12 bajtow`() {
        assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(11), ByteArray(17))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(13), ByteArray(17))
        }
    }

    @Test
    fun `SECRET-03 konstruktor odrzuca wersje inna niz 0x01`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(2, ByteArray(12), ByteArray(17))
        }
        assertEquals("invalid version", ex.message)
    }

    @Test
    fun `SECRET-03 konstruktor odrzuca szyfrogram krotszy niz 17 bajtow`() {
        assertThrows(IllegalArgumentException::class.java) {
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(12), ByteArray(16))
        }
    }

    companion object {
        private fun base64(version: Int, ivLen: Int, ctLen: Int): String {
            val bytes = ByteArray(1 + ivLen + ctLen)
            bytes[0] = version.toByte()
            for (i in 0 until ctLen) bytes[1 + ivLen + i] = 0xAA.toByte()
            return Base64.getEncoder().encodeToString(bytes)
        }

        @JvmStatic
        fun invalidRecords(): List<Array<Any>> = listOf(
            arrayOf("pusty", ""),
            arrayOf("nie Base64", "@@@@"),
            arrayOf("29 bajtow", base64(1, 12, 16)),
            arrayOf("wersja 0x02", base64(2, 12, 17)),
            arrayOf("wersja 0x00", base64(0, 12, 17)),
        )
    }
}
