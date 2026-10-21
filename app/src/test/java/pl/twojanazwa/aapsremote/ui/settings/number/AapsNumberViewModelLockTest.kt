package pl.twojanazwa.aapsremote.ui.settings.number

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles

class AapsNumberViewModelLockTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val store = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val session = SessionDoubles()

    private fun viewModel() = AapsNumberViewModel(store, session.coordinator)

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `UI-15 przy poleceniu akcje numeru niedostepne i znacznik commandActive`() = runTest(main.dispatcher) {
        session.startCommand()
        val vm = viewModel()
        settle()
        val state = vm.uiState.value
        assertTrue(state.commandActive)
        assertFalse(state.canSet)
        assertFalse(state.canChange)
        assertFalse(state.canClear)
        assertFalse(state.canSave)
    }

    @Test
    fun `UI-15 start polecenia zamyka okno usuniecia a edycja zostaje bez Zapisz`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        settle()
        assertTrue(vm.uiState.value.confirmingClear)
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        session.startCommand()
        settle()
        val state = vm.uiState.value
        assertFalse(state.confirmingClear)
        assertTrue(state.editing)
        assertEquals("+44 7700 900123", state.input)
        assertFalse(state.canSave)
    }

    @Test
    fun `UI-15 wyscig Zapisz po starcie polecenia nie wola save i daje COMMAND_ACTIVE`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        session.startCommand() // bez wykonania zadań dyspozytora
        vm.onSaveClicked()
        settle()
        assertTrue(store.saveCalls.isEmpty())
        assertEquals(NumberMessage.COMMAND_ACTIVE, vm.uiState.value.message)
    }

    @Test
    fun `UI-15 wyscig potwierdzenie usuniecia po starcie polecenia nie wola clear`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onClearClicked()
        settle()
        session.startCommand() // bez wykonania zadań dyspozytora
        vm.onClearConfirmed()
        settle()
        assertEquals(0, store.clearCalls)
        assertEquals(NumberMessage.COMMAND_ACTIVE, vm.uiState.value.message)
        assertFalse(vm.uiState.value.confirmingClear)
    }

    @Test
    fun `UI-15 zapis zawieszony przed startem polecenia jest dokanczany z SAVED`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        vm.onSaveClicked()
        settle()
        session.startCommand()
        settle()
        gate.complete(Unit)
        settle()
        assertEquals(1, store.saveCalls.size)
        assertEquals(NumberMessage.SAVED, vm.uiState.value.message)
    }

    @Test
    fun `UI-15 koniec polecenia przywraca akcje wedlug UI-04`() = runTest(main.dispatcher) {
        session.startCommand()
        val vm = viewModel()
        settle()
        session.finishCommand()
        settle()
        val state = vm.uiState.value
        assertFalse(state.commandActive)
        assertTrue(state.canChange)
        assertTrue(state.canClear)
    }

    @Test
    fun `UI-16 dwa razy Zapisz i Usun przy zawieszonym save daje jedno wywolanie magazynu`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            store.gate = gate
            val vm = viewModel()
            settle()
            vm.onEditClicked()
            vm.onInputChanged("+44 7700 900123")
            vm.onSaveClicked()
            vm.onSaveClicked()
            vm.onClearClicked()
            vm.onClearConfirmed()
            settle()
            assertTrue(vm.uiState.value.busy)
            assertEquals(1, store.saveCalls.size)
            assertEquals(0, store.clearCalls)
            gate.complete(Unit)
            settle()
            assertEquals(1, store.saveCalls.size)
            assertEquals(0, store.clearCalls)
            assertFalse(vm.uiState.value.busy)
        }
}
