package pl.twojanazwa.aapsremote.data

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation

private class FakeLimitsStorage(val values: MutableMap<String, Any> = mutableMapOf()) : CommandLimitsStorage {
    var readCount = 0
    var writeCount = 0
    var readThrows: Throwable? = null
    var writeThrows: Throwable? = null
    var writeResult = true

    override fun readInt(key: String): Int? {
        readCount++
        readThrows?.let { throw it }
        return values[key]?.let { it as Int }
    }

    override fun writeAll(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): Boolean {
        writeCount++
        writeThrows?.let { throw it }
        if (!writeResult) return false
        values[CommandLimitsStorage.KEY_CARBS_MAX_GRAMS] = carbsMaxGrams
        values[CommandLimitsStorage.KEY_BOLUS_MAX_CENTI_UNITS] = bolusMaxCentiUnits
        values[CommandLimitsStorage.KEY_BOLUS_MIN_INTERVAL_MINUTES] = bolusMinIntervalMinutes
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLimitsSettingsTest {
    private fun TestScope.settings(storage: FakeLimitsStorage) =
        CommandLimitsSettings(storage, StandardTestDispatcher(testScheduler), this)

    private fun stored(carbs: Any? = 30, bolus: Any? = 250, interval: Any? = 20) = FakeLimitsStorage(
        listOfNotNull(
            carbs?.let { CommandLimitsStorage.KEY_CARBS_MAX_GRAMS to it },
            bolus?.let { CommandLimitsStorage.KEY_BOLUS_MAX_CENTI_UNITS to it },
            interval?.let { CommandLimitsStorage.KEY_BOLUS_MIN_INTERVAL_MINUTES to it },
        ).toMap().toMutableMap(),
    )

    private fun limits(c: Int, b: Int, i: Int) =
        (CommandLimits.of(c, b, i) as LimitsValidation.Valid).limits

    private fun CommandLimitsState.loaded() = (this as CommandLimitsState.Loaded).limits

    @Test
    fun `LIM-04 konstruktor bez zadan dyspozytora daje Loading a magazyn nieczytany`() = runTest {
        val storage = stored()
        val settings = settings(storage)
        assertSame(CommandLimitsState.Loading, settings.state.value)
        assertEquals(0, storage.readCount)
    }

    @Test
    fun `LIM-01 pusty magazyn daje Loaded z wartosciami domyslnymi`() = runTest {
        val settings = settings(FakeLimitsStorage())
        testScheduler.advanceUntilIdle()
        assertEquals(CommandLimits.DEFAULT, settings.state.value.loaded())
    }

    @Test
    fun `LIM-04 komplet kluczy daje Loaded z zapisanymi wartosciami`() = runTest {
        val settings = settings(stored())
        testScheduler.advanceUntilIdle()
        assertEquals(limits(30, 250, 20), settings.state.value.loaded())
    }

    @Test
    fun `LIM-04 kolektor widzi Loading potem Loaded`() = runTest {
        val settings = settings(stored())
        settings.state.test {
            assertSame(CommandLimitsState.Loading, awaitItem())
            assertEquals(limits(30, 250, 20), awaitItem().loaded())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LIM-04 brak czesci kluczy daje LoadFailed`() = runTest {
        for (storage in listOf(stored(bolus = null, interval = null), stored(carbs = null), stored(interval = null))) {
            val settings = settings(storage)
            testScheduler.advanceUntilIdle()
            assertSame(CommandLimitsState.LoadFailed, settings.state.value)
            assertEquals(0, storage.writeCount)
        }
    }

    @Test
    fun `LIM-04 wartosci spoza zakresu lub zly typ daja LoadFailed bez zapisu`() = runTest {
        val cases = listOf(stored(bolus = 252), stored(carbs = 500), stored(interval = "20"))
        for (storage in cases) {
            val settings = settings(storage)
            testScheduler.advanceUntilIdle()
            assertSame(CommandLimitsState.LoadFailed, settings.state.value)
            assertEquals(0, storage.writeCount)
        }
    }

    @Test
    fun `LIM-04 ClassCastException przy odczycie daje LoadFailed bez wyjatku`() = runTest {
        val storage = FakeLimitsStorage().apply { readThrows = ClassCastException("x") }
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertSame(CommandLimitsState.LoadFailed, settings.state.value)
    }

    @Test
    fun `LIM-04 w stanie LoadFailed zapis daje Saved i Loaded z jednym zapisem trzech kluczy`() = runTest {
        val storage = stored(bolus = null)
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertSame(CommandLimitsState.LoadFailed, settings.state.value)
        assertEquals(SaveLimitsResult.Saved, settings.save(limits(40, 300, 20)))
        assertEquals(limits(40, 300, 20), settings.state.value.loaded())
        assertEquals(1, storage.writeCount)
        assertEquals(3, storage.values.size)
    }

    @Test
    fun `LIM-04 zapis przed wczytaniem wygrywa a magazyn nie jest czytany`() = runTest {
        val storage = stored()
        val settings = settings(storage)
        assertEquals(SaveLimitsResult.Saved, settings.save(limits(40, 300, 20)))
        testScheduler.advanceUntilIdle()
        assertEquals(limits(40, 300, 20), settings.state.value.loaded())
        assertEquals(0, storage.readCount)
    }

    @Test
    fun `LIM-04 commit false daje Failed a stan bez zmian`() = runTest {
        val storage = stored().apply { writeResult = false }
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertEquals(SaveLimitsResult.Failed, settings.save(limits(40, 300, 20)))
        assertEquals(limits(30, 250, 20), settings.state.value.loaded())
    }

    @Test
    fun `LIM-04 wyjatek zapisu daje Failed bez wyjatku a stan bez zmian`() = runTest {
        val storage = stored().apply { writeThrows = RuntimeException("x") }
        val settings = settings(storage)
        testScheduler.advanceUntilIdle()
        assertEquals(SaveLimitsResult.Failed, settings.save(limits(40, 300, 20)))
        assertEquals(limits(30, 250, 20), settings.state.value.loaded())
    }

    @Test
    fun `LIM-12 toString stanu i ustawien nie zawiera wartosci limitow`() = runTest {
        val settings = settings(stored(37, 235, 23))
        testScheduler.advanceUntilIdle()
        for (text in listOf(settings.state.value.toString(), settings.toString())) {
            for (forbidden in listOf("37", "235", "2,35", "23")) {
                assertFalse(text.contains(forbidden), "toString zawiera $forbidden")
            }
        }
        assertTrue(settings.state.value.toString().startsWith("Loaded"))
    }
}
