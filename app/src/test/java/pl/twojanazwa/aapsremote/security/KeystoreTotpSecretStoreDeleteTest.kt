package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreDeleteTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private fun s1() = TotpSecret.fromBytes(s1Bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher = fakeCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-11 usuniecie zapisanego S1 czysci magazyn i klucz, stan ABSENT, load daje Absent`() = runTest {
        val s = store()
        s.save(s1())
        var clearsSeenByDeleteKey = -1
        fakeCipher.onDeleteKey = { clearsSeenByDeleteKey = storage.clearCount }
        assertEquals(DeleteResult.Deleted, s.delete())
        assertNull(storage.record)
        assertEquals(1, fakeCipher.deleteKeyCount)
        assertEquals(1, clearsSeenByDeleteKey) // rekord przed kluczem
        assertEquals(SecretState.ABSENT, s.state.value)
        assertEquals(LoadResult.Absent, s.load())
    }

    @Test
    fun `SECRET-11 pusty magazyn i brak klucza, dwa delete daja Deleted`() = runTest {
        val s = store(AesGcmSecretCipher(FakeSecretKeySource(hasKey = false), FakeKeystoreEnvironment()))
        assertEquals(DeleteResult.Deleted, s.delete())
        assertEquals(DeleteResult.Deleted, s.delete())
    }

    @Test
    fun `SECRET-11 usuniecie rekordu false daje Failed, klucz zostaje, load zwraca S1`() = runTest {
        val s = store()
        s.save(s1())
        storage.clearResult = false
        assertEquals(DeleteResult.Failed, s.delete())
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
        val loaded = s.load() as LoadResult.Loaded
        assertArrayEquals(s1Bytes, loaded.secret.copyBytes())
    }

    @Test
    fun `SECRET-11 wyjatek przy usuwaniu klucza w zrodle nie psuje wyniku`() = runTest {
        val keySource = FakeSecretKeySource()
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))
        s.save(s1())
        keySource.deleteKeyError = RuntimeException("test")
        assertEquals(DeleteResult.Deleted, s.delete())
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-11 wyjatek szyfratora przy usuwaniu klucza nie psuje wyniku`() = runTest {
        val s = store()
        s.save(s1())
        fakeCipher.deleteKeyError = RuntimeException("test")
        assertEquals(DeleteResult.Deleted, s.delete())
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-11 stan INVALIDATED i delete daje Deleted i stan ABSENT`() = runTest {
        val s = store()
        s.save(s1())
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        s.load()
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(DeleteResult.Deleted, s.delete())
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-11 wyjatek przy usuwaniu rekordu daje Failed, klucz zostaje, stan bez zmian`() = runTest {
        val s = store()
        s.save(s1())
        storage.clearError = RuntimeException("test")
        assertEquals(DeleteResult.Failed, s.delete())
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-11 anulowanie przy usuwaniu klucza zostawia stan ABSENT`() = runTest {
        val s = store()
        s.save(s1())
        fakeCipher.deleteKeyError = CancellationException("test")
        var thrown: Throwable? = null
        try {
            s.delete()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
        assertNull(storage.record)
        assertEquals(SecretState.ABSENT, s.state.value)
    }
}
