package pl.twojanazwa.aapsremote.ui.history

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class HistoryViewModelDetailsTest {
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

    @Test
    fun `HISTUI-07 klikniecie otwiera szczegoly a zamkniecie je chowa`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(2, 1))
        val vm = HistoryViewModel(history)
        idle()
        vm.onEntryClicked(1)
        assertEquals(1L, vm.uiState.value.details?.id)
        vm.onDetailsClosed()
        assertNull(vm.uiState.value.details)
    }

    @Test
    fun `HISTUI-07 wpis znika ze strumienia i szczegoly sie zamykaja a przy obecnym zostaja`() =
        runTest(main.dispatcher) {
            val stream = history.enqueue(snapshot(3, 2, 1))
            val vm = HistoryViewModel(history)
            idle()
            vm.onEntryClicked(1)
            stream.emit(snapshot(4, 3, 2, 1))
            idle()
            assertEquals(1L, vm.uiState.value.details?.id)
            stream.emit(snapshot(3, 2))
            idle()
            assertNull(vm.uiState.value.details)
            stream.emit(snapshot(3, 2, 1))
            idle()
            assertNull(vm.uiState.value.details)
        }

    @Test
    fun `HISTUI-07 nieznany identyfikator nie otwiera szczegolow`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(2, 1))
        val vm = HistoryViewModel(history)
        idle()
        vm.onEntryClicked(7)
        assertNull(vm.uiState.value.details)
    }

    @Test
    fun `HISTUI-12 wstecz zamyka szczegoly a na liscie nie przechwytuje`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(1))
        val vm = HistoryViewModel(history)
        idle()
        assertFalse(vm.uiState.value.interceptsBack)
        vm.onEntryClicked(1)
        assertTrue(vm.uiState.value.interceptsBack)
        vm.onBack()
        assertNull(vm.uiState.value.details)
        assertFalse(vm.uiState.value.interceptsBack)
    }
}
