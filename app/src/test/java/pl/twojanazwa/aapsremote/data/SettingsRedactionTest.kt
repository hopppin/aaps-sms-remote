package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRedactionTest {
    private val forbidden = listOf("7700", "900123", "700900123")

    private fun assertRedacted(text: String) {
        forbidden.forEach { assertFalse(text.contains(it), "toString zawiera fragment numeru") }
    }

    @Test
    fun `DATA-17 toString stanu wynikow i obiektu ustawien nie zawiera numeru`() = runTest {
        val settings = AapsNumberSettings(
            InMemorySettingsStorage("+447700900123"),
            StandardTestDispatcher(testScheduler),
            this,
        )
        testScheduler.advanceUntilIdle()
        assertRedacted(settings.state.value.toString())
        assertRedacted(settings.toString())
        assertRedacted(settings.save("+44 7700 900123").toString())
        assertRedacted(SaveNumberResult.Invalid.toString())
        assertRedacted(SaveNumberResult.Failed.toString())
        assertRedacted(settings.clear().toString())
        assertRedacted(ClearNumberResult.Failed.toString())
    }
}
