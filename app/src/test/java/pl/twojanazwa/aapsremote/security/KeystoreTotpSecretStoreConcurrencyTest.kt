package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoreTotpSecretStoreConcurrencyTest {

    private val s1Bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val s2Bytes = ByteArray(20) { it.toByte() }
    private fun secret(bytes: ByteArray) = TotpSecret.fromBytes(bytes.copyOf())

    private val storage = InMemorySecretRecordStorage()
    private val fakeCipher = FakeSecretCipher()
    private val executors = mutableListOf<ExecutorService>()

    private fun pool(threads: Int, namePrefix: String): CoroutineDispatcher {
        val counter = java.util.concurrent.atomic.AtomicInteger()
        val executor = Executors.newFixedThreadPool(threads) { Thread(it, "$namePrefix-${counter.incrementAndGet()}") }
        executors += executor
        return executor.asCoroutineDispatcher()
    }

    @AfterEach
    fun tearDown() {
        executors.forEach { it.shutdownNow() }
    }

    @Test
    fun `SECRET-13 load czeka na zakonczenie zapisu i nie wola szyfratora przed jego zwolnieniem`() = runBlocking {
        withTimeout(20_000) {
            val s = KeystoreTotpSecretStore(storage, fakeCipher, pool(4, "secret-io"))
            val gate = CountDownLatch(1)
            storage.writeGate = gate
            val saving = async(Dispatchers.Default) { s.save(secret(s1Bytes)) }
            assertTrue(storage.writeEntered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            // UNDISPATCHED: load wykonuje się synchronicznie do pierwszego zawieszenia (czekanie na Mutex trzymany przez save).
            val loading = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { s.load() }
            assertFalse(loading.isCompleted)
            assertEquals(0, fakeCipher.decryptCount)
            gate.countDown()
            assertEquals(SaveResult.Saved, saving.await())
            val loaded = loading.await() as LoadResult.Loaded
            assertArrayEquals(s1Bytes, loaded.secret.copyBytes())
            assertEquals(1, fakeCipher.decryptCount)
        }
    }

    @Test
    fun `SECRET-13 piecdziesiat rownoleglych zapisow nigdy nie nakłada wywolan portow`() = runBlocking {
        withTimeout(30_000) {
            val probe = ConcurrencyProbe()
            storage.probe = probe
            fakeCipher.probe = probe
            val s = KeystoreTotpSecretStore(storage, fakeCipher, pool(4, "secret-io"))
            val results = (0 until 50).map { i ->
                async(Dispatchers.Default) { s.save(secret(if (i % 2 == 0) s1Bytes else s2Bytes)) }
            }.awaitAll()
            assertTrue(results.all { it == SaveResult.Saved })
            assertEquals(1, probe.max)
            val loaded = s.load() as LoadResult.Loaded
            val bytes = loaded.secret.copyBytes()
            assertTrue(bytes.contentEquals(s1Bytes) || bytes.contentEquals(s2Bytes))
        }
    }

    @Test
    fun `SECRET-13 wszystkie wywolania portow ida na watku dyspozytora`() = runBlocking {
        withTimeout(20_000) {
            val probe = ConcurrencyProbe()
            storage.probe = probe
            fakeCipher.probe = probe
            val dispatcher = pool(1, "secret-io-test")
            val s = KeystoreTotpSecretStore(storage, fakeCipher, dispatcher)
            val callerThread = Thread.currentThread().name
            s.refreshState()
            s.save(secret(s1Bytes))
            s.load()
            s.delete()
            val names = probe.threadNames
            assertTrue(names.isNotEmpty())
            assertTrue(names.all { it.startsWith("secret-io-test-1") } /* debug korutyn dopisuje " @coroutine#N" */, "wywolanie poza watkiem dyspozytora")
            assertFalse(names.any { it.startsWith(callerThread) })
        }
    }

    @Test
    fun `SECRET-13 anulowanie po zapisie rekordu daje CancellationException, rekord S1 i stan PRESENT`() = runTest {
        val s = KeystoreTotpSecretStore(storage, fakeCipher, StandardTestDispatcher(testScheduler))
        var job: Job? = null
        var thrown: Throwable? = null
        storage.onWrite = { job?.cancel() }
        job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                s.save(secret(s1Bytes))
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        advanceUntilIdle()
        assertTrue(thrown is CancellationException)
        assertEquals(SecretState.PRESENT, s.state.value)
        storage.onWrite = null
        val loaded = s.load() as LoadResult.Loaded
        assertArrayEquals(s1Bytes, loaded.secret.copyBytes())
    }
}
