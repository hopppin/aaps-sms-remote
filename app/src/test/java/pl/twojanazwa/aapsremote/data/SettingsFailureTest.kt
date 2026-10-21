package pl.twojanazwa.aapsremote.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFailureTest {
    @Test
    fun `DATA-15 wyjatki we wszystkich metodach magazynu daja Failed i null bez wyjatku`() = runTest {
        val storage = InMemorySettingsStorage().apply {
            readThrows = RuntimeException("x")
            writeThrows = RuntimeException("x")
            removeThrows = RuntimeException("x")
        }
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        testScheduler.advanceUntilIdle()
        assertSame(AapsNumberState.LoadFailed, settings.state.value)
        assertSame(SaveNumberResult.Failed, settings.save("+44 7700 900123"))
        assertSame(ClearNumberResult.Failed, settings.clear())
        assertNull(settings.currentNumber())
    }

    @Test
    fun `DATA-15 CancellationException z magazynu przy zapisie trafia do wywolujacego`() = runTest {
        val storage = InMemorySettingsStorage().apply { writeThrows = CancellationException("x") }
        val settings = AapsNumberSettings(storage, StandardTestDispatcher(testScheduler), this)
        testScheduler.advanceUntilIdle()
        try {
            settings.save("+44 7700 900123")
            fail("oczekiwano CancellationException")
        } catch (e: CancellationException) {
            // oczekiwane
        }
    }
}
