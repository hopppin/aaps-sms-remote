package pl.twojanazwa.aapsremote.ui.history

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.ClearHistoryResult
import pl.twojanazwa.aapsremote.data.CommandHistory
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class HistoryViewModelClearTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private val history = ControllableCommandHistory()

    private fun snapshot(vararg ids: Long) = HistorySnapshot.Entries(
        ids.map { historyEntry(HistoryOutcome.Cancelled, HistoryCommand.BG, id = it) },
    )

    private fun idle() = main.dispatcher.scheduler.advanceUntilIdle()

    private fun loaded(): HistoryViewModel {
        val vm = HistoryViewModel(history)
        idle()
        return vm
    }

    @Test
    fun `HISTUI-10 okno z liczba wpisow i zamkniecie bez czyszczenia`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(3, 2, 1))
        val vm = loaded()
        vm.onClearClicked()
        assertEquals(3, vm.uiState.value.confirmingClearCount)
        assertEquals(0, history.clearCount)
        vm.onClearDismissed()
        assertNull(vm.uiState.value.confirmingClearCount)
        idle()
        assertEquals(0, history.clearCount)
    }

    @Test
    fun `HISTUI-10 wielokrotne potwierdzenie daje jedno clearAll`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(3, 2, 1))
        val gate = CompletableDeferred<Unit>()
        history.clearGate = gate
        val vm = loaded()
        vm.onClearClicked()
        vm.onClearConfirmed()
        vm.onClearConfirmed()
        vm.onClearClicked()
        idle()
        assertEquals(1, history.clearCount)
        val state = vm.uiState.value
        assertNull(state.confirmingClearCount)
        assertTrue(state.clearing)
        assertFalse(state.canClear)
        gate.complete(Unit)
        idle()
        assertFalse(vm.uiState.value.clearing)
    }

    @Test
    fun `HISTUI-10 konstruktor ViewModelu nie przyjmuje SessionCoordinator`() {
        val params = HistoryViewModel::class.java.constructors.flatMap { it.parameterTypes.toList() }
        assertEquals(listOf<Class<*>>(CommandHistory::class.java), params)
    }

    @Test
    fun `HISTUI-11 udane czyszczenie daje komunikat i pusty stan`() = runTest(main.dispatcher) {
        val stream = history.enqueue(snapshot(3, 2, 1))
        history.enqueueClearResult(ClearHistoryResult.Cleared)
        val vm = loaded()
        vm.onClearClicked()
        vm.onClearConfirmed()
        stream.emit(snapshot())
        idle()
        val state = vm.uiState.value
        assertEquals(HistoryMessage.CLEARED, state.message)
        assertEquals(HistoryContent.Empty, state.content)
        assertFalse(state.clearing)
    }

    @Test
    fun `HISTUI-11 nieudane czyszczenie zachowuje liste i nie ponawia`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(3, 2, 1))
        history.enqueueClearResult(ClearHistoryResult.Failed)
        val vm = loaded()
        vm.onClearClicked()
        vm.onClearConfirmed()
        idle()
        val state = vm.uiState.value
        assertEquals(HistoryMessage.CLEAR_FAILED, state.message)
        assertEquals(3, (state.content as HistoryContent.Entries).items.size)
        assertTrue(state.canClear)
        assertEquals(1, history.clearCount)
    }

    @Test
    fun `HISTUI-11 pokazany komunikat znika po onMessageShown`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(1))
        history.enqueueClearResult(ClearHistoryResult.Failed)
        val vm = loaded()
        vm.onClearClicked()
        vm.onClearConfirmed()
        idle()
        assertEquals(HistoryMessage.CLEAR_FAILED, vm.uiState.value.message)
        vm.onMessageShown()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `HISTUI-12 wstecz przy oknie czyszczenia zamyka okno bez czyszczenia`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(1))
        val vm = loaded()
        vm.onClearClicked()
        assertTrue(vm.uiState.value.interceptsBack)
        vm.onBack()
        assertNull(vm.uiState.value.confirmingClearCount)
        idle()
        assertEquals(0, history.clearCount)
    }

    @Test
    fun `HISTUI-09 klikniecie czyszczenia w bledzie odczytu nic nie zmienia`() = runTest(main.dispatcher) {
        history.enqueue(HistorySnapshot.Unavailable)
        val vm = loaded()
        val before = vm.uiState.value
        vm.onClearClicked()
        assertEquals(before, vm.uiState.value)
    }
}
