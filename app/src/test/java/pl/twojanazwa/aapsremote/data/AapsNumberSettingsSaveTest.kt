package pl.twojanazwa.aapsremote.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AapsNumberSettingsSaveTest {
    private fun TestScope.settings(storage: InMemorySettingsStorage): AapsNumberSettings {
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        testScheduler.advanceUntilIdle()
        return settings
    }

    private fun AapsNumberSettings.destination(): String? =
        (state.value as? AapsNumberState.Configured)?.number?.destination

    @Test
    fun `DATA-01 pusty magazyn zapis numeru ze spacjami zapisuje postac znormalizowana raz`() = runTest {
        val storage = InMemorySettingsStorage()
        val settings = settings(storage)
        assertSame(SaveNumberResult.Saved, settings.save("+44 7700 900123"))
        assertEquals(1, storage.writeCount)
        assertEquals("+447700900123", storage.lastWritten)
    }

    @Test
    fun `DATA-01 zapis z nawiasami zapisuje numer bez nawiasow`() = runTest {
        val storage = InMemorySettingsStorage()
        val settings = settings(storage)
        assertSame(SaveNumberResult.Saved, settings.save("+44 (0)7700 900123"))
        assertEquals("+4407700900123", storage.lastWritten)
    }

    @Test
    fun `DATA-03 zapis nadpisuje skonfigurowany numer`() = runTest {
        val settings = settings(InMemorySettingsStorage("+447700900999"))
        assertSame(SaveNumberResult.Saved, settings.save("+44 7700 900123"))
        assertEquals("+447700900123", settings.destination())
    }

    @Test
    fun `DATA-03 niepoprawne numery daja Invalid bez zapisu i bez zmiany stanu`() = runTest {
        val storage = InMemorySettingsStorage("+447700900999")
        val settings = settings(storage)
        for (raw in listOf("70090012", "", "   ", "+44 7700 9OO123", "44+7700900123")) {
            assertSame(SaveNumberResult.Invalid, settings.save(raw), "raw o dlugosci ${raw.length}")
        }
        assertEquals(0, storage.writeCount)
        assertEquals("+447700900999", settings.destination())
    }

    @Test
    fun `DATA-03 dokladnie dziewiec cyfr jest poprawne`() = runTest {
        val storage = InMemorySettingsStorage()
        val settings = settings(storage)
        assertSame(SaveNumberResult.Saved, settings.save("700900123"))
        assertEquals("700900123", storage.lastWritten)
    }

    @Test
    fun `DATA-03 commit false daje Failed i stan bez zmian`() = runTest {
        val storage = InMemorySettingsStorage("+447700900999").apply { writeResult = false }
        val settings = settings(storage)
        assertSame(SaveNumberResult.Failed, settings.save("+44 7700 900123"))
        assertEquals("+447700900999", settings.destination())
    }

    @Test
    fun `DATA-03 wyjatek zapisu daje Failed bez wyjatku i stan bez zmian`() = runTest {
        val storage = InMemorySettingsStorage("+447700900999").apply { writeThrows = RuntimeException("x") }
        val settings = settings(storage)
        assertSame(SaveNumberResult.Failed, settings.save("+44 7700 900123"))
        assertEquals("+447700900999", settings.destination())
    }

    @Test
    fun `DATA-03 po LoadFailed zapis daje Saved i Configured`() = runTest {
        val storage = InMemorySettingsStorage().apply { readThrows = ClassCastException("x") }
        val settings = settings(storage)
        assertSame(AapsNumberState.LoadFailed, settings.state.value)
        assertSame(SaveNumberResult.Saved, settings.save("+44 7700 900123"))
        assertTrue(settings.state.value is AapsNumberState.Configured)
        assertEquals("+447700900123", settings.destination())
    }

    @Test
    fun `DATA-03 anulowanie po udanym commit zostawia stan zgodny z plikiem`() = runTest {
        val storage = InMemorySettingsStorage("+447700900999")
        val settings = settings(storage)
        val job = async(start = CoroutineStart.UNDISPATCHED) { settings.save("+44 7700 900123") }
        storage.afterWrite = { job.cancel() }
        try {
            job.await()
            fail("oczekiwano CancellationException")
        } catch (e: CancellationException) {
            // oczekiwane
        }
        assertEquals("+447700900123", storage.value)
        assertEquals("+447700900123", settings.destination())
    }
}
