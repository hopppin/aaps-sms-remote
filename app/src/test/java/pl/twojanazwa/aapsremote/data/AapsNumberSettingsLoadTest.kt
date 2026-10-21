package pl.twojanazwa.aapsremote.data

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AapsNumberSettingsLoadTest {
    private fun TestScope.settings(storage: InMemorySettingsStorage): AapsNumberSettings {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AapsNumberSettings(storage, dispatcher, this)
    }

    private fun AapsNumberState.destination(): String? = (this as? AapsNumberState.Configured)?.number?.destination

    @Test
    fun `DATA-02 przed wykonaniem zadan Loading i magazyn nieczytany potem Configured i jeden odczyt`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123")
        val settings = settings(storage)
        assertSame(AapsNumberState.Loading, settings.state.value)
        assertEquals(0, storage.readCount)
        testScheduler.advanceUntilIdle()
        assertEquals("+447700900123", settings.state.value.destination())
        assertEquals(1, storage.readCount)
    }

    @Test
    fun `DATA-02 pusty magazyn daje NotConfigured`() = runTest {
        val settings = settings(InMemorySettingsStorage())
        testScheduler.advanceUntilIdle()
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
    }

    @Test
    fun `DATA-02 niepoprawna wartosc w magazynie daje NotConfigured bez zapisu i usuniecia`() = runTest {
        val storage = InMemorySettingsStorage("70090012")
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
        assertEquals(0, storage.writeCount)
        assertEquals(0, storage.removeCount)
    }

    @Test
    fun `DATA-02 wyjatek odczytu daje LoadFailed bez wyjatku na zewnatrz`() = runTest {
        val storage = InMemorySettingsStorage().apply { readThrows = ClassCastException("x") }
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertSame(AapsNumberState.LoadFailed, settings.state.value)
    }

    @Test
    fun `DATA-02 zapis przed wczytaniem wygrywa a magazyn nie jest czytany`() = runTest {
        val storage = InMemorySettingsStorage("+447700900999")
        val settings = settings(storage)
        assertEquals(SaveNumberResult.Saved, settings.save("+44 7700 900123"))
        testScheduler.advanceUntilIdle()
        assertEquals("+447700900123", settings.state.value.destination())
        assertEquals(0, storage.readCount)
    }

    @Test
    fun `DATA-02 kolektor widzi Loading potem Configured`() = runTest {
        val settings = settings(InMemorySettingsStorage("+447700900123"))
        settings.state.test {
            assertSame(AapsNumberState.Loading, awaitItem())
            val configured = awaitItem()
            assertTrue(configured is AapsNumberState.Configured)
            assertEquals("+447700900123", configured.destination())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DATA-02 toString stanu Configured nie zawiera numeru`() = runTest {
        val settings = settings(InMemorySettingsStorage("+447700900123"))
        testScheduler.advanceUntilIdle()
        assertEquals("Configured(***)", settings.state.value.toString())
    }
}
