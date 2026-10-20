package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreLockedTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private fun s1() = TotpSecret.fromBytes(s1Bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()
    private val keySource = FakeSecretKeySource()
    private val environment = FakeKeystoreEnvironment()
    private val realCipher = AesGcmSecretCipher(keySource, environment)

    private fun TestScope.store(cipher: SecretCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-09 LOCKED przy odszyfrowaniu daje Locked, rekord i klucz nietkniete, stan PRESENT`() = runTest {
        val s = store(fakeCipher)
        s.save(s1())
        val before = storage.record
        fakeCipher.enqueueDecryptFailure(CryptoFailure.LOCKED)
        assertEquals(LoadResult.Locked, s.load())
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, fakeCipher.deleteKeyCount)
        assertEquals(SecretState.PRESENT, s.state.value)
    }

    @Test
    fun `SECRET-09 zablokowany telefon i wyjatek getKey daje Locked, po odblokowaniu load zwraca S1`() = runTest {
        val s = store(realCipher)
        s.save(s1())
        environment.locked = true
        keySource.getKeyError = IllegalStateException("test")
        assertEquals(LoadResult.Locked, s.load())
        environment.locked = false
        keySource.getKeyError = null
        val loaded = s.load() as LoadResult.Loaded
        assertArrayEquals(s1Bytes, loaded.secret.copyBytes())
    }

    @Test
    fun `SECRET-09 brak klucza przy zablokowanym telefonie daje Locked i nie usuwa rekordu`() = runTest {
        val s = store(realCipher)
        s.save(s1())
        val before = storage.record
        environment.locked = true
        keySource.currentKey = null
        assertEquals(LoadResult.Locked, s.load())
        assertEquals(before, storage.record)
        assertEquals(0, storage.clearCount)
        assertEquals(0, keySource.deleteKeyCount)
    }
}
