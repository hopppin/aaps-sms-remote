package pl.twojanazwa.aapsremote.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AapsNumberSettingsClearTest {
    private fun TestScope.settings(storage: InMemorySettingsStorage): AapsNumberSettings {
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        testScheduler.advanceUntilIdle()
        return settings
    }

    private fun AapsNumberSettings.destination(): String? =
        (state.value as? AapsNumberState.Configured)?.number?.destination

    @Test
    fun `DATA-04 usuniecie skonfigurowanego numeru daje Cleared i NotConfigured`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123")
        val settings = settings(storage)
        assertSame(ClearNumberResult.Cleared, settings.clear())
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
        assertNull(storage.value)
    }

    @Test
    fun `DATA-04 commit false daje Failed i stan bez zmian`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123").apply { removeResult = false }
        val settings = settings(storage)
        assertSame(ClearNumberResult.Failed, settings.clear())
        assertEquals("+447700900123", settings.destination())
    }

    @Test
    fun `DATA-04 wyjatek usuniecia daje Failed i stan bez zmian`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123").apply { removeThrows = RuntimeException("x") }
        val settings = settings(storage)
        assertSame(ClearNumberResult.Failed, settings.clear())
        assertEquals("+447700900123", settings.destination())
    }

    @Test
    fun `DATA-04 usuniecie przy NotConfigured wola magazyn i daje Cleared`() = runTest {
        val storage = InMemorySettingsStorage()
        val settings = settings(storage)
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
        assertSame(ClearNumberResult.Cleared, settings.clear())
        assertEquals(1, storage.removeCount)
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
    }

    @Test
    fun `DATA-04 anulowanie po zatwierdzonym commit zostawia NotConfigured`() = runTest {
        val storage = InMemorySettingsStorage("+447700900123")
        val settings = settings(storage)
        val job = async(start = CoroutineStart.UNDISPATCHED) { settings.clear() }
        storage.afterRemove = { job.cancel() }
        try {
            job.await()
            fail("oczekiwano CancellationException")
        } catch (e: CancellationException) {
            // oczekiwane
        }
        assertSame(AapsNumberState.NotConfigured, settings.state.value)
    }
}
