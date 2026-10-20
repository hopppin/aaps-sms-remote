package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreReplaceTest {

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

    private suspend fun KeystoreTotpSecretStore.assertLoads(expected: ByteArray) {
        val loaded = load() as LoadResult.Loaded
        assertArrayEquals(expected, loaded.secret.copyBytes())
    }

    @Test
    fun `SECRET-05 zamiana S1 na S2 daje Saved, jeden rekord, load zwraca S2, klucz nietkniety`() = runTest {
        val keySource = FakeSecretKeySource()
        val s = store(AesGcmSecretCipher(keySource, FakeKeystoreEnvironment()))
        s.saveS1()
        assertEquals(SaveResult.Saved, s.save(secret(s2Bytes)))
        assertNotNull(storage.record)
        s.assertLoads(s2Bytes)
        assertEquals(0, keySource.generateKeyCount)
        assertEquals(0, keySource.deleteKeyCount)
    }

    @Test
    fun `SECRET-05 zapis false bez zmiany wartosci zostawia rekord S1`() = runTest {
        val s = store()
        s.saveS1()
        val before = storage.record
        storage.writeResult = false
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(before, storage.record)
        storage.writeResult = true
        s.assertLoads(s1Bytes)
    }

    @Test
    fun `SECRET-05 pierwszy zapis zmienia wartosc i zwraca false, wycofanie przywraca S1`() = runTest {
        val s = store()
        s.saveS1()
        val before = storage.record
        storage.failFirstWritesWithChange = 1
        val writesBefore = storage.writeCount
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(2, storage.writeCount - writesBefore) // nowy rekord + dokładnie jedna próba wycofania
        assertEquals(before, storage.record)
        s.assertLoads(s1Bytes)
    }

    @Test
    fun `SECRET-05 pusty magazyn i zapis zmieniajacy wartosc z false konczy sie usunieciem i stanem ABSENT`() = runTest {
        val s = store()
        storage.failFirstWritesWithChange = 1
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s1Bytes)))
        assertNull(storage.record)
        assertEquals(1, storage.clearCount)
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-05 kazdy zapis zmienia wartosc i zwraca false, dwa zapisy, load nie daje Invalidated, stan PRESENT`() = runTest {
        val s = store()
        s.saveS1()
        storage.writeResult = false
        storage.writeChangesValueButReturnsFalse = true
        val writesBefore = storage.writeCount
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(2, storage.writeCount - writesBefore)
        assertEquals(SecretState.PRESENT, s.state.value)
        s.assertLoads(s1Bytes)
    }

    @Test
    fun `SECRET-05 wyjatek przy zapisie nowego rekordu wycofuje do S1`() = runTest {
        val s = store()
        s.saveS1()
        val before = storage.record
        storage.failFirstWritesWithChangeAndThrow = 1
        val writesBefore = storage.writeCount
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(2, storage.writeCount - writesBefore) // nowy rekord (wyjątek) + dokładnie jedna próba wycofania
        assertEquals(before, storage.record)
        assertEquals(SecretState.PRESENT, s.state.value)
        s.assertLoads(s1Bytes)
    }

    @Test
    fun `SECRET-05 rekord nieprawidlowy nie jest przywracany, wycofanie usuwa wartosc`() = runTest {
        storage.record = "@@@@"
        storage.failFirstWritesWithChange = 1
        val s = store()
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s1Bytes)))
        assertNull(storage.record)
        assertEquals(1, storage.writes.size)
        assertEquals(1, storage.clearCount)
    }

    @Test
    fun `SECRET-05 odczyt przed zapisem rzuca, brak proby wycofania`() = runTest {
        val s = store()
        s.saveS1()
        storage.readErrorOnCalls = setOf(storage.readCount + 1)
        storage.writeResult = false
        val writesBefore = storage.writeCount
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(1, storage.writeCount - writesBefore)
        assertEquals(0, storage.clearCount)
    }

    @Test
    fun `SECRET-05 stan INVALIDATED i pusty magazyn, nieudany zapis zachowuje INVALIDATED, potem zapis dziala`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueDecryptFailure(CryptoFailure.CORRUPTED)
        assertEquals(LoadResult.Invalidated, s.load())
        storage.failFirstWritesWithChange = 1
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s1Bytes)))
        assertNull(storage.record)
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(SaveResult.Saved, s.save(secret(s1Bytes)))
        assertEquals(SecretState.PRESENT, s.state.value)
        s.assertLoads(s1Bytes)
    }

    @Test
    fun `SECRET-05 KEY_INVALIDATED przy zapisie i nieudany commit konczy sie usunieciem i stanem INVALIDATED`() = runTest {
        val s = store()
        s.saveS1()
        fakeCipher.enqueueEncryptFailure(CryptoFailure.KEY_INVALIDATED)
        storage.failFirstWritesWithChange = 1
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(secret(s2Bytes)))
        assertEquals(2, storage.clearCount) // wyczyszczenie po KEY_INVALIDATED + wycofanie przez usunięcie
        assertNull(storage.record)
        assertEquals(SecretState.INVALIDATED, s.state.value)
        assertEquals(LoadResult.Absent, s.load())
    }

    @Test
    fun `SECRET-05 LOCKED albo UNAVAILABLE przy zapisie nie zmieniaja rekordu`() = runTest {
        val s = store()
        s.saveS1()
        val before = storage.record
        fakeCipher.enqueueEncryptFailure(CryptoFailure.LOCKED)
        assertEquals(SaveResult.Locked, s.save(secret(s2Bytes)))
        assertEquals(before, storage.record)
        fakeCipher.enqueueEncryptFailure(CryptoFailure.UNAVAILABLE)
        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), s.save(secret(s2Bytes)))
        assertEquals(before, storage.record)
    }
}
