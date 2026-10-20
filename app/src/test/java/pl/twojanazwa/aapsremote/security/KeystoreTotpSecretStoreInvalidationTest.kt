package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreInvalidationTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val s2Bytes = ByteArray(20) { it.toByte() }
    private fun secret(bytes: ByteArray) = TotpSecret.fromBytes(bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher = fakeCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    private suspend fun KeystoreTotpSecretStore.saveS1() {
        assertEquals(SaveResult.Saved, save(secret(s1Bytes)))
    }

    private fun assertWiped() {
        assertNull(storage.record)
        assertEquals(1, storage.clearCount)
        assertEquals(1, fakeCipher.deleteKeyCount)
    }

    @Test
    fun `SECRET-08 rekord nieprawidlowy daje Invalidated bez odszyfrowania, rekord potem klucz usuniete`() = runTest {
        storage.record = "@@@@"
        var clearsSeenByDeleteKey = -1
        fakeCipher.onDeleteKey = { clearsSeenByDeleteKey = storage.clearCount }
        val s = store()
        assertEquals(LoadResult.Invalidated, s.load())
        assertEquals(0, fakeCipher.decryptCount)
        assertWiped()
        assertEquals(1, clearsSeenByDeleteKey)
        assertEquals(SecretState.INVALIDATED, s.state.value)
    }

    @Test
    fun `SECRET-08 KEY_INVALIDATED przy odszyfrowaniu czysci i nastepny load daje Absent`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueDecryptFailure(CryptoFailure.KEY_INVALIDATED)
        assertEquals(LoadResult.Invalidated, s.load())
        assertWiped()
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(LoadResult.Absent, s.load())
    }

    @Test
    fun `SECRET-08 CORRUPTED przy odszyfrowaniu czysci i nastepny load daje Absent`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        assertEquals(LoadResult.Invalidated, s.load())
        assertWiped()
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(LoadResult.Absent, s.load())
    }

    @Test
    fun `SECRET-08 pusta tablica po odszyfrowaniu daje Invalidated i zeruje bufor`() = runTest {
        val s = store()
        s.saveS1()
        val empty = ByteArray(0)
        fakeCipher.enqueueDecrypt(DecryptResult.Success(empty))
        assertEquals(LoadResult.Invalidated, s.load())
        assertWiped()
    }

    @Test
    fun `SECRET-08 usuniecie rekordu false i wyjatek przy usuwaniu klucza nie psuja wyniku`() = runTest {
        val s = store()
        s.saveS1()
        storage.clearResult = false
        fakeCipher.deleteKeyError = IllegalStateException("test")
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        assertEquals(LoadResult.Invalidated, s.load())
        assertEquals(SecretState.INVALIDATED, s.state.value)
    }

    @Test
    fun `SECRET-08 wyjatek magazynu przy usuwaniu rekordu nie psuje wyniku`() = runTest {
        val s = store()
        s.saveS1()
        storage.clearError = RuntimeException("test")
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        assertEquals(LoadResult.Invalidated, s.load())
        assertEquals(1, fakeCipher.deleteKeyCount)
    }

    @Test
    fun `SECRET-08 po INVALIDATED zapis S2 daje Saved, PRESENT i load zwraca S2`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        s.load()
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(SaveResult.Saved, s.save(secret(s2Bytes)))
        assertEquals(SecretState.PRESENT, s.state.value)
        val loaded = s.load() as LoadResult.Loaded
        assertArrayEquals(s2Bytes, loaded.secret.copyBytes())
    }

    @Test
    fun `SECRET-04 SECRET-08 KEY_INVALIDATED przy zapisie czysci i ponawia zaszyfrowanie raz`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueEncryptFailure(CryptoFailure.KEY_INVALIDATED)
        var clearsSeenByDeleteKey = -1
        var encryptsSeenByDeleteKey = -1
        fakeCipher.onDeleteKey = {
            clearsSeenByDeleteKey = storage.clearCount
            encryptsSeenByDeleteKey = fakeCipher.encryptCount
        }
        assertEquals(SaveResult.Saved, s.save(secret(s2Bytes)))
        assertEquals(3, fakeCipher.encryptCount) // S1 + dwie próby S2
        assertEquals(1, clearsSeenByDeleteKey)
        assertEquals(2, encryptsSeenByDeleteKey) // klucz usunięty przed drugą próbą
        assertEquals(SecretState.PRESENT, s.state.value)
        val loaded = s.load() as LoadResult.Loaded
        assertArrayEquals(s2Bytes, loaded.secret.copyBytes())
        // obie próby dostały tę samą kopię, wyzerowaną na końcu
        val inputs = fakeCipher.encryptInputs.takeLast(2)
        assertEquals(true, inputs[0] === inputs[1])
        assertEquals(true, inputs[0].all { it == 0.toByte() })
    }

    @Test
    fun `SECRET-04 SECRET-08 dwukrotne KEY_INVALIDATED daje Failed CRYPTO i stan INVALIDATED`() = runTest {
        fakeCipher.enqueueEncryptFailure(CryptoFailure.KEY_INVALIDATED)
        fakeCipher.enqueueEncryptFailure(CryptoFailure.KEY_INVALIDATED)
        val s = store()
        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), s.save(secret(s2Bytes)))
        assertEquals(2, fakeCipher.encryptCount)
        assertEquals(0, storage.writeCount)
        assertEquals(SecretState.INVALIDATED, s.state.value)
    }

    @Test
    fun `SECRET-08 prawdziwy szyfrator z utrata klucza po zapisie daje Invalidated`() = runTest {
        val keySource = FakeSecretKeySource(hasKey = false)
        keySource.loseKeyAfterGenerate = true
        val real = AesGcmSecretCipher(keySource, FakeKeystoreEnvironment(locked = false))
        val s = store(real)
        assertEquals(SaveResult.Saved, s.save(secret(s1Bytes)))
        assertEquals(LoadResult.Invalidated, s.load())
        assertNull(storage.record)
        assertEquals(1, keySource.deleteKeyCount)
    }

    @Test
    fun `SECRET-06 stan INVALIDATED i pusty magazyn daja Absent i stan pozostaje INVALIDATED`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        s.load()
        assertEquals(LoadResult.Absent, s.load())
        assertEquals(SecretState.INVALIDATED, s.state.value)
    }
}
