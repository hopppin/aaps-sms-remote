package pl.twojanazwa.aapsremote.security

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreStateTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val s2Bytes = ByteArray(20) { it.toByte() }
    private fun secret(bytes: ByteArray) = TotpSecret.fromBytes(bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher = fakeCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-12 nowy magazyn ma stan UNKNOWN i nie wywoluje portow`() = runTest {
        val keySource = FakeSecretKeySource()
        val cipher = AesGcmSecretCipher(keySource, FakeKeystoreEnvironment())
        val s = store(cipher)
        assertEquals(SecretState.UNKNOWN, s.state.value)
        assertEquals(0, keySource.getKeyCount)
        assertEquals(0, keySource.generateKeyCount)
        assertEquals(0, keySource.deleteKeyCount)
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.writeCount)
        assertEquals(0, storage.clearCount)
    }

    @Test
    fun `SECRET-12 rekord nieprawidlowy daje PRESENT bez wywolania szyfratora`() = runTest {
        storage.record = "@@@@"
        val s = store()
        assertEquals(SecretState.PRESENT, s.refreshState())
        assertEquals(SecretState.PRESENT, s.state.value)
        assertEquals(0, fakeCipher.decryptCount)
        assertEquals(0, storage.clearCount)
    }

    @Test
    fun `SECRET-12 pusty magazyn daje ABSENT`() = runTest {
        assertEquals(SecretState.ABSENT, store().refreshState())
    }

    @Test
    fun `SECRET-12 stan INVALIDATED i pusty magazyn daje INVALIDATED`() = runTest {
        val s = store()
        s.save(secret(s1Bytes))
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        s.load()
        assertEquals(SecretState.INVALIDATED, s.refreshState())
    }

    @Test
    fun `SECRET-12 wyjatek odczytu zostawia stan PRESENT bez wyjatku`() = runTest {
        val s = store()
        s.save(secret(s1Bytes))
        storage.readError = RuntimeException("test")
        assertEquals(SecretState.PRESENT, s.refreshState())
    }

    @Test
    fun `SECRET-12 kolektor stanu dostaje sekwencje UNKNOWN ABSENT PRESENT INVALIDATED PRESENT ABSENT`() = runTest {
        val s = store()
        s.state.test {
            assertEquals(SecretState.UNKNOWN, awaitItem())
            s.refreshState()
            assertEquals(SecretState.ABSENT, awaitItem())
            s.save(secret(s1Bytes))
            assertEquals(SecretState.PRESENT, awaitItem())
            fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
            s.load()
            assertEquals(SecretState.INVALIDATED, awaitItem())
            s.save(secret(s2Bytes))
            assertEquals(SecretState.PRESENT, awaitItem())
            s.delete()
            assertEquals(SecretState.ABSENT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SECRET-12 nowy obiekt magazynu przy pustym magazynie daje ABSENT (INVALIDATED tylko w pamieci)`() = runTest {
        val first = store()
        first.save(secret(s1Bytes))
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        first.load()
        assertEquals(SecretState.INVALIDATED, first.state.value)
        val restarted = store()
        assertEquals(SecretState.ABSENT, restarted.refreshState())
    }
}
