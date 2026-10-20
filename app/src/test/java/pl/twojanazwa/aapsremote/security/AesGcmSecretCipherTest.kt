package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

class AesGcmSecretCipherTest {

    private val s1 = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val source = FakeSecretKeySource()
    private val environment = FakeKeystoreEnvironment()
    private val cipher = AesGcmSecretCipher(source, environment)

    private fun encryptS1(): SealedSecret =
        (cipher.encrypt(s1) as EncryptResult.Success).sealed

    private fun decryptFailure(sealed: SealedSecret): CryptoFailure =
        (cipher.decrypt(sealed) as DecryptResult.Failure).reason

    @Test
    fun `SECRET-03 rekord S1 ma 49 bajtow i 68 znakow z wersja 1`() {
        val record = SealedSecretFormat.encode(encryptS1())
        val bytes = Base64.getDecoder().decode(record)
        assertEquals(49, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(68, record.length)
        assertTrue(Regex("^[A-Za-z0-9+/]+={0,2}$").matches(record))
    }

    @Test
    fun `SECRET-03 odszyfrowanie rekordu kluczem K daje bajty S1`() {
        val record = SealedSecretFormat.encode(encryptS1())
        val sealed = SealedSecretFormat.decode(record)!!
        val result = cipher.decrypt(sealed) as DecryptResult.Success
        assertArrayEquals(s1, result.plaintext)
    }

    @Test
    fun `SECRET-03 dwa zaszyfrowania daja rozne IV i rozne rekordy`() {
        val a = encryptS1()
        val b = encryptS1()
        assertFalse(a.iv.contentEquals(b.iv))
        assertNotEquals(SealedSecretFormat.encode(a), SealedSecretFormat.encode(b))
    }

    @Test
    fun `SECRET-03 zmiana jednego bitu w IV szyfrogramie lub tagu daje CORRUPTED`() {
        val sealed = encryptS1()
        val ctLast = sealed.ciphertext.size - 1
        val variants = listOf(
            "iv" to SealedSecret(sealed.version, sealed.iv.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, sealed.ciphertext),
            "szyfrogram" to SealedSecret(sealed.version, sealed.iv, sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }),
            "tag" to SealedSecret(sealed.version, sealed.iv, sealed.ciphertext.copyOf().also { it[ctLast] = (it[ctLast].toInt() xor 0x80).toByte() }),
        )
        for ((name, variant) in variants) {
            assertEquals(CryptoFailure.CORRUPTED, decryptFailure(variant), name)
        }
    }

    @Test
    fun `SECRET-03 zmieniony bajt wersji daje rekord nieprawidlowy przy decode`() {
        val bytes = Base64.getDecoder().decode(SealedSecretFormat.encode(encryptS1()))
        bytes[0] = 0x02
        assertNull(SealedSecretFormat.decode(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun `SECRET-03 pusty tekst jawny daje UNAVAILABLE bez wyjatku`() {
        assertEquals(EncryptResult.Failure(CryptoFailure.UNAVAILABLE), cipher.encrypt(ByteArray(0)))
    }

    @Test
    fun `SECRET-07 inny klucz daje CORRUPTED`() {
        val sealed = encryptS1()
        source.swapKey()
        assertEquals(CryptoFailure.CORRUPTED, decryptFailure(sealed))
    }

    @Test
    fun `SECRET-07 brak klucza przy odszyfrowaniu odblokowany daje KEY_INVALIDATED bez generacji`() {
        val sealed = encryptS1()
        source.currentKey = null
        assertEquals(CryptoFailure.KEY_INVALIDATED, decryptFailure(sealed))
        assertEquals(0, source.generateKeyCount)
    }

    @Test
    fun `SECRET-07 brak klucza przy odszyfrowaniu zablokowany daje LOCKED bez generacji`() {
        val sealed = encryptS1()
        source.currentKey = null
        environment.locked = true
        assertEquals(CryptoFailure.LOCKED, decryptFailure(sealed))
        assertEquals(0, source.generateKeyCount)
    }

    @Test
    fun `SECRET-07 klucz trwale uniewazniony z getKey daje KEY_INVALIDATED przy encrypt i decrypt`() {
        val sealed = encryptS1()
        source.getKeyError = TestKeyInvalidatedException()
        assertEquals(EncryptResult.Failure(CryptoFailure.KEY_INVALIDATED), cipher.encrypt(s1))
        assertEquals(CryptoFailure.KEY_INVALIDATED, decryptFailure(sealed))
    }

    @Test
    fun `SECRET-07 wyjatek getKey przy zablokowanym telefonie daje LOCKED`() {
        source.getKeyError = TestKeyInvalidatedException()
        environment.locked = true
        assertEquals(EncryptResult.Failure(CryptoFailure.LOCKED), cipher.encrypt(s1))
    }

    @Test
    fun `SECRET-01 encrypt przy braku klucza generuje go dokladnie raz`() {
        val noKey = FakeSecretKeySource(hasKey = false)
        val c = AesGcmSecretCipher(noKey, environment)
        val sealed = (c.encrypt(s1) as EncryptResult.Success).sealed
        assertEquals(1, noKey.generateKeyCount)
        assertArrayEquals(s1, (c.decrypt(sealed) as DecryptResult.Success).plaintext)
        assertEquals(1, noKey.generateKeyCount)
    }

    @Test
    fun `SECRET-01 encrypt przy istniejacym kluczu i decrypt nie generuja klucza`() {
        val sealed = encryptS1()
        cipher.decrypt(sealed)
        assertEquals(0, source.generateKeyCount)
    }

    @Test
    fun `SECRET-02 wyjatek generateKey daje KEY_GENERATION_FAILED`() {
        val noKey = FakeSecretKeySource(hasKey = false)
        noKey.generateKeyError = RuntimeException("test")
        noKey.getKeyError = null
        val c = AesGcmSecretCipher(noKey, environment)
        assertEquals(EncryptResult.Failure(CryptoFailure.KEY_GENERATION_FAILED), c.encrypt(s1))
    }

    @Test
    fun `SECRET-17 wyjatek getKey daje UNAVAILABLE przy encrypt i decrypt`() {
        val sealed = encryptS1()
        source.getKeyError = RuntimeException("test")
        assertEquals(EncryptResult.Failure(CryptoFailure.UNAVAILABLE), cipher.encrypt(s1))
        assertEquals(CryptoFailure.UNAVAILABLE, decryptFailure(sealed))
    }

    @Test
    fun `SECRET-17 wyjatek deleteKey zrodla daje false a sukces true`() {
        assertTrue(cipher.deleteKey())
        assertEquals(1, source.deleteKeyCount)
        source.deleteKeyError = RuntimeException("test")
        assertFalse(cipher.deleteKey())
    }

    @Test
    fun `SECRET-17 CancellationException jest przepuszczany`() {
        val sealed = encryptS1()
        source.getKeyError = CancellationException("test")
        assertThrows(CancellationException::class.java) { cipher.encrypt(s1) }
        assertThrows(CancellationException::class.java) { cipher.decrypt(sealed) }
        source.getKeyError = null
        source.deleteKeyError = CancellationException("test")
        assertThrows(CancellationException::class.java) { cipher.deleteKey() }
        val noKey = FakeSecretKeySource(hasKey = false)
        noKey.generateKeyError = CancellationException("test")
        assertThrows(CancellationException::class.java) { AesGcmSecretCipher(noKey, environment).encrypt(s1) }
    }

    @Test
    fun `SECRET-17 encrypt nie modyfikuje tablicy plaintext`() {
        val input = s1.copyOf()
        cipher.encrypt(input)
        assertArrayEquals(s1, input)
    }
}
