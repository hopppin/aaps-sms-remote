package pl.twojanazwa.aapsremote.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AapsNumberProviderTest {
    @Test
    fun `DATA-05 w stanie Loading currentNumber to null a magazyn nieczytany`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123")
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        assertSame(AapsNumberState.Loading, settings.state.value)
        assertNull(settings.currentNumber())
        assertEquals(0, storage.readCount)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `DATA-05 tysiac wywolan currentNumber zwraca numer bez dotykania magazynu`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123")
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        testScheduler.advanceUntilIdle()
        val readsBefore = storage.readCount
        repeat(1000) { assertEquals("+447700900123", settings.currentNumber()) }
        assertEquals(readsBefore, storage.readCount)
        assertEquals(0, storage.writeCount)
        assertEquals(0, storage.removeCount)
    }

    @Test
    fun `DATA-05 currentNumber nie czeka na zablokowany zapis`() = kotlinx.coroutines.runBlocking {
        val storage = InMemorySettingsStorage("+447700900999")
        val scope = CoroutineScope(Dispatchers.Default)
        val settings = AapsNumberSettings(storage, Dispatchers.IO, scope)
        while (settings.state.value is AapsNumberState.Loading) Thread.sleep(1)
        val gate = java.util.concurrent.CountDownLatch(1)
        storage.writeGate = gate
        val save = async(Dispatchers.Default) { settings.save("+44 7700 900123") }
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            assertTrue(storage.writeEntered.await(10, TimeUnit.SECONDS))
            // Odczyt na osobnym wątku z limitem czasu: blokujący odczyt da porażkę, nie zawieszenie.
            val during = reader.submit<String?> { settings.currentNumber() }.get(5, TimeUnit.SECONDS)
            assertEquals("+447700900999", during)
        } finally {
            gate.countDown()
            reader.shutdownNow()
        }
        assertSame(SaveNumberResult.Saved, save.await())
        assertEquals("+447700900123", settings.currentNumber())
    }

    @Test
    fun `DATA-05 NotConfigured i LoadFailed daja null`() = runTest {
        val notConfigured = AapsNumberSettings(InMemorySettingsStorage(), StandardTestDispatcher(testScheduler), this)
        val failed = AapsNumberSettings(
            InMemorySettingsStorage().apply { readThrows = ClassCastException("x") },
            StandardTestDispatcher(testScheduler),
            this,
        )
        testScheduler.advanceUntilIdle()
        assertSame(AapsNumberState.NotConfigured, notConfigured.state.value)
        assertSame(AapsNumberState.LoadFailed, failed.state.value)
        assertNull(notConfigured.currentNumber())
        assertNull(failed.currentNumber())
    }
}
