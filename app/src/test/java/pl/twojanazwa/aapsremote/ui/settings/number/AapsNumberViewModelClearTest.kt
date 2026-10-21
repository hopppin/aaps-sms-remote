package pl.twojanazwa.aapsremote.ui.settings.number

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.ClearNumberResult
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles

class AapsNumberViewModelClearTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val store = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val session = SessionDoubles()

    private fun viewModel() = AapsNumberViewModel(store, session.coordinator)

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `UI-06 usun otwiera okno potwierdzenia bez wywolania clear`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        settle()
        assertTrue(vm.uiState.value.confirmingClear)
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun `UI-06 potwierdzenie przy Cleared zamyka okno wola clear raz i daje CLEARED`() = runTest(main.dispatcher) {
        store.clearResults += ClearNumberResult.Cleared
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        vm.onClearConfirmed()
        settle()
        val state = vm.uiState.value
        assertFalse(state.confirmingClear)
        assertEquals(1, store.clearCalls)
        assertEquals(NumberMessage.CLEARED, state.message)
        assertFalse(state.busy)
    }

    @Test
    fun `UI-06 potwierdzenie przy Failed daje CLEAR_FAILED`() = runTest(main.dispatcher) {
        store.clearResults += ClearNumberResult.Failed
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        vm.onClearConfirmed()
        settle()
        assertEquals(NumberMessage.CLEAR_FAILED, vm.uiState.value.message)
    }

    @Test
    fun `UI-06 anulowanie zamyka okno bez clear`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        vm.onClearDismissed()
        settle()
        assertFalse(vm.uiState.value.confirmingClear)
        assertEquals(0, store.clearCalls)
    }
}
