package pl.twojanazwa.aapsremote.data

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AapsNumberSettingsConcurrencyTest {
    @Test
    fun `DATA-06 wczytanie zapis i usuniecie wolaja magazyn tylko na watku dyspozytora`() {
        val executor = Executors.newSingleThreadExecutor(ThreadFactory { Thread(it, "data-test-io") })
        try {
            val storage = InMemorySettingsStorage("+447700900999")
            val scope = CoroutineScope(Dispatchers.Default)
            runBlocking {
                val settings = AapsNumberSettings(storage, executor.asCoroutineDispatcher(), scope)
                while (settings.state.value is AapsNumberState.Loading) Thread.sleep(1)
                settings.save("+44 7700 900123")
                settings.clear()
            }
            assertEquals(3, storage.threadNames.size)
            assertTrue(storage.threadNames.all { it.startsWith("data-test-io") }, "watki: ${storage.threadNames}")
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `DATA-06 piecdziesiat rownoczesnych zapisow jest serializowane a stan zgadza sie z magazynem`() {
        val storage = InMemorySettingsStorage()
        val scope = CoroutineScope(Dispatchers.Default)
        val settings = AapsNumberSettings(storage, Dispatchers.IO, scope)
        runBlocking {
            val results = (0 until 50).map { i ->
                async(Dispatchers.Default) {
                    settings.save(if (i % 2 == 0) "+44 7700 900123" else "+44 7700 900999")
                }
            }.awaitAll()
            assertTrue(results.all { it == SaveNumberResult.Saved })
        }
        assertEquals(1, storage.maxInFlight)
        assertNotNull(storage.value)
        assertEquals(storage.value, settings.currentNumber())
        assertEquals(50, storage.writeCount)
    }
}
