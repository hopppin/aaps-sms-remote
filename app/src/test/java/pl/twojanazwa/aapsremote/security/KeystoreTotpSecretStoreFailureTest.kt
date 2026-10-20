package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

/**
 * SECRET-17: każda awaria jednego portu daje wynik zamiast wyjątku. W każdym teście awaria dotyczy
 * tylko jednego portu, reszta działa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreFailureTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private fun s1() = TotpSecret.fromBytes(s1Bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()

    private fun TestScope.store(cipher: SecretCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-17 getKey rzuca przy zapisie daje Failed CRYPTO bez wyjatku i bez zapisu`() = runTest {
        val keySource = FakeSecretKeySource().apply { getKeyError = RuntimeException("x") }
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))

        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), s.save(s1()))
        assertEquals(0, keySource.generateKeyCount)
        assertEquals(0, storage.writeCount)
        assertEquals(SecretState.UNKNOWN, s.state.value)
    }

    @Test
    fun `SECRET-17 generateKey rzuca przy braku klucza daje Failed KEY_GENERATION`() = runTest {
        val keySource = FakeSecretKeySource(hasKey = false).apply { generateKeyError = RuntimeException("x") }
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))

        assertEquals(SaveResult.Failed(SaveFailure.KEY_GENERATION), s.save(s1()))
        assertEquals(1, keySource.generateKeyCount)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `SECRET-17 Cipher odrzuca klucz o zlej dlugosci przy zapisie daje Failed CRYPTO`() = runTest {
        // AES nie przyjmuje klucza 5-bajtowego: Cipher.init rzuca InvalidKeyException wewnątrz szyfratora.
        val keySource = FakeSecretKeySource().apply { currentKey = SecretKeySpec(ByteArray(5), "AES") }
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))

        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), s.save(s1()))
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `SECRET-17 magazyn rzucajacy w zapisie po udanym szyfrowaniu daje Failed STORAGE bez wyjatku`() = runTest {
        storage.writeError = RuntimeException("x")
        val s = store(FakeSecretCipher())
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(s1()))
    }

    @Test
    fun `SECRET-17 odczyt magazynu rzucajacy w load daje Unavailable i nic nie usuwa`() = runTest {
        val s = store(FakeSecretCipher())
        assertEquals(SaveResult.Saved, s.save(s1()))
        storage.readError = RuntimeException("x")

        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(0, storage.clearCount)
        assertTrue(storage.record != null, "rekord zostal usuniety")
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-17 getKey rzuca przy odszyfrowaniu daje Unavailable i nie usuwa rekordu ani klucza`() = runTest {
        val keySource = FakeSecretKeySource()
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))
        assertEquals(SaveResult.Saved, s.save(s1()))
        keySource.getKeyError = RuntimeException("x")

        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(0, storage.clearCount)
        assertTrue(storage.record != null, "rekord zostal usuniety")
        assertEquals(0, keySource.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-17 magazyn rzucajacy w delete daje Failed i nie usuwa klucza`() = runTest {
        val cipher = FakeSecretCipher()
        val s = store(cipher)
        assertEquals(SaveResult.Saved, s.save(s1()))
        storage.clearError = RuntimeException("x")

        assertEquals(DeleteResult.Failed, s.delete())
        assertEquals(0, cipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-17 deleteKey rzuca po usunieciu rekordu daje Deleted`() = runTest {
        val cipher = FakeSecretCipher().apply { deleteKeyError = RuntimeException("x") }
        val s = store(cipher)
        assertEquals(SaveResult.Saved, s.save(s1()))

        assertEquals(DeleteResult.Deleted, s.delete())
        assertEquals(1, cipher.deleteKeyCount)
        assertEquals(SecretState.ABSENT, s.state.value)
        assertEquals(null, storage.record)
    }

    @Test
    fun `SECRET-17 odczyt magazynu rzucajacy w refreshState nie zmienia stanu`() = runTest {
        val s = store(FakeSecretCipher())
        storage.readError = RuntimeException("x")

        assertEquals(SecretState.UNKNOWN, s.refreshState())
        assertEquals(SecretState.UNKNOWN, s.state.value)
    }

    @Test
    fun `SECRET-17 CancellationException z zapisu jest przepuszczana a kopia bajtow wyzerowana`() = runTest {
        val cipher = FakeSecretCipher()
        storage.writeError = CancellationException("x")
        val s = store(cipher)
        try {
            s.save(s1())
            fail("oczekiwano CancellationException")
        } catch (_: CancellationException) {
            // oczekiwane
        }
        assertTrue(cipher.encryptInputs.single().all { it == 0.toByte() })
    }

    @Test
    fun `SECRET-17 szyfrator rzucajacy RuntimeException w encrypt daje Failed CRYPTO, nic nie zapisane, kopia wyzerowana`() = runTest {
        val cipher = FakeSecretCipher().apply { encryptError = RuntimeException("x") }
        val s = store(cipher)
        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), s.save(s1()))
        assertEquals(0, storage.writeCount)
        assertTrue(cipher.encryptInputs.single().all { it == 0.toByte() })
    }

    @Test
    fun `SECRET-17 szyfrator rzucajacy RuntimeException w decrypt daje Unavailable bez usuwania i bez zmiany stanu`() = runTest {
        val cipher = FakeSecretCipher()
        val s = store(cipher)
        s.save(s1())
        val before = storage.record
        cipher.decryptError = RuntimeException("x")
        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, cipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-17 szyfrator rzucajacy RuntimeException w deleteKey nie psuje delete ani wyczyszczenia`() = runTest {
        val cipher = FakeSecretCipher().apply { deleteKeyError = RuntimeException("x") }
        val s = store(cipher)
        s.save(s1())
        assertEquals(DeleteResult.Deleted, s.delete())
        s.save(s1())
        cipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        assertEquals(LoadResult.Invalidated, s.load())
    }

    @Test
    fun `SECRET-17 CancellationException z szyfratora jest przepuszczana`() = runTest {
        val cipher = FakeSecretCipher().apply { encryptError = CancellationException("x") }
        val s = store(cipher)
        try {
            s.save(s1())
            fail("oczekiwano CancellationException")
        } catch (_: CancellationException) {
            // oczekiwane
        }
        assertTrue(cipher.encryptInputs.single().all { it == 0.toByte() })
    }

    @Test
    fun `SECRET-17 CancellationException z odczytu load jest przepuszczana`() = runTest {
        storage.readError = CancellationException("x")
        val s = store(FakeSecretCipher())
        try {
            s.load()
            fail("oczekiwano CancellationException")
        } catch (_: CancellationException) {
            // oczekiwane
        }
    }
}
