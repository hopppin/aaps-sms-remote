package pl.twojanazwa.aapsremote.ui.settings.number

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles

class AapsNumberViewModelStateTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val store = FakeAapsNumberSettingsStore()
    private val session = SessionDoubles()

    private fun viewModel() = AapsNumberViewModel(store, session.coordinator)

    @Test
    fun `UI-04 magazyn w Loading daje Loading bez akcji Ustaw Zmien i Usun`() = runTest(main.dispatcher) {
        val state = viewModel().uiState.also { main.dispatcher.scheduler.runCurrent() }.value
        assertEquals(NumberDisplay.Loading, state.display)
        assertFalse(state.canSet)
        assertFalse(state.canChange)
        assertFalse(state.canClear)
    }

    @Test
    fun `UI-04 stany magazynu NotConfigured Configured LoadFailed daja wlasciwe wyswietlanie i akcje`() =
        runTest(main.dispatcher) {
            store.setState(AapsNumberState.NotConfigured)
            val vm = viewModel()
            vm.uiState.test {
                var state = awaitItem()
                if (state.display == NumberDisplay.Loading) state = awaitItem()
                assertEquals(NumberDisplay.NotConfigured, state.display)
                assertTrue(state.canSet)
                assertFalse(state.canClear)

                store.setConfigured("+447700900123")
                state = awaitItem()
                assertEquals(NumberDisplay.Configured("+447700900123"), state.display)
                assertTrue(state.canChange)
                assertTrue(state.canClear)
                assertFalse(state.canSet)

                store.setState(AapsNumberState.LoadFailed)
                state = awaitItem()
                assertEquals(NumberDisplay.LoadFailed, state.display)
                assertTrue(state.canSet)
                assertFalse(state.canClear)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
