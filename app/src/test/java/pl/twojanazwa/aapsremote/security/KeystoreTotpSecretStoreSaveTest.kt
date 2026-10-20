package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpGenerator
import pl.twojanazwa.aapsremote.otp.TotpSecret
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreSaveTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val s2Bytes = ByteArray(20) { it.toByte() }
    private fun secret(bytes: ByteArray) = TotpSecret.fromBytes(bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val keySource = FakeSecretKeySource(hasKey = false)
    private val realCipher = AesGcmSecretCipher(keySource, FakeKeystoreEnvironment())
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher = realCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    private fun code(s: TotpSecret) = TotpGenerator.generate(s, 59000).value

    private fun assertInputZeroed() {
        assertTrue(fakeCipher.encryptInputs.single().all { it == 0.toByte() })
    }

    @Test
    fun `SECRET-01 brak klucza zapis S1 generuje klucz dokladnie raz`() = runTest {
        assertEquals(SaveResult.Saved, store().save(secret(s1Bytes)))
        assertEquals(1, keySource.generateKeyCount)
    }

    @Test
    fun `SECRET-01 istniejacy klucz zapis S2 nie generuje klucza`() = runTest {
        keySource.currentKey = FakeSecretKeySource.keyK()
        assertEquals(SaveResult.Saved, store().save(secret(s2Bytes)))
        assertEquals(0, keySource.generateKeyCount)
    }

    @Test
    fun `SECRET-01 brak klucza i istniejacy rekord load nie generuje klucza`() = runTest {
        val s = store()
        keySource.currentKey = FakeSecretKeySource.keyK()
        s.save(secret(s1Bytes))
        keySource.currentKey = null
        s.load()
        assertEquals(0, keySource.generateKeyCount)
    }

    @Test
    fun `SECRET-04 pusty magazyn zapis S1 daje Saved, jeden rekord, PRESENT i odczyt kodu 287082`() = runTest {
        val s = store()
        assertEquals(SecretState.UNKNOWN, s.state.value)
        assertEquals(SaveResult.Saved, s.save(secret(s1Bytes)))
        assertEquals(1, storage.writeCount)
        assertNotNull(storage.record)
        assertEquals(SecretState.PRESENT, s.state.value)
        val loaded = s.load() as LoadResult.Loaded
        assertEquals("287082", code(loaded.secret))
    }

    @Test
    fun `SECRET-04 kopia bajtow jest zerowana po sukcesie`() = runTest {
        assertEquals(SaveResult.Saved, store(fakeCipher).save(secret(s1Bytes)))
        assertInputZeroed()
        assertEquals(s1Bytes.toList(), fakeCipher.encryptInputSnapshots.single().toList())
    }

    @Test
    fun `SECRET-04 kopia bajtow jest zerowana przy Failed CRYPTO`() = runTest {
        fakeCipher.enqueueEncryptFailure(CryptoFailure.UNAVAILABLE)
        store(fakeCipher).save(secret(s1Bytes))
        assertInputZeroed()
    }

    @Test
    fun `SECRET-04 kopia bajtow jest zerowana przy Failed STORAGE`() = runTest {
        storage.writeResult = false
        store(fakeCipher).save(secret(s1Bytes))
        assertInputZeroed()
    }

    @Test
    fun `SECRET-04 kopia bajtow jest zerowana przy Locked`() = runTest {
        fakeCipher.enqueueEncryptFailure(CryptoFailure.LOCKED)
        store(fakeCipher).save(secret(s1Bytes))
        assertInputZeroed()
    }

    @Test
    fun `SECRET-04 kopia bajtow jest zerowana przy anulowaniu w trakcie zapisu rekordu`() = runTest {
        storage.writeError = CancellationException("test")
        val s = store(fakeCipher)
        val job = launch { s.save(secret(s1Bytes)) }
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertInputZeroed()
    }

    @Test
    fun `SECRET-04 anulowanie korutyny przy zapisie zachowuje stan PRESENT po udanym write`() = runTest {
        val s = store(fakeCipher)
        lateinit var job: Job
        storage.onWrite = { job.cancel() }
        job = launch { s.save(secret(s1Bytes)) }
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertEquals(SecretState.PRESENT, s.state.value)
        assertInputZeroed()
    }

    @Test
    fun `SECRET-04 TotpSecret po zapisie daje ten sam kod`() = runTest {
        val s1 = secret(s1Bytes)
        val before = code(s1)
        store().save(s1)
        assertEquals("287082", before)
        assertEquals(before, code(s1))
    }

    @Test
    fun `SECRET-04 Locked nie zapisuje, nie usuwa klucza i nie zmienia stanu`() = runTest {
        fakeCipher.enqueueEncryptFailure(CryptoFailure.LOCKED)
        val s = store(fakeCipher)
        assertEquals(SaveResult.Locked, s.save(secret(s1Bytes)))
        assertEquals(0, storage.writeCount)
        assertEquals(0, storage.clearCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.UNKNOWN, s.state.value)
    }

    @Test
    fun `SECRET-04 SECRET-02 blad generacji klucza daje Failed KEY_GENERATION bez zapisu`() = runTest {
        keySource.generateKeyError = IllegalStateException("test")
        assertEquals(SaveResult.Failed(SaveFailure.KEY_GENERATION), store().save(secret(s1Bytes)))
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `SECRET-04 UNAVAILABLE daje Failed CRYPTO bez zapisu i bez usuwania klucza`() = runTest {
        fakeCipher.enqueueEncryptFailure(CryptoFailure.UNAVAILABLE)
        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), store(fakeCipher).save(secret(s1Bytes)))
        assertEquals(0, storage.writeCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(0, storage.clearCount)
    }

    @Test
    fun `SECRET-04 zapis zwracajacy false daje Failed STORAGE, klucz nie jest usuwany, stan wg magazynu`() = runTest {
        storage.writeResult = false
        val s = store(fakeCipher)
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s1Bytes)))
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-04 zapis zmieniajacy wartosc i zwracajacy false daje stan PRESENT`() = runTest {
        val s = store(fakeCipher)
        assertEquals(SaveResult.Saved, s.save(secret(s1Bytes)))
        storage.writeResult = false
        storage.writeChangesValueButReturnsFalse = true
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-04 wyjatki zapisu daja Failed STORAGE`() = runTest {
        val errors = listOf<Exception>(
            IllegalStateException("test"),
            RuntimeException(IOException("test")),
            RuntimeException("test"),
        )
        for (error in errors) {
            storage.writeError = error
            val s = store(fakeCipher)
            assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s1Bytes)))
            assertEquals(0, fakeCipher.deleteKeyCount)
        }
    }

    @Test
    fun `SECRET-04 SECRET-17 CancellationException z zapisu jest przepuszczany`() = runTest {
        storage.writeError = CancellationException("test")
        val s = store(fakeCipher)
        var thrown: Throwable? = null
        launch {
            try {
                s.save(secret(s1Bytes))
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        advanceUntilIdle()
        assertTrue(thrown is CancellationException)
    }
}
