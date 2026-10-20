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
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreFailureTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private fun s1() = TotpSecret.fromBytes(s1Bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()

    private fun TestScope.store(cipher: SecretCipher) =
        KeystoreTotpSecretStore(storage, cipher, StandardTestDispatcher(testScheduler))

    @Test
    fun `SECRET-17 kazdy port rzucajacy RuntimeException nie powoduje wyjatku z magazynu`() = runTest {
        val boom = RuntimeException("x")
        val keySource = FakeSecretKeySource().apply {
            getKeyError = boom
            generateKeyError = boom
            deleteKeyError = boom
        }
        val environment = FakeKeystoreEnvironment(lockedCheckError = boom, predicateError = boom)
        storage.readError = boom
        storage.writeError = boom
        storage.clearError = boom
        val s = store(AesGcmSecretCipher(keySource, environment))

        val state = s.refreshState()
        val save = s.save(s1())
        val load = s.load()
        val delete = s.delete()

        assertEquals(SecretState.UNKNOWN, state)
        assertTrue(
            save == SaveResult.Saved || save == SaveResult.Locked || save is SaveResult.Failed,
            "nieoczekiwany wynik save",
        )
        assertEquals(SaveResult.Failed(SaveFailure.CRYPTO), save)
        assertEquals(LoadResult.Unavailable, load)
        assertEquals(DeleteResult.Failed, delete)
    }

    @Test
    fun `SECRET-17 magazyn rzucajacy w zapisie po udanym szyfrowaniu daje Failed STORAGE bez wyjatku`() = runTest {
        storage.writeError = RuntimeException("x")
        val s = store(FakeSecretCipher())
        assertEquals(SaveResult.Failed(SaveFailure.STORAGE), s.save(s1()))
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
