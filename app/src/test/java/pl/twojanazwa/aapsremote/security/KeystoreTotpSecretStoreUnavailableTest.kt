package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import java.security.KeyStoreException

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreUnavailableTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private fun s1() = TotpSecret.fromBytes(s1Bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-10 UNAVAILABLE trzy razy daje Unavailable, rekord i klucz nietkniete, stan PRESENT`() = runTest {
        val s = store(fakeCipher)
        s.save(s1())
        val before = storage.record
        repeat(3) {
            fakeCipher.enqueueDecryptFailure(CryptoFailure.UNAVAILABLE)
            assertEquals(LoadResult.Unavailable, s.load())
        }
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-10 KEY_GENERATION_FAILED przy odszyfrowaniu daje Unavailable i nic nie usuwa`() = runTest {
        val s = store(fakeCipher)
        s.save(s1())
        val before = storage.record
        fakeCipher.enqueueDecryptFailure(CryptoFailure.KEY_GENERATION_FAILED)
        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-10 wyjatek odczytu magazynu daje Unavailable bez wywolania szyfratora i bez usuwania`() = runTest {
        storage.readError = RuntimeException("test")
        val s = store(fakeCipher)
        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(0, fakeCipher.decryptCount)
        assertEquals(0, storage.clearCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.UNKNOWN, s.state.value)
    }

    @Test
    fun `SECRET-10 KeyStoreException z getKey przy odblokowanym telefonie daje Unavailable bez usuwania`() = runTest {
        val keySource = FakeSecretKeySource()
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment(locked = false)))
        s.save(s1())
        val before = storage.record
        keySource.getKeyError = KeyStoreException("test")
        assertEquals(LoadResult.Unavailable, s.load())
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, keySource.deleteKeyCount)
    }
}
