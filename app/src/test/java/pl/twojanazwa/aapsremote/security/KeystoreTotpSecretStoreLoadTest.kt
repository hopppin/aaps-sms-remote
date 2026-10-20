package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpGenerator
import pl.twojanazwa.aapsremote.otp.TotpSecret

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreLoadTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()

    private fun TestScope.store(cipher: SecretCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    private fun code(s: TotpSecret) = TotpGenerator.generate(s, 59000).value

    @Test
    fun `SECRET-06 dwa odczyty daja dwa rozne obiekty z kodem 287082`() = runTest {
        val s = store(fakeCipher)
        s.save(TotpSecret.fromBytes(s1Bytes.copyOf()))
        val a = s.load() as LoadResult.Loaded
        val b = s.load() as LoadResult.Loaded
        assertNotSame(a.secret, b.secret)
        assertEquals("287082", code(a.secret))
        assertEquals("287082", code(b.secret))
        assertEquals(2, fakeCipher.decryptCount)
    }

    @Test
    fun `SECRET-06 odszyfrowana tablica jest zerowana a sekret nadal daje 287082`() = runTest {
        val s = store(fakeCipher)
        s.save(TotpSecret.fromBytes(s1Bytes.copyOf()))
        val loaded = s.load() as LoadResult.Loaded
        assertTrue(fakeCipher.decryptOutputs.single().all { it == 0.toByte() })
        assertEquals("287082", code(loaded.secret))
    }

    @Test
    fun `SECRET-06 pusty magazyn daje Absent bez wywolania szyfratora i stan ABSENT`() = runTest {
        val s = store(fakeCipher)
        assertEquals(LoadResult.Absent, s.load())
        assertEquals(0, fakeCipher.decryptCount)
        assertEquals(SecretState.ABSENT, s.state.value)
    }

    @Test
    fun `SECRET-06 udany odczyt ustawia stan PRESENT`() = runTest {
        val s = store(fakeCipher)
        s.save(TotpSecret.fromBytes(s1Bytes.copyOf()))
        s.load()
        assertEquals(SecretState.PRESENT, s.state.value)
    }
}
