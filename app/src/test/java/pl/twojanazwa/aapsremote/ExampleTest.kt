package pl.twojanazwa.aapsremote

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Przykładowy test pokazujący użycie JUnit 5, MockK i Turbine. */
class ExampleTest {

    private interface MessageSource {
        fun messages(): Flow<String>
    }

    @Test
    fun `flow z mocka emituje przetworzone wartosci`() = runTest {
        val source = mockk<MessageSource>()
        every { source.messages() } returns flowOf("bg", "pump")

        source.messages().map { it.uppercase() }.test {
            assertEquals("BG", awaitItem())
            assertEquals("PUMP", awaitItem())
            awaitComplete()
        }
    }
}
